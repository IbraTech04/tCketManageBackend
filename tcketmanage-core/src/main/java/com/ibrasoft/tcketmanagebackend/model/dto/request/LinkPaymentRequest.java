package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** The order an operator has decided an unmatched payment belongs to. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkPaymentRequest {

    @NotNull
    private UUID orderId;
}
