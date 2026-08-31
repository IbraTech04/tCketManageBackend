package com.ibrasoft.tcketmanagebackend.repository;

import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface EtransferReceiptRepository extends JpaRepository<EtransferReceipt, UUID> {

    /**
     * Every receipt for the given orders, newest first. Takes a collection so an order listing can
     * resolve receipts for the whole page in one query rather than one per row; callers keep the
     * first receipt seen per order id to get the newest.
     */
    List<EtransferReceipt> findByOrderIdInOrderByCreatedAtDesc(Collection<UUID> orderIds);

    /** Receipts for one order, newest first. */
    List<EtransferReceipt> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    /**
     * The review queue: payments that reached us but belong to no order and have not been written off.
     *
     * <p>Newest first, because an operator working the queue wants today's payment before one from
     * three weeks ago — the buyer of a recent one is still waiting and their seats may still be
     * holdable. Matches the partial index added in V4.
     */
    List<EtransferReceipt> findByOrderIsNullAndDismissedAtIsNullOrderByEmailReceivedAtDesc();
}
