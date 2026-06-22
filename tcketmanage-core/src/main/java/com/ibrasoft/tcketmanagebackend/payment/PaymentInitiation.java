package com.ibrasoft.tcketmanagebackend.payment;

import java.util.Map;

/**
 * The result of {@link PaymentProvider#initiate}, describing how the buyer should proceed. Sealed so
 * the order/controller layer can exhaustively handle each shape without knowing the concrete provider:
 * <ul>
 *   <li>{@link Redirect} — send the buyer to a hosted payment page (e.g. Stripe Checkout).</li>
 *   <li>{@link Instructions} — show manual payment instructions (e.g. Interac e-Transfer details).</li>
 *   <li>{@link Completed} — payment already settled (e.g. Mock auto-pay or a zero-cost order).</li>
 * </ul>
 */
public sealed interface PaymentInitiation
        permits PaymentInitiation.Redirect, PaymentInitiation.Instructions, PaymentInitiation.Completed {

    /** Provider-side reference for this payment attempt (session id, transfer ref, …). */
    String providerRef();

    record Redirect(String providerRef, String redirectUrl) implements PaymentInitiation {}

    record Instructions(String providerRef, String instructions, Map<String, String> details)
            implements PaymentInitiation {}

    record Completed(String providerRef) implements PaymentInitiation {}
}
