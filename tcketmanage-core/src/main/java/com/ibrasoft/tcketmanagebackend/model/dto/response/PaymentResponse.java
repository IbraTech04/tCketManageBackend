package com.ibrasoft.tcketmanagebackend.model.dto.response;

import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Flattened, JSON-friendly view of a {@link PaymentInitiation} for the API response. {@code type}
 * tells the client how to proceed: {@code redirect}, {@code instructions}, or {@code completed}.
 */
@Data
@Builder
@AllArgsConstructor
public class PaymentResponse {
    private String type;
    private String providerRef;
    private String redirectUrl;
    private String instructions;
    private Map<String, String> details;

    public static PaymentResponse from(PaymentInitiation initiation) {
        PaymentResponseBuilder b = PaymentResponse.builder().providerRef(initiation.providerRef());
        return switch (initiation) {
            case PaymentInitiation.Redirect r -> b.type("redirect").redirectUrl(r.redirectUrl()).build();
            case PaymentInitiation.Instructions i ->
                    b.type("instructions").instructions(i.instructions()).details(i.details()).build();
            case PaymentInitiation.Completed c -> b.type("completed").build();
        };
    }
}
