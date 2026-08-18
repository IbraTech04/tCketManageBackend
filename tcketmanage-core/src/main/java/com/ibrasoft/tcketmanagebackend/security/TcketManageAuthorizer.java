package com.ibrasoft.tcketmanagebackend.security;

/**
 * Host-provided strategy deciding what the current caller is allowed to do in tCketManage.
 *
 * <p>Core's operator endpoints ask this three questions rather than naming roles directly, because
 * a role name is not a portable way to express permission. An embedding host may not model
 * authorization as roles at all — LensBridge, for one, expands roles into permissions and checks
 * only permissions — so a library that can only compare role strings cannot be wired into it
 * faithfully. Implementations are free to consult roles, permissions, scopes, tenancy, or anything
 * else they know about the caller.
 *
 * <p><strong>To override:</strong> declare a bean of this type. Any bean name will do — core's
 * {@link AuthorizationGateway} discovers it by type and delegates to it, so the name used in core's
 * {@code @PreAuthorize} expressions never has to change. When no host bean is present, core falls
 * back to {@link RoleBasedAuthorizer}, preserving the previous role-name behaviour.
 *
 * <p>Called on the request thread, so implementations may read
 * {@code org.springframework.security.core.context.SecurityContextHolder}.
 *
 * <p>This mirrors the {@code PaymentProvider} and {@link com.ibrasoft.tcketmanagebackend.service.order.OrderOwnerResolver}
 * SPI pattern: core defines the interface and a sensible default, the host wires the real one.
 */
public interface TcketManageAuthorizer {

    /** May scan and validate tickets, and read scan history. */
    boolean canScan();

    /** May set up events, issue tickets, manage the roster, and read the order book. */
    boolean canManageEvents();

    /** May perform destructive deletes and settle payments manually. */
    boolean canAdminister();

    /**
     * Whether the caller acts on behalf of the organisation rather than as a buyer. Used to let
     * staff read or cancel an order they do not personally own; see
     * {@link com.ibrasoft.tcketmanagebackend.service.order.OrderAccessPolicy}.
     */
    default boolean isOperator() {
        return canManageEvents() || canAdminister();
    }
}
