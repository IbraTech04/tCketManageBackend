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
 * Fully-working in-process provider used for dev and tests. Depending on {@code payments.mock.auto-confirm}
 * it either settles immediately (returns {@link PaymentInitiation.Completed}) or returns manual
 * {@link PaymentInitiation.Instructions} to exercise the operator-confirmation path. Enabled by default.
 */
@Service
@ConditionalOnProperty(prefix = "payments.mock", name = "enabled", havingValue = "true", matchIfMissing = true)
@AllArgsConstructor
public class MockPaymentProvider implements PaymentProvider {

    private final PaymentProperties properties;

    @Override
    public String id() {
        return "mock";
    }

    @Override
    public PaymentInitiation initiate(PaymentContext context) {
        String ref = "mock-" + context.referenceCode();
        if (properties.getMock().isAutoConfirm()) {
            return new PaymentInitiation.Completed(ref);
        }
        return new PaymentInitiation.Instructions(ref,
                "Mock manual payment. Confirm via POST /api/v1/payments/mock/{orderId}/complete.",
                Map.of("referenceCode", context.referenceCode(),
                       "amount", context.amount().toPlainString(),
                       "currency", context.currency()));
    }

    @Override
    public Duration holdDuration() {
        return Duration.ofMinutes(properties.getMock().getHoldMinutes());
    }

    @Override
    public boolean isAutomatic() {
        return properties.getMock().isAutoConfirm();
    }

    @Override
    public RefundOutcome refund(Order order) {
        return RefundOutcome.completed();
    }
}
