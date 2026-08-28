package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request to purchase one or more seats for an event. {@code providerId} is optional; the configured
 * default provider is used when omitted.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderRequest {
    public static final int MAX_ITEMS = 100;

    @Email
    @NotBlank
    private String buyerEmail;

    @NotNull
    private UUID eventId;

    private String providerId;

    @Valid
    @NotEmpty
    @Size(max = MAX_ITEMS, message = "An order may contain at most " + MAX_ITEMS + " seats")
    private List<OrderItemRequest> items = new ArrayList<>();
}
