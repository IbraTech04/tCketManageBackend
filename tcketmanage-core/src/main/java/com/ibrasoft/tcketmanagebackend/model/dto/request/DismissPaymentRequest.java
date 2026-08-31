package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Why an operator is writing a payment off. Optional, but worth capturing: "sent to us by mistake,
 * refunded by hand" is the difference between a resolved queue and a mystery six months later.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DismissPaymentRequest {

    @Size(max = 500)
    private String note;
}
