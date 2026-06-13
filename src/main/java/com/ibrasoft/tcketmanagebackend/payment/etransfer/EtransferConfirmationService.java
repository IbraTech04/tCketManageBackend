package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Turns a received Interac e-Transfer email into a confirmed order. This is the policy layer between
 * the raw IMAP listener and the provider-agnostic {@link PaymentConfirmationService}: it decides
 * whether an email is trustworthy and unambiguously tied to an awaiting order, and only then settles
 * it. Anything that fails a check is reported as {@link EtransferOutcome.Status#QUARANTINED} so the
 * listener can set the message aside for an operator. We never auto-confirm on a partial match.
 *
 * <p>Confirmation funnels through {@link PaymentConfirmationService#confirmPayment}, so it inherits
 * that method's row-locked idempotency: a redelivered email resolves to a no-op rather than
 * double-fulfilling.
 */
@Service
@AllArgsConstructor
public class EtransferConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(EtransferConfirmationService.class);

    private final InteracEmailParser parser;
    private final OrderRepository orderRepository;
    private final PaymentConfirmationService paymentConfirmationService;
    private final PaymentProperties paymentProperties;

    /**
     * Validates and (if everything checks out) confirms the order described by an email.
     *
     * @param fromAddress the bare {@code From} address of the message (no display name)
     * @param html        the email's HTML body
     * @return the outcome; the caller quarantines the message when {@link EtransferOutcome#isQuarantined()}
     */
    public EtransferOutcome process(String fromAddress, String html) {
        PaymentProperties.Imap config = paymentProperties.getInterac().getImap();

        // 1. Trust the sender. The From header is spoofable, but matching the configured Interac
        // address is the standard guard here; stronger SPF/DKIM enforcement is out of scope.
        if (!isTrustedSender(fromAddress, config)) {
            return quarantine("untrusted sender: " + fromAddress);
        }

        // 2. Parse. A missing amount means this isn't a recognizable notification.
        ParsedEtransfer parsed;
        try {
            parsed = parser.parse(html);
        } catch (EtransferParseException e) {
            return quarantine("unparseable email: " + e.getMessage());
        }

        // 3. The memo must contain one of our reference codes.
        if (parsed.referenceCode() == null) {
            return quarantine("no reference code in memo: \"" + parsed.message() + "\"");
        }

        // 4. The code must map to a known order.
        Optional<Order> match = orderRepository.findByReferenceCode(parsed.referenceCode());
        if (match.isEmpty()) {
            return quarantine("no order for reference code " + parsed.referenceCode());
        }
        Order order = match.get();

        // 5. Amount (and currency) must match exactly, when required. Defence-in-depth on top of the
        // unguessable code: a transfer for the wrong amount never silently fulfills an order.
        if (config.isRequireExactAmount() && !amountMatches(parsed, order)) {
            return quarantine(String.format(
                    "amount mismatch for order %s (code %s): received %s %s, expected %s %s",
                    order.getId(), parsed.referenceCode(),
                    parsed.amount().toPlainString(), parsed.currency(),
                    order.getAmountTotal().toPlainString(), order.getCurrency()));
        }

        // 6. Settle through the single idempotent confirmation seam. Interac's own reference number
        // becomes the provider ref (audit trail). A redelivered email is a harmless no-op here.
        try {
            paymentConfirmationService.confirmPayment(order.getId(), parsed.interacReferenceNumber());
        } catch (RuntimeException e) {
            // e.g. the order is in a state that can't transition to PAID: surface for an operator.
            return quarantine("confirmation failed for order " + order.getId() + ": " + e.getMessage());
        }

        log.info("e-Transfer confirmed order {} (code {}, {} {}, interac ref {})",
                order.getId(), parsed.referenceCode(), parsed.amount().toPlainString(),
                parsed.currency(), parsed.interacReferenceNumber());
        return EtransferOutcome.confirmed("order " + order.getId() + " confirmed via e-Transfer");
    }

    private boolean isTrustedSender(String fromAddress, PaymentProperties.Imap config) {
        if (fromAddress == null) {
            return false;
        }
        return config.getExpectedSenders().stream()
                .anyMatch(expected -> expected.equalsIgnoreCase(fromAddress.trim()));
    }

    private boolean amountMatches(ParsedEtransfer parsed, Order order) {
        BigDecimal received = parsed.amount();
        return received.compareTo(order.getAmountTotal()) == 0
                && parsed.currency().equalsIgnoreCase(order.getCurrency());
    }

    private EtransferOutcome quarantine(String reason) {
        log.warn("Quarantining e-Transfer email for review: {}", reason);
        return EtransferOutcome.quarantined(reason);
    }
}
