package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtransferConfirmationServiceTest {

    private static final String TRUSTED = "notify@payments.interac.ca";
    private static final String HTML = "<ignored, parser is mocked>";
    /** An aligned dmarc=pass added by our own server; used by the DMARC tests. */
    private static final String AUTHSERV_ID = "mail.lensbridge.tech";
    private static final String[] DMARC_PASS = {
            AUTHSERV_ID + "; spf=pass smtp.mailfrom=interac.ca; "
                    + "dkim=pass header.d=interac.ca; dmarc=pass header.from=payments.interac.ca"};

    @Mock
    private InteracEmailParser parser;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    private EtransferConfirmationService service;

    @BeforeEach
    void setUp() {
        // Real properties POJO: defaults trust the Interac sender, require an exact amount, DMARC off.
        service = serviceWith(new PaymentProperties());
    }

    /** Builds a service over the given properties; uses the real (pure) Authentication-Results parser. */
    private EtransferConfirmationService serviceWith(PaymentProperties props) {
        return new EtransferConfirmationService(
                parser, new AuthenticationResultsParser(), orderRepository, paymentConfirmationService, props);
    }

    /** Properties with DMARC enforcement enabled for the given authserv-id (aligned domain auto-derived). */
    private static PaymentProperties dmarcEnabled(String authservId) {
        PaymentProperties props = new PaymentProperties();
        PaymentProperties.Dmarc dmarc = props.getInterac().getImap().getDmarc();
        dmarc.setEnabled(true);
        dmarc.setAuthservId(authservId);
        return props;
    }

    private static ParsedEtransfer parsed(String code, String amount) {
        return new ParsedEtransfer("memo " + code, code, new BigDecimal(amount), "CAD", "INTREF1");
    }

    private static Order order(String amount) {
        return Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.AWAITING_PAYMENT)
                .amountTotal(new BigDecimal(amount))
                .currency("CAD")
                .build();
    }

    @Test
    void happyPath_confirmsOrderWithInteracRef() {
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        EtransferOutcome outcome = service.process(TRUSTED, null, HTML);

        assertEquals(EtransferOutcome.Status.CONFIRMED, outcome.status());
        verify(paymentConfirmationService).confirmPayment(o.getId(), "INTREF1");
    }

    @Test
    void untrustedSender_quarantinesWithoutParsing() {
        EtransferOutcome outcome = service.process("scammer@example.com", null, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void parseFailure_quarantines() {
        when(parser.parse(HTML)).thenThrow(new EtransferParseException("no amount"));

        EtransferOutcome outcome = service.process(TRUSTED, null, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(orderRepository, paymentConfirmationService);
    }

    @Test
    void noReferenceCodeInMemo_quarantines() {
        when(parser.parse(HTML)).thenReturn(
                new ParsedEtransfer("thanks!", null, new BigDecimal("35.00"), "CAD", "INTREF1"));

        EtransferOutcome outcome = service.process(TRUSTED, null, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(orderRepository, paymentConfirmationService);
    }

    @Test
    void unknownReferenceCode_quarantines() {
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.empty());

        EtransferOutcome outcome = service.process(TRUSTED, null, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(paymentConfirmationService);
    }

    @Test
    void amountMismatch_quarantinesOrderWithoutConfirming() {
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "5.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        EtransferOutcome outcome = service.process(TRUSTED, null, HTML);

        assertTrue(outcome.isQuarantined());
        verify(paymentConfirmationService).quarantineOrder(o.getId());
        verify(paymentConfirmationService, never()).confirmPayment(any(), any());
    }

    @Test
    void confirmationThrows_quarantines() {
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));
        doThrow(new ConflictException("bad state"))
                .when(paymentConfirmationService).confirmPayment(any(), any());

        EtransferOutcome outcome = service.process(TRUSTED, null, HTML);

        assertTrue(outcome.isQuarantined());
    }

    // --- DMARC enforcement (opt-in) ---

    @Test
    void dmarcEnabled_alignedPass_confirms() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        EtransferOutcome outcome = service.process(TRUSTED, DMARC_PASS, HTML);

        assertEquals(EtransferOutcome.Status.CONFIRMED, outcome.status());
        verify(paymentConfirmationService).confirmPayment(o.getId(), "INTREF1");
    }

    @Test
    void dmarcEnabled_missingHeader_quarantinesBeforeParsing() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));

        EtransferOutcome outcome = service.process(TRUSTED, null, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_fail_quarantines() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        String[] fail = {AUTHSERV_ID + "; dmarc=fail header.from=payments.interac.ca"};

        EtransferOutcome outcome = service.process(TRUSTED, fail, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_forgedHeaderFromOtherAuthservId_isIgnored() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        // A pass stamped by some other server (e.g. an attacker's) must not be trusted.
        String[] forged = {"evil.example.com; dmarc=pass header.from=payments.interac.ca"};

        EtransferOutcome outcome = service.process(TRUSTED, forged, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_passButMisalignedDomain_quarantines() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        // dmarc=pass, but authenticated for a different domain than the trusted From's.
        String[] misaligned = {AUTHSERV_ID + "; dmarc=pass header.from=evil.example.com"};

        EtransferOutcome outcome = service.process(TRUSTED, misaligned, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_withoutAuthservIdConfigured_quarantines() {
        service = serviceWith(dmarcEnabled(null));

        EtransferOutcome outcome = service.process(TRUSTED, DMARC_PASS, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }
}
