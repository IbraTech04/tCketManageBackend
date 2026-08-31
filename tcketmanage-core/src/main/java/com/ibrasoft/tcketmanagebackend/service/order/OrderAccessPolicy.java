package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.exception.ResourceNotFoundException;
import com.ibrasoft.tcketmanagebackend.properties.OrderProperties;
import com.ibrasoft.tcketmanagebackend.security.AuthorizationGateway;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides whether the current caller may see or act on an owned resource — an order, or a ticket
 * issued from one.
 *
 * <h2>Owner-if-owned</h2>
 * The rule is deliberately conditional on there <em>being</em> an owner:
 * <ul>
 *   <li>the resource carries an owner ref and it matches the caller's → allowed;</li>
 *   <li>the resource carries an owner ref and it does not match → denied;</li>
 *   <li>the caller is an operator ({@link AuthorizationGateway#isOperator()}) → allowed, so staff
 *       can support a buyer they are not;</li>
 *   <li>the resource has <strong>no</strong> owner ref → allowed.</li>
 * </ul>
 *
 * <p>That last case is the important one. Core supports guest checkout, where there is no account to
 * own anything and the unguessable UUID in the URL is itself the credential. Gating unconditionally
 * on owner refs would lock guest buyers out of the orders they had just paid for, which is not a
 * security improvement so much as a broken product. A deployment that wants no anonymous orders at
 * all closes this by setting {@code tcketmanage.orders.require-owner=true} (see
 * {@link OrderProperties}), after which every order has an owner and the fallback never applies.
 *
 * <h2>Not found, not forbidden</h2>
 * A refusal raises {@link ResourceNotFoundException} (404) rather than a 403. Answering "forbidden"
 * would confirm that an order with that id exists, letting someone probe for valid ids by watching
 * which ones change status code. 404 tells an unauthorized caller exactly what a nonexistent id
 * would.
 */
@Component
@AllArgsConstructor
public class OrderAccessPolicy {

    private final OrderOwnerResolver ownerResolver;
    private final AuthorizationGateway authz;

    /**
     * @param ownerRef the stored owner reference ({@code Order.externalRef} or
     *                 {@code Ticket.holderRef}); {@code null} for guest-owned resources
     */
    public boolean canAccess(String ownerRef) {
        if (ownerRef == null) {
            return true;
        }
        if (authz.isOperator()) {
            return true;
        }
        return ownerRef.equals(ownerResolver.currentOwnerRef());
    }

    /**
     * Enforces {@link #canAccess(String)}, reporting a denial as if the resource did not exist.
     *
     * @param resource human-readable resource name for the message, e.g. {@code "Order"}
     */
    public void requireAccess(String ownerRef, String resource) {
        if (!canAccess(ownerRef)) {
            throw new ResourceNotFoundException(resource + " not found");
        }
    }
}
