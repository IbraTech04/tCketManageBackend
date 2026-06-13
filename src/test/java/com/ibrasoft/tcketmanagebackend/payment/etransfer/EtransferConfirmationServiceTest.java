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

    @Mock
    private InteracEmailParser parser;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    private EtransferConfirmationService service;

    @BeforeEach
    void setUp() {
        // Real properties POJO: defaults trust the Interac sender and require an exact amount.
        service = new EtransferConfirmationService(
                parser, orderRepository, paymentConfirmationService, new PaymentProperties());
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

        EtransferOutcome outcome = service.process(TRUSTED, HTML);

        assertEquals(EtransferOutcome.Status.CONFIRMED, outcome.status());
        verify(paymentConfirmationService).confirmPayment(o.getId(), "INTREF1");
    }

    @Test
    void untrustedSender_quarantinesWithoutParsing() {
        EtransferOutcome outcome = service.process("scammer@example.com", HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(parser, orderRepository, paymentConfirmationService);
    }

    @Test
    void parseFailure_quarantines() {
        when(parser.parse(HTML)).thenThrow(new EtransferParseException("no amount"));

        EtransferOutcome outcome = service.process(TRUSTED, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(orderRepository, paymentConfirmationService);
    }

    @Test
    void noReferenceCodeInMemo_quarantines() {
        when(parser.parse(HTML)).thenReturn(
                new ParsedEtransfer("thanks!", null, new BigDecimal("35.00"), "CAD", "INTREF1"));

        EtransferOutcome outcome = service.process(TRUSTED, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(orderRepository, paymentConfirmationService);
    }

    @Test
    void unknownReferenceCode_quarantines() {
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.empty());

        EtransferOutcome outcome = service.process(TRUSTED, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(paymentConfirmationService);
    }

    @Test
    void amountMismatch_quarantinesWithoutConfirming() {
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "5.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(order("35.00")));

        EtransferOutcome outcome = service.process(TRUSTED, HTML);

        assertTrue(outcome.isQuarantined());
        verifyNoInteractions(paymentConfirmationService);
    }

    @Test
    void confirmationThrows_quarantines() {
        Order o = order("35.00");
        when(parser.parse(HTML)).thenReturn(parsed("ABCD-EFGH", "35.00"));
        when(orderRepository.findByReferenceCode("ABCD-EFGH")).thenReturn(Optional.of(o));
        doThrow(new ConflictException("bad state"))
                .when(paymentConfirmationService).confirmPayment(any(), any());

        EtransferOutcome outcome = service.process(TRUSTED, HTML);

        assertTrue(outcome.isQuarantined());
    }
}
