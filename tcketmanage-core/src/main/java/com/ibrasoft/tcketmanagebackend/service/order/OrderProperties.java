package com.ibrasoft.tcketmanagebackend.service.order;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Order-lifecycle configuration, bound from {@code tcketmanage.orders.*}.
 *
 * <p>Modeled on {@code SecurityRoleProperties}: a scanned {@code @ConfigurationProperties} POJO so the
 * standalone app and any host get sensible defaults with zero configuration.
 */
@Component
@ConfigurationProperties(prefix = "tcketmanage.orders")
@Data
public class OrderProperties {

    /**
     * When {@code true}, reject order creation whose {@link OrderOwnerResolver} yields no owner ref —
     * i.e. forbid anonymous/guest orders. Defaults to {@code false} (guest checkout allowed).
     *
     * <p>This is a fail-closed safety net <em>near the data</em>; the primary place an embedding host
     * enforces "must be logged in to buy" is still its own security filter chain on
     * {@code POST /api/v1/orders}. Core ships no filter chain of its own.
     */
    private boolean requireOwner = false;
}
