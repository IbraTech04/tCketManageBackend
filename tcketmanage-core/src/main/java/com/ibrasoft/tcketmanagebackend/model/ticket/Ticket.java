package com.ibrasoft.tcketmanagebackend.model.ticket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.tcketmanagebackend.model.event.Event;
import com.ibrasoft.tcketmanagebackend.model.order.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import jakarta.validation.constraints.Email;

import java.time.Instant;
import java.util.UUID;

/**
 * POJO class representing a Ticket entity.
 *
 * <h2>Fetch strategy of the to-one associations</h2>
 *
 * <p>{@code event} and {@code ticketType} are deliberately left EAGER; {@code order} is LAZY. The
 * split is not stylistic, so it is worth recording why each way round:
 *
 * <ul>
 *   <li><b>{@code event} / {@code ticketType} must stay EAGER.</b> Ticket emails render on the
 *       {@code emailExecutor} pool ({@code AsyncConfig}), and {@code TicketEmailSender.load} hands
 *       the async thread a <em>detached</em> Ticket on purpose — the read transaction closes before
 *       the slow SMTP/Batik work starts, so a DB connection is not pinned for the length of each
 *       send. {@code SmtpEmailService.sendTicket} and {@code TicketGenerationService} then read
 *       {@code event.name/location/time}, {@code ticketType.name} and {@code event.id} (via
 *       {@code TicketQRData.fromTicket}) with no session open. Lazy proxies would throw
 *       {@code LazyInitializationException} there — and {@code sendTicket} swallows every exception
 *       and merely returns false, so the ticket would simply never arrive, silently.
 *       {@code spring.jpa.open-in-view=true} ({@code OpenInViewRequirement}) does not help: it binds
 *       a session to the <em>request</em> thread, and this work does not run on one.</li>
 *   <li><b>{@code order} is LAZY.</b> Nothing outside a session ever navigates it — no service reads
 *       {@code ticket.getOrder()}, and {@code TicketResponse.from} does not map it — so nothing has
 *       to initialize the proxy. Left EAGER it was a pure tax: because {@code Order} in turn holds an
 *       EAGER {@code event}, every single {@code find}/{@code findById} of a Ticket dragged in
 *       {@code left join orders} plus a nested {@code left join events} and materialized a whole
 *       Order that was then discarded.</li>
 * </ul>
 *
 * <p>Note on pessimistic locking, because the obvious worry is the wrong one: on Hibernate 6.6 an
 * EAGER to-one is <em>not</em> join-fetched by an HQL query. {@code TicketRepository.findByIdForUpdate}
 * (an HQL {@code @Query} plus {@code @Lock(PESSIMISTIC_WRITE)}) renders as a join-free single-table
 * select ending in {@code for no key update}, so it takes a genuine row lock and does not fall back
 * to follow-on locking. The outer joins above appear only on the {@code find()} entity-loader path,
 * which nothing locks through. Making {@code event}/{@code ticketType} LAZY would therefore buy the
 * scan-time lock nothing while breaking email delivery.
 */
@Data
@Table(name = "tcket:tickets",
       indexes = @Index(name = "idx_ticket_holder_ref", columnList = "holder_ref"))
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    /**
     * The unique identifier for the ticket.
     *
     * This ID acts as both a primary key for the database, and a value to be used in QR Code Generation
     */
    @Id
    private UUID ID;

    /**
     * The event this ticket is for. EAGER on purpose — the async ticket-email render reads it off a
     * detached entity with no session open. See the fetch-strategy note on the class.
     */
    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    /**
     * The first name of the ticket holder.
     */
    @Size(min = 1, max = 50)
    @NotBlank
    private String firstName;

    /**
     * The last name of the ticket holder.
     */
    @Size(min = 1, max = 50)
    @NotBlank
    private String lastName;

    /**
     * The email address of the ticket holder.
     */
    @Email
    @NotBlank
    private String email;

    /**
     * The type this ticket was issued as — it carries the price and the zone entitlements. EAGER for
     * the same reason as {@link #event}; see the fetch-strategy note on the class.
     */
    @ManyToOne
    @JoinColumn(name = "ticket_type_id")
    @NotNull
    private TicketType ticketType;

    /**
     * Opaque, host-owned reference identifying who <em>holds</em> this ticket (i.e. who may use/show
     * it), as distinct from who purchased the order ({@code Order.externalRef}). Core never interprets
     * it; it is indexed for reverse lookup ({@code TicketRepository.findByHolderRef}) so an embedding
     * host can render a "my tickets / wallet" view. Defaulted to the purchasing order's
     * {@code externalRef} at issuance; any later transfer/reassignment (and its auditing) is the host's
     * concern. {@code null} for anonymous/guest orders.
     */
    @Column(name = "holder_ref", length = 200)
    private String holderRef;

    /**
     * Lifecycle state of the ticket. Defaults to {@code ACTIVE}.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.ACTIVE;

    /**
     * The order this ticket was issued from, if any (tickets are materialized on payment).
     *
     * <p>LAZY — see the fetch-strategy note on the class. {@code @ToString.Exclude} and
     * {@code @EqualsAndHashCode.Exclude} go with it: Lombok's {@code @Data} would otherwise walk this
     * association from {@code toString()}/{@code equals()}, which initializes the proxy and throws
     * {@code LazyInitializationException} the moment anything logs or compares a detached Ticket.
     * Excluding it also keeps Ticket equality on the ticket's own fields, where it belongs.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    /**
     * When this ticket was last successfully emailed to its holder, or {@code null} if it has never
     * been sent. Drives the "send missing" delivery flow and lets operators see delivery status.
     */
    @Column(name = "last_ticket_sent")
    private Instant lastTicketSent;
}
