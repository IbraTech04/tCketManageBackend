package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A scan originating from a scanned QR code: the base64-encoded signed {@code TicketQRData} payload
 * and the zone being entered.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QrScanRequest {

    @NotBlank
    private String qrPayload;

    @NotNull
    private UUID zoneId;
}
