package com.ibrasoft.tcketmanagebackend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScanResult {
    private ScanOutcome outcome;
    private String message;
    private ScanEventResponse scanEvent;

    public boolean isSuccess() {
        return outcome == ScanOutcome.SUCCESS;
    }
}
