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

/**
 * Stub for Stripe Checkout. Activated only when {@code tcketmanage.payments.stripe.enabled=true}. The real
 * implementation will create a Checkout Session (returning a {@link PaymentInitiation.Redirect}) and
 * confirm via a signature-verified, idempotent webhook routed through
 * {@link com.ibrasoft.tcketmanagebackend.payment.PaymentConfirmationService}. Not yet wired.
 */
@Service
@ConditionalOnProperty(prefix = "tcketmanage.payments.stripe", name = "enabled", havingValue = "true")
@AllArgsConstructor
public class StripePaymentProvider implements PaymentProvider {

    private final PaymentProperties properties;

    @Override
    public String id() {
        return "stripe";
    }

    @Override
    public PaymentInitiation initiate(PaymentContext context) {
        throw new UnsupportedOperationException(
            "Stripe provider is enabled but not yet implemented. Wire the Stripe SDK or disable payments.stripe.");
    }

    @Override
    public Duration holdDuration() {
        return properties.getStripe().getHold();
    }

    @Override
    public boolean isAutomatic() {
        return true;
    }

    @Override
    public RefundOutcome refund(Order order) {
        throw new UnsupportedOperationException("Stripe refunds not yet implemented.");
    }
}
