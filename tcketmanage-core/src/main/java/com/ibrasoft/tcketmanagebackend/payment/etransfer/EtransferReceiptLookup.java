package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import com.ibrasoft.tcketmanagebackend.repository.EtransferReceiptRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the e-Transfer receipt trail: resolves the newest receipt per order for the API layer.
 *
 * <p>An order can accumulate more than one receipt - a transfer that was quarantined for the wrong
 * amount, then a corrected one that settled - so "the" receipt for an order is always the most recent.
 *
 * <p>{@link #latestByOrderId(Collection)} exists so an order listing resolves the whole page in a
 * single query. Navigating from each {@code Order} instead would be an N+1, which is the trap
 * {@code OrderRepository.findByIdWithItems} already exists to avoid elsewhere in this codebase.
 */
@Service
@AllArgsConstructor
public class EtransferReceiptLookup {

    private final EtransferReceiptRepository receiptRepository;

    /** The newest receipt for one order, or {@code null} if no e-Transfer has been seen for it. */
    @Transactional(readOnly = true)
    public EtransferReceipt latestFor(UUID orderId) {
        return receiptRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * The newest receipt for each of the given orders, keyed by order id. Orders with no receipt are
     * simply absent from the map. One query regardless of page size.
     */
    @Transactional(readOnly = true)
    public Map<UUID, EtransferReceipt> latestByOrderId(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<EtransferReceipt> receipts =
                receiptRepository.findByOrderIdInOrderByCreatedAtDesc(orderIds);
        // Newest first, so the first receipt seen for an id wins and later (older) ones are discarded.
        return receipts.stream().collect(Collectors.toMap(
                EtransferReceipt::getOrderId, Function.identity(), (newest, older) -> newest));
    }
}
