package com.ibrasoft.tcketmanagebackend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for updating a ticket type. The owning event cannot be changed; the entitlement
 * list, when provided, replaces the existing set.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTicketTypeRequest {

    @NotBlank
    private String name;

    @NotNull
    private BigDecimal price;

    private Boolean isActive = true;

    /**
     * Maximum seats that may be sold for this type. {@code null} means unlimited, matching
     * {@code TicketType#capacity}.
     *
     * <p>SECURITY: this is the one field on this DTO that is <em>not</em> a blind full replacement,
     * and deliberately so. Every other field is overwritten from the request even when the client
     * omits it, because losing a name or a sales window is a visible, recoverable mistake. Losing a
     * capacity is neither: it silently switches the type to unlimited, and
     * {@code TicketTypeRepository.reserveSeats}' {@code capacity IS NULL} branch then matches
     * unconditionally, so the type oversells with no error, no 409, and no log line until people
     * are turned away at the door. Because {@code capacity} did not exist on this DTO until now,
     * <em>every</em> client written before this change omits it — an admin renaming a sold-out type
     * through an un-upgraded UI would have uncapped it. So an absent field leaves the stored
     * capacity untouched ({@link #capacityPresent}), while an explicit {@code "capacity": null}
     * still means "remove the cap".
     *
     * <p>Rejected alternative: treating absent and explicit-null alike, i.e. plain replacement
     * semantics. That is more consistent with the rest of the DTO but reintroduces exactly the
     * oversell hole this change was written to close, and it is inconsistent in the direction that
     * matters — lowering a cap of 50 to 49 with 50 seats held is refused with a
     * {@code ConflictException}, so silently accepting 50 to unlimited would be strictly worse than
     * the case already guarded.
     *
     * <p>Lowering this below the type's current {@code reservedCount} is rejected with a
     * {@code ConflictException} — see {@code TicketTypeService#updateTicketType}. Those seats are
     * already held by live orders, so accepting the edit would put the row in a state the oversell
     * invariant forbids and that the V2 {@code ck_ticket_types_reserved_within_capacity} CHECK
     * would refuse to store anyway.
     */
    @Min(0)
    private Integer capacity;

    /**
     * Whether the request body actually carried a {@code capacity} property, as opposed to omitting
     * it. Derived state, not an input: Jackson invokes {@link #setCapacity(Integer)} only for a
     * property that is present in the JSON, so the setter flipping this flag is what distinguishes
     * {@code {"capacity": null}} (clear the cap) from a body with no {@code capacity} key at all
     * (leave it alone). {@code @JsonIgnore} keeps a client from setting the flag directly.
     *
     * <p>Note for anyone constructing this DTO in Java: the Lombok all-args constructor takes this
     * flag as an argument, while the {@code @NoArgsConstructor}-plus-setters path used by Jackson
     * and by the tests maintains it on its own.
     */
    @JsonIgnore
    private boolean capacityPresent;

    private Instant salesStartAt;

    private Instant salesEndAt;

    @Valid
    private List<ZoneEntitlementRequest> entitlements = new ArrayList<>();

    /**
     * Records that the caller supplied a capacity — including an explicit {@code null} — before
     * storing it. Hand-written rather than Lombok-generated precisely so this bookkeeping happens;
     * see {@link #capacityPresent}.
     */
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        this.capacityPresent = true;
    }
}
