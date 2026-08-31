package com.ibrasoft.tcketmanagebackend.payment.etransfer;

import com.ibrasoft.tcketmanagebackend.model.payment.EtransferReceipt;
import com.ibrasoft.tcketmanagebackend.repository.EtransferReceiptRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists an {@link EtransferReceipt} in a transaction of its own.
 *
 * <p>This is a separate bean, and {@link Propagation#REQUIRES_NEW}, for a reason that is easy to get
 * wrong. It is tempting to make {@link EtransferConfirmationService#process} transactional so the
 * receipt and the order transition commit together, but that breaks the error path: when
 * {@code confirmPayment} throws, an enclosing transaction is marked rollback-only, and the
 * quarantine that {@code process} then attempts would fail at commit with
 * {@code UnexpectedRollbackException} instead of setting the order aside. The existing behaviour -
 * each transition in its own transaction - is what makes that path work.
 *
 * <p>Independence is also the right semantics. A receipt is an audit record of <em>the email</em>,
 * not part of the payment transition, so it must survive a confirmation that rolled back; that is
 * precisely the case an operator most needs to see. A self-invoked or same-transaction write would
 * vanish with the rollback.
 *
 * <p>The method is public because Spring's proxy-based transaction advice is not applied to
 * package-private methods; callers are expected to catch, since an audit write must never turn a
 * settled payment into a failure.
 */
@Service
@AllArgsConstructor
public class EtransferReceiptRecorder {

    private final EtransferReceiptRepository receiptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EtransferReceipt record(EtransferReceipt receipt) {
        return receiptRepository.save(receipt);
    }
}
