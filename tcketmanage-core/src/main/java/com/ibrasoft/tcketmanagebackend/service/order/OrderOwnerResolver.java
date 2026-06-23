package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.dto.request.CreateOrderRequest;

/**
 * Host-provided strategy for stamping an order with an opaque <em>owner reference</em> at creation time.
 *
 * <p>Core has no user model: an order's only built-in identity is {@code buyerEmail}. An embedding host
 * (e.g. LensBridge/Minbar) that has its own accounts implements this interface so each order it creates
 * is tagged with the host's own identifier for the purchasing user. Core stores and indexes that value
 * (see {@code Order.externalRef}) but <strong>never interprets it</strong>; it is the host's token to
 * later reverse-look-up "all orders for user X" via {@code OrderRepository.findByExternalRef}.
 *
 * <p>The value is deliberately opaque and namespacing is the host's choice — a raw user id, a UUID, or a
 * namespaced token like {@code "lensbridge:user:1234"} so multiple hosts can't collide.
 *
 * <p><strong>Invocation:</strong> called on the request thread during {@code OrderService.createOrder},
 * before any inventory is reserved, so implementations may read the host's
 * {@code org.springframework.security.core.context.SecurityContextHolder}. Returning {@code null} marks
 * the order as anonymous/guest. When no host bean is supplied, core's default resolver returns
 * {@code null} (preserving guest checkout). A deployment can require a non-null owner by setting
 * {@code tcketmanage.orders.require-owner=true} (see {@link OrderProperties}).
 *
 * <p>This intentionally mirrors the {@code PaymentProvider} SPI pattern: core defines the interface and a
 * sensible default, the host wires the real implementation.
 */
@FunctionalInterface
public interface OrderOwnerResolver {

    /**
     * @param request the order being created (e.g. for access to {@code buyerEmail} or items)
     * @return an opaque owner reference for this order, or {@code null} for an anonymous/guest order
     */
    String resolveOwnerRef(CreateOrderRequest request);
}
