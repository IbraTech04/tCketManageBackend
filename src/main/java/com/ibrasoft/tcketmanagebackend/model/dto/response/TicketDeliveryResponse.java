package com.ibrasoft.tcketmanagebackend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Outcome of a bulk ticket-delivery operation ("resend all" / "send missing"): how many tickets
 * were considered and how many emails actually went out versus failed.
 */
@Data
@Builder
@AllArgsConstructor
public class TicketDeliveryResponse {

    /** Number of tickets the operation attempted to send. */
    private int total;

    /** Number that were sent successfully (and had {@code lastTicketSent} stamped). */
    private int sent;

    /** Number whose delivery failed (left unstamped so they can be retried). */
    private int failed;
}
