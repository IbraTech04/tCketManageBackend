package com.ibrasoft.tcketmanagebackend.service;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.model.dto.request.ImportConfig;
import com.ibrasoft.tcketmanagebackend.model.dto.response.ImportResult;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketType;
import com.ibrasoft.tcketmanagebackend.repository.EventRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketRepository;
import com.ibrasoft.tcketmanagebackend.repository.TicketTypeRepository;
import com.ibrasoft.tcketmanagebackend.service.order.InventoryService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Imports an attendee CSV into an existing event, creating immediately-scannable {@code ACTIVE}
 * tickets with no order or payment. Resolution of the ticket type per row comes from the configured
 * ticket-type column (by name within the event) or a default type. Import is all-or-nothing: if any
 * row is invalid, nothing is persisted and the row errors are returned for the operator to fix.
 *
 * <p>SECURITY: the whole import runs in one transaction and accumulates every parsed row in memory,
 * so the row count is capped ({@code tcketmanage.import.max-rows}). Spring Boot's multipart limits
 * bound the number of <em>bytes</em> uploaded, not the number of rows those bytes decode to — a
 * 14-byte minimal CSV row means Boot's 1 MB default file limit still admits ~75,000 rows, each of
 * which becomes a heap-resident {@link Ticket} plus an INSERT held open in a single transaction.
 * Rejected alternative: streaming/batching the inserts, which would give up the all-or-nothing
 * guarantee that operators rely on to fix a bad file and retry cleanly.
 */
@Service
@RequiredArgsConstructor
public class ImportService {

    /**
     * Default ceiling on CSV data rows per import. Sized to be far above any plausible real roster
     * (the largest events this runs for are in the low thousands of attendees) while still bounding
     * the single-transaction batch to something a modest heap and a normal statement timeout can
     * absorb. Operators with a genuinely larger roster raise the property or split the file.
     */
    static final int DEFAULT_MAX_ROWS = 10_000;

    /**
     * Ticket-holder name column width, mirroring {@code Ticket.firstName}/{@code lastName}'s
     * {@code @Size(max = 50)}. Checked here so an over-long cell is reported as a row error the
     * operator can fix, instead of surfacing as a Hibernate {@code ConstraintViolationException}
     * at flush time — which aborts the whole import with a 500 and no row number.
     */
    private static final int MAX_NAME_LENGTH = 50;

    /** Email column width, mirroring the {@code tcket:tickets.email} column. */
    private static final int MAX_EMAIL_LENGTH = 255;

    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final InventoryService inventoryService;

    /**
     * Maximum number of CSV data rows accepted in one import; a file with more is rejected outright
     * (400) rather than partially imported. Field-injected rather than a constructor parameter so
     * the Mockito {@code @InjectMocks} tests keep getting the default without having to model a
     * property source; tests that exercise the cap set it directly.
     */
    @Setter(AccessLevel.PACKAGE)
    @Value("${tcketmanage.import.max-rows:" + DEFAULT_MAX_ROWS + "}")
    private int maxRows = DEFAULT_MAX_ROWS;

    @Transactional
    public ImportResult importAttendees(UUID eventId, MultipartFile file, ImportConfig cfg) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found"));

        if (cfg.getTicketTypeColumn() == null && cfg.getDefaultTicketTypeId() == null) {
            throw new IllegalArgumentException(
                "Provide either ticketTypeColumn or defaultTicketTypeId");
        }

        Map<String, TicketType> typesByName = new HashMap<>();
        for (TicketType type : ticketTypeRepository.findByEvent_Id(eventId)) {
            typesByName.put(type.getName().trim().toLowerCase(), type);
        }

        TicketType defaultType = null;
        if (cfg.getDefaultTicketTypeId() != null) {
            defaultType = ticketTypeRepository.findById(cfg.getDefaultTicketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Default ticket type not found"));
            if (defaultType.getEvent() == null || !defaultType.getEvent().getId().equals(eventId)) {
                throw new IllegalArgumentException("Default ticket type does not belong to this event");
            }
        }

        List<ImportResult.RowError> errors = new ArrayList<>();
        List<Ticket> toSave = new ArrayList<>();
        Map<UUID, Integer> perTypeCount = new HashMap<>();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.parse(reader)) {
            int rowNum = 0;
            int dataRows = 0;
            for (CSVRecord record : parser) {
                rowNum++;
                if (cfg.isHasHeaderRow() && rowNum == 1) {
                    continue;
                }
                // SECURITY: bail out on the row that crosses the ceiling rather than after parsing
                // the file, so a hostile or accidental multi-hundred-thousand-row CSV never
                // materialises as that many Ticket instances (or RowErrors) on the heap. Thrown, not
                // collected as a row error, because the file as a whole is unacceptable — there is
                // nothing per-row for the operator to fix.
                if (++dataRows > maxRows) {
                    throw new IllegalArgumentException(
                        "CSV has more than the maximum of " + maxRows + " data rows per import; "
                        + "split the file or raise tcketmanage.import.max-rows");
                }
                try {
                    String firstName = column(record, cfg.getFirstNameColumn());
                    String lastName = column(record, cfg.getLastNameColumn());
                    String email = column(record, cfg.getEmailColumn());
                    if (isBlank(firstName) || isBlank(lastName) || isBlank(email)) {
                        throw new IllegalArgumentException("Missing required attendee field");
                    }
                    firstName = requireLength(firstName.trim(), MAX_NAME_LENGTH, "First name");
                    lastName = requireLength(lastName.trim(), MAX_NAME_LENGTH, "Last name");
                    email = requireDeliverableEmail(email.trim());

                    TicketType type = resolveType(record, cfg, typesByName, defaultType);
                    toSave.add(Ticket.builder()
                            .ID(UUID.randomUUID())
                            .firstName(firstName)
                            .lastName(lastName)
                            .email(email)
                            .event(event)
                            .ticketType(type)
                            .status(TicketStatus.ACTIVE)
                            .build());
                    perTypeCount.merge(type.getId(), 1, Integer::sum);
                } catch (IllegalArgumentException rowError) {
                    errors.add(new ImportResult.RowError(rowNum, rowError.getMessage()));
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Could not read CSV file: " + e.getMessage());
        }

        if (errors.isEmpty()) {
            // Reserve capacity (may throw ConflictException → 409 and roll back) then persist.
            //
            // DEADLOCK SAFETY — do NOT "simplify" this back to perTypeCount.forEach(...).
            // Each reserve() is a conditional UPDATE that takes the ticket_types row lock, so an
            // import spanning several types acquires several row locks inside one transaction.
            // docs/LOCKING.MD rule 2 ("ticket-type rows in UUID order") requires every multi-type
            // path to acquire them in the same global order; InventoryService.tryReserveAll and
            // releaseAll both wrap their maps in a TreeMap for exactly this reason. A HashMap
            // iterates in hash-bucket order, which differs from UUID order and differs between maps,
            // so an import and a concurrent order touching the same two shared types could each
            // hold the row the other wants. The TreeMap is the fix; it is the only thing keeping
            // this path consistent with every other reserver in the system.
            new TreeMap<>(perTypeCount).forEach(inventoryService::reserve);
            ticketRepository.saveAll(toSave);
        }

        return ImportResult.builder()
                .imported(errors.isEmpty() ? toSave.size() : 0)
                .errors(errors)
                .build();
    }

    private TicketType resolveType(CSVRecord record, ImportConfig cfg,
                                   Map<String, TicketType> typesByName, TicketType defaultType) {
        if (cfg.getTicketTypeColumn() != null) {
            String name = column(record, cfg.getTicketTypeColumn());
            if (!isBlank(name)) {
                TicketType type = typesByName.get(name.trim().toLowerCase());
                if (type == null) {
                    throw new IllegalArgumentException("Unknown ticket type: " + name);
                }
                return type;
            }
        }
        if (defaultType != null) {
            return defaultType;
        }
        throw new IllegalArgumentException("No ticket type for row and no default configured");
    }

    /**
     * Rejects a row whose email address JavaMail cannot parse as a deliverable RFC-822 address.
     *
     * <p>SECURITY: this is <em>not</em> a header-injection fix — there is none to make here.
     * {@link InternetAddress#parse} rejects the CR/LF that a header-injection payload needs, and the
     * mailer runs subjects through {@code MimeUtility.encodeText}, so a malformed address could never
     * have forged headers. The value is failing the import <em>loudly and per row</em> instead of
     * persisting a ticket whose holder can never be emailed: bulk delivery is a separate, later,
     * asynchronous job ({@code TicketDeliveryService}), so without this check a garbage address shows
     * up only as one failed send buried in a job that already reported success for the import.
     *
     * <p>Validated with JavaMail rather than a hand-rolled regex deliberately: JavaMail is the exact
     * parser the delivery path will later hand the address to, so "accepted here" and "sendable
     * later" cannot disagree.
     *
     * <p>The bare-addr-spec check is not redundant. JavaMail happily parses the display-name form
     * ({@code Jane Doe <jane@x.com>}), but the cell is stored verbatim into {@code Ticket.email},
     * whose {@code @Email} constraint rejects that form — so accepting it here would just move the
     * failure to Hibernate's flush, which aborts the whole import with a 500 naming no row. Requiring
     * the cell to be exactly the address it parses to keeps both validators in agreement.
     */
    private String requireDeliverableEmail(String email) {
        requireLength(email, MAX_EMAIL_LENGTH, "Email");
        try {
            InternetAddress address = new InternetAddress(email, true);
            address.validate();
            if (!email.equals(address.getAddress())) {
                throw new IllegalArgumentException(
                    "Email must be a plain address with no display name: " + email);
            }
        } catch (AddressException e) {
            throw new IllegalArgumentException("Invalid email address: " + email);
        }
        return email;
    }

    private String requireLength(String value, int max, String fieldName) {
        if (value.length() > max) {
            throw new IllegalArgumentException(
                fieldName + " exceeds the maximum of " + max + " characters");
        }
        return value;
    }

    private String column(CSVRecord record, int index) {
        if (index < 0 || index >= record.size()) {
            throw new IllegalArgumentException("Column index " + index + " is out of range");
        }
        return record.get(index);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
