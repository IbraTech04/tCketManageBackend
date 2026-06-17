package com.ibrasoft.tcketmanagebackend.payment.provider;

import com.ibrasoft.tcketmanagebackend.payment.PaymentContext;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MockPaymentProviderTest {

    private PaymentContext ctx() {
        return new PaymentContext(UUID.randomUUID(), "ORD-ABC123", new BigDecimal("25.00"), "CAD",
                "buyer@example.com", "Tickets", null, null);
    }

    private MockPaymentProvider provider(boolean autoConfirm) {
        PaymentProperties props = new PaymentProperties();
        props.getMock().setAutoConfirm(autoConfirm);
        return new MockPaymentProvider(props);
    }

    @Test
    void autoConfirm_returnsCompleted() {
        PaymentInitiation initiation = provider(true).initiate(ctx());
        assertInstanceOf(PaymentInitiation.Completed.class, initiation);
        assertTrue(provider(true).isAutomatic());
    }

    @Test
    void manualMode_returnsInstructions() {
        PaymentInitiation initiation = provider(false).initiate(ctx());
        assertInstanceOf(PaymentInitiation.Instructions.class, initiation);
        assertFalse(provider(false).isAutomatic());
        PaymentInitiation.Instructions instr = (PaymentInitiation.Instructions) initiation;
        assertEquals("ORD-ABC123", instr.details().get("referenceCode"));
    }
}
