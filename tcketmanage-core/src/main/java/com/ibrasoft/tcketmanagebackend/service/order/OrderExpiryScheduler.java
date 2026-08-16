package com.ibrasoft.tcketmanagebackend.service.order;

import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs the expired-order sweep on a thread core owns.
 *
 * <p>This replaces an {@code @Scheduled} method, which required core's auto-configuration to turn on
 * {@code @EnableScheduling} for the whole application. That had two costs an embedded library should
 * not impose. It switched on annotation-driven scheduling in hosts that had not asked for it; and
 * because Spring Boot's default {@code TaskScheduler} is single-threaded, core's sweep ran on the
 * very same thread as every {@code @Scheduled} method the host had — so a slow sweep delayed the
 * host's unrelated jobs, and vice versa.
 *
 * <p>Declaring a {@code TaskScheduler} bean instead would have made things worse rather than better:
 * a single {@code TaskScheduler} in the context is adopted by <em>all</em> annotation-driven
 * scheduling, so core would have quietly taken over the host's jobs as well. A private executor is
 * the only arrangement that leaves the host's scheduling exactly as core found it.
 *
 * <p>The thread is a daemon so it can never hold JVM shutdown open, and the executor is stopped on
 * context close.
 *
 * <p>{@code scheduleWithFixedDelay} (not {@code atFixedRate}) so the interval is measured between
 * the end of one sweep and the start of the next: a sweep that runs long delays the following one
 * rather than having runs queue up behind it. Exceptions are caught and logged, because an escaping
 * exception would cancel the schedule permanently and silently stop all future sweeps.
 */
@Component
public class OrderExpiryScheduler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryScheduler.class);

    private final ScheduledExecutorService executor;

    public OrderExpiryScheduler(OrderExpiryService expiryService, PaymentProperties properties) {
        long intervalMs = properties.getSweepIntervalMs();
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tcketmanage-order-expiry");
            thread.setDaemon(true);
            return thread;
        });
        this.executor.scheduleWithFixedDelay(
                () -> runSweep(expiryService), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("tCketManage order-expiry sweep scheduled every {} ms", intervalMs);
    }

    private void runSweep(OrderExpiryService expiryService) {
        try {
            expiryService.sweepExpiredOrders();
        } catch (Exception e) {
            // Never propagate: scheduleWithFixedDelay cancels the task on an escaping exception,
            // which would silently disable order expiry for the lifetime of the process.
            log.error("Order expiry sweep failed; will retry at the next interval", e);
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
