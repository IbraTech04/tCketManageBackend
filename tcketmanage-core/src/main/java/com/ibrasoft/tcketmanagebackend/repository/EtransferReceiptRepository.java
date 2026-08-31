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
}
