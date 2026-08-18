package com.ibrasoft.tcketmanagebackend.payment.provider;

import com.ibrasoft.tcketmanagebackend.payment.PaymentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the {@code tcketmanage.payments.<provider>.hold} contract: each provider's hold window is
 * whatever config says, in whatever unit config wrote it in.
 *
 * <p>Worth pinning because the hold is the only thing standing between an unpaid order and the
 * expiry sweep releasing its seats, and nothing else exercises it — {@code OrderTransactionsTest}
 * stubs {@code holdDuration()} outright, so a provider reading the wrong property or applying the
 * wrong unit would not fail a single other test.
 */
class ProviderHoldDurationTest {

    private PaymentProperties bind(Map<String, Object> properties) {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        return new Binder(source)
                .bind("tcketmanage.payments", Bindable.of(PaymentProperties.class))
                .orElseGet(PaymentProperties::new);
    }

    @Test
    void defaults_areThirtyMinutesForMockAndStripe_andFortyEightHoursForInterac() {
        PaymentProperties props = new PaymentProperties();

        assertEquals(Duration.ofMinutes(30), new MockPaymentProvider(props).holdDuration());
        assertEquals(Duration.ofMinutes(30), new StripePaymentProvider(props).holdDuration());
        assertEquals(Duration.ofHours(48), new InteracPaymentProvider(props).holdDuration());
        assertEquals(Duration.ofSeconds(60), props.getSweepInterval());
    }

    @Test
    void eachProviderReadsItsOwnHold() {
        PaymentProperties props = bind(Map.of(
                "tcketmanage.payments.mock.hold", "5m",
                "tcketmanage.payments.stripe.hold", "15m",
                "tcketmanage.payments.interac.hold", "90m",
                "tcketmanage.payments.sweep-interval", "10s"));

        assertEquals(Duration.ofMinutes(5), new MockPaymentProvider(props).holdDuration());
        assertEquals(Duration.ofMinutes(15), new StripePaymentProvider(props).holdDuration());
        assertEquals(Duration.ofMinutes(90), new InteracPaymentProvider(props).holdDuration());
        assertEquals(Duration.ofSeconds(10), props.getSweepInterval());
    }

    /**
     * The point of the Duration type: an operator can express the same hold in whichever unit reads
     * best, so shortening Interac's window from days to minutes is a value edit, not a key rename.
     */
    @Test
    void holdAcceptsAnyUnit_andABareNumberMeansMilliseconds() {
        assertEquals(Duration.ofHours(48),
                bind(Map.of("tcketmanage.payments.interac.hold", "2880m")).getInterac().getHold());
        assertEquals(Duration.ofHours(48),
                bind(Map.of("tcketmanage.payments.interac.hold", "2d")).getInterac().getHold());
        assertEquals(Duration.ofHours(48),
                bind(Map.of("tcketmanage.payments.interac.hold", "48h")).getInterac().getHold());
        assertEquals(Duration.ofSeconds(60),
                bind(Map.of("tcketmanage.payments.sweep-interval", "60000")).getSweepInterval());
    }
}
