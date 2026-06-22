package com.ibrasoft.tcketmanagebackend.model.dto.request;

import lombok.Data;

/**
 * Body for the operator "deny quarantine" action. {@code fundsReceived} captures whether the
 * mismatched payment actually arrived: when true the order is queued for an out-of-band refund
 * ({@code REFUND_PENDING}); when false it is simply cancelled and its seats released.
 */
@Data
public class DenyQuarantineRequest {

    private boolean fundsReceived;

    public DenyQuarantineRequest() {}

    public DenyQuarantineRequest(boolean fundsReceived) {
        this.fundsReceived = fundsReceived;
    }

    public void setFundsReceived(boolean fundsReceived) {
        this.fundsReceived = fundsReceived;
    }
}
