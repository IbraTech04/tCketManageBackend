package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.exception.ConflictException;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.model.order.OrderStatus;
import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import com.ibrasoft.tcketmanagebackend.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtransferConfirmationServiceTest {

    private static final String TRUSTED = "notify@payments.interac.ca";
    private static final String HTML = "<ignored, parser is mocked>";
    /** When the mail server took delivery; what a receipt's emailReceivedAt must carry. */
    private static final Instant RECEIVED_AT = Instant.parse("2026-06-06T02:35:00Z");
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
    @Mock
    private EtransferReceiptRecorder receiptRecorder;

    private EtransferConfirmationService service;

    @BeforeEach
    void setUp() {
        // Real properties POJO: defaults trust the Interac sender, require an exact amount, DMARC off.
        service = serviceWith(new PaymentProperties());
    }

    /** Builds a service over the given properties; uses the real (pure) Authentication-Results parser. */
    private EtransferConfirmationService serviceWith(PaymentProperties props) {
        return new EtransferConfirmationService(
                parser, new AuthenticationResultsParser(), orderRepository, paymentConfirmationService, props,
                receiptRecorder);
    }

    /** The received notification, with the body irrelevant because the parser is mocked. */
    private static ReceivedEmail email(String from, String[] authResults) {
        return new ReceivedEmail(from, "SAMMY NIMOUR", RECEIVED_AT, authResults, HTML);
    }

    /** The receipt the service handed the recorder. Fails the test if it never recorded one. */
    private EtransferReceipt recordedReceipt() {
        ArgumentCaptor<EtransferReceipt> captor = ArgumentCaptor.forClass(EtransferReceipt.class);
        verify(receiptRecorder).record(captor.capture());
        return captor.getValue();
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
        return new ParsedEtransfer("memo " + code, code, new BigDecimal(amount), "CAD", "INTREF1",
                "SAMMY NIMOUR", "June 6, 2026");
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

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

        assertEquals(EtransferOutcome.Status.CONFIRMED, outcome.status());
        verify(paymentConfirmationService).confirmPayment(o.getId(), "INTREF1");
    }

    @Test
    void untrustedSender_quarantinesWithoutParsing() {
        EtransferOutcome outcome = service.process(email("scammer@example.com", null));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void parseFailure_quarantines() {
        when(parser.parse(HTML)).thenThrow(new EtransferParseException("no amount"));

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(orderRepository, paymentConfirmationService);
    }

    @Test
    void noReferenceCodeInMemo_quarantines() {
        when(parser.parse(HTML)).thenReturn(new ParsedEtransfer(
                "thanks!", null, new BigDecimal("35.00"), "CAD", "INTREF1", "SAMMY NIMOUR", "June 6, 2026"));

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(orderRepository, paymentConfirmationService);
    }

    @Test
    void unknownReferenceCode_quarantines() {
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.empty());

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(paymentConfirmationService);
    }

    @Test
    void amountMismatch_quarantinesOrderWithoutConfirming() {
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "5.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

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

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

        assertTrue(outcome.isQuarantined());
    }

    // --- receipts: every email leaves a record, whatever the verdict ---

    @Test
    void confirmed_recordsReceiptWithTheExtractedDetails() {
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        service.process(email(TRUSTED, null));

        EtransferReceipt receipt = recordedReceipt();
        assertEquals(EtransferReceipt.Outcome.CONFIRMED, receipt.getOutcome());
        assertEquals("INTREF1", receipt.getInteracReference());
        assertEquals("SAMMY NIMOUR", receipt.getSenderName());
        assertEquals(RECEIVED_AT, receipt.getEmailReceivedAt());
        assertEquals("June 6, 2026", receipt.getBodyDateText());
        assertEquals(TRUSTED, receipt.getSenderEmail());
        assertEquals(o, receipt.getOrder());
        assertEquals(0, new BigDecimal("35.00").compareTo(receipt.getAmount()));
    }

    @Test
    void untrustedSender_stillRecordsReceiptFromTheEnvelopeAlone() {
        // Rejected before parsing, so the body fields are unknown - but the email must not vanish.
        service.process(email("scammer@example.com", null));

        EtransferReceipt receipt = recordedReceipt();
        assertEquals(EtransferReceipt.Outcome.QUARANTINED, receipt.getOutcome());
        assertEquals("scammer@example.com", receipt.getSenderEmail());
        assertEquals(RECEIVED_AT, receipt.getEmailReceivedAt());
        assertNull(receipt.getOrder());
        assertNull(receipt.getInteracReference());
        assertTrue(receipt.getDetail().contains("untrusted sender"));
    }

    @Test
    void unknownReferenceCode_recordsReceiptWithNoOrderAttached() {
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.empty());

        service.process(email(TRUSTED, null));

        EtransferReceipt receipt = recordedReceipt();
        assertEquals(EtransferReceipt.Outcome.QUARANTINED, receipt.getOutcome());
        assertNull(receipt.getOrder());
        // The facts an operator needs to chase an unmatched payment are all still on the row.
        assertEquals("ABCD-EFGH", receipt.getReferenceCode());
        assertEquals("INTREF1", receipt.getInteracReference());
        assertEquals("SAMMY NIMOUR", receipt.getSenderName());
    }

    @Test
    void amountMismatch_recordsWhatWasActuallyReceived() {
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "5.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        service.process(email(TRUSTED, null));

        EtransferReceipt receipt = recordedReceipt();
        assertEquals(EtransferReceipt.Outcome.QUARANTINED, receipt.getOutcome());
        assertEquals(o, receipt.getOrder());
        assertEquals(0, new BigDecimal("5.00").compareTo(receipt.getAmount()));
    }

    @Test
    void receiptWriteFailure_doesNotUndoAConfirmedPayment() {
        // The audit write is best-effort: losing it must never turn a settled payment into a
        // quarantine, which is what an exception escaping here would cause in EtransferMailHandler.
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));
        doThrow(new RuntimeException("db down")).when(receiptRecorder).record(any());

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

        assertEquals(EtransferOutcome.Status.CONFIRMED, outcome.status());
        verify(paymentConfirmationService).confirmPayment(o.getId(), "INTREF1");
    }

    @Test
    void overlongMemo_isTruncatedRatherThanFailingTheWrite() {
        when(parser.parse(HTML)).thenReturn(new ParsedEtransfer(
                "x".repeat(900), null, new BigDecimal("35.00"), "CAD", "INTREF1", "SAMMY NIMOUR", null));

        service.process(email(TRUSTED, null));

        assertEquals(500, recordedReceipt().getMemo().length());
    }

    // --- DMARC enforcement (opt-in) ---

    @Test
    void dmarcEnabled_alignedPass_confirms() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));

        EtransferOutcome outcome = service.process(email(TRUSTED, DMARC_PASS));

        assertEquals(EtransferOutcome.Status.CONFIRMED, outcome.status());
        verify(paymentConfirmationService).confirmPayment(o.getId(), "INTREF1");
    }

    @Test
    void dmarcEnabled_missingHeader_quarantinesBeforeParsing() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));

        EtransferOutcome outcome = service.process(email(TRUSTED, null));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_fail_quarantines() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        String[] fail = {AUTHSERV_ID + "; dmarc=fail header.from=payments.interac.ca"};

        EtransferOutcome outcome = service.process(email(TRUSTED, fail));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_forgedHeaderFromOtherAuthservId_isIgnored() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        // A pass stamped by some other server (e.g. an attacker's) must not be trusted.
        String[] forged = {"evil.example.com; dmarc=pass header.from=payments.interac.ca"};

        EtransferOutcome outcome = service.process(email(TRUSTED, forged));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_passButMisalignedDomain_quarantines() {
        service = serviceWith(dmarcEnabled(AUTHSERV_ID));
        // dmarc=pass, but authenticated for a different domain than the trusted From's.
        String[] misaligned = {AUTHSERV_ID + "; dmarc=pass header.from=evil.example.com"};

        EtransferOutcome outcome = service.process(email(TRUSTED, misaligned));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void dmarcEnabled_withoutAuthservIdConfigured_quarantines() {
        service = serviceWith(dmarcEnabled(null));

        EtransferOutcome outcome = service.process(email(TRUSTED, DMARC_PASS));

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }
}
