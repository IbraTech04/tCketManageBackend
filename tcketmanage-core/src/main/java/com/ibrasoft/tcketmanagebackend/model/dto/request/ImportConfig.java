package com.ibrasoft.tcketmanagebackend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Tells the importer which CSV column holds which attendee field. Ticket type is resolved per row
 * from {@code ticketTypeColumn} (the cell value is a ticket-type name within the event); when that
 * column is absent or a cell is blank, {@code defaultTicketTypeId} is used. At least one of the two
 * must be supplied.
 */
@Data
public class ImportConfig {

    @NotNull
    private Integer firstNameColumn;

    @NotNull
    private Integer lastNameColumn;

    @NotNull
    private Integer emailColumn;

    /** Optional column whose value is a ticket-type name to resolve within the event. */
    private Integer ticketTypeColumn;

    /** Optional fallback ticket type when {@code ticketTypeColumn} is unset or a cell is blank. */
    private UUID defaultTicketTypeId;

    /** Whether the first CSV row is a header and should be skipped. */
    private boolean hasHeaderRow = true;
}
