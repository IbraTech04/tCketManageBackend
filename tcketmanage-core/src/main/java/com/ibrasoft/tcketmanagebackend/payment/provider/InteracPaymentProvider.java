package com.ibrasoft.tcketmanagebackend.payment.provider;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.payment.PaymentContext;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProvider;
import com.ibrasoft.tcketmanagebackend.payment.RefundOutcome;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * Interac e-Transfer via the manual reference-code flow. There is no universal API to receive
 * e-Transfers, so {@code initiate} returns {@link PaymentInitiation.Instructions} (payee email +
 * memo = order reference code + amount); an operator confirms receipt via the manual-confirm
 * endpoint, which routes through
 * {@link com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService}. Activated only when
 * {@code payments.interac.enabled=true}.
 */
@Service
@ConditionalOnProperty(prefix = "payments.interac", name = "enabled", havingValue = "true")
@AllArgsConstructor
public class InteracPaymentProvider implements PaymentProvider {

    private final PaymentProperties properties;

    @Override
    public String id() {
        return "interac";
    }

    @Override
    public PaymentInitiation initiate(PaymentContext context) {
        String payeeEmail = properties.getInterac().getPayeeEmail();
        String instructions = String.format(
                "Send an Interac e-Transfer of %s %s to %s and include the memo code %s.",
                context.amount().toPlainString(), context.currency(), payeeEmail, context.referenceCode());
        return new PaymentInitiation.Instructions(
                "interac-" + context.referenceCode(),
                instructions,
                Map.of("payeeEmail", payeeEmail == null ? "" : payeeEmail,
                       "memo", context.referenceCode(),
                       "amount", context.amount().toPlainString(),
                       "currency", context.currency()));
    }

    @Override
    public Duration holdDuration() {
        return Duration.ofHours(properties.getInterac().getHoldHours());
    }

    @Override
    public boolean isAutomatic() {
        return false;
    }

    @Override
    public RefundOutcome refund(Order order) {
        return RefundOutcome.manualActionRequired(
                "Send an Interac e-Transfer refund of " + order.getAmountTotal() + " "
                        + order.getCurrency() + " to " + order.getBuyerEmail());
    }
}
