package com.ibrasoft.tcketmanagebackend.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Everything a {@link PaymentProvider} needs to initiate payment for an order. Provider-neutral.
 */
public record PaymentContext(
        UUID orderId,
        String referenceCode,
        BigDecimal amount,
        String currency,
        String buyerEmail,
        String description,
        String returnUrl,
        String cancelUrl
) {}
