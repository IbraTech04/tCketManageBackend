package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.model.order.Order;
import com.ibrasoft.tcketmanagebackend.payment.PaymentInitiation;

/**
 * The outcome of creating an order: the persisted order plus how the buyer should pay.
 */
public record OrderCreationResult(Order order, PaymentInitiation initiation) {}
