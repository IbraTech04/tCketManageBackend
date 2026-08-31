package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An operator-supplied provider-side payment reference - for Interac, the reference number printed on
 * the e-Transfer notification.
 *
 * <p>The constraints here bind only where the request is validated. The payment-reference endpoint
 * validates, because replacing a reference with nothing is a mistake worth a 400. The manual-confirm
 * endpoint does not, because confirming without a reference is legitimate and callers say so by
 * sending an empty body - see {@code OrderController#confirmManualPayment}, which normalizes blank
 * to none itself.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentReferenceRequest {

    /** Capped at the {@code orders.provider_ref} column width so an overlong value fails as a 400. */
    @NotBlank
    @Size(max = 255)
    private String providerRef;
}
