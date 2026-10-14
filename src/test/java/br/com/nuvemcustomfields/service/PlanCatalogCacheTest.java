package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.assertThat;

class PlanCatalogCacheTest {
    @Test void cachesForThirtyMinutesWithIndependentCopiesAndDateAwareKeys() {
        AtomicLong clock = new AtomicLong();
        AtomicInteger queries = new AtomicInteger();
        var cache = new PlanCatalogCache(new SimpleMeterRegistry(), Duration.ofMinutes(30), clock::get);
        var date = LocalDate.of(2026, 10, 14);
        Supplier<PlanAsset> load = () -> {
            queries.incrementAndGet(); var plan = new PlanAsset();
            plan.setPlanType(PlanType.PREMIUM); plan.setProductLimit(10); return plan;
        };
        var first = cache.activePlan(PlanType.PREMIUM, date, load);
        first.setProductLimit(999);
        clock.set(Duration.ofMinutes(29).plusSeconds(59).toNanos());
        assertThat(cache.activePlan(PlanType.PREMIUM, date, load).getProductLimit()).isEqualTo(10);
        assertThat(queries).hasValue(1);
        clock.set(Duration.ofMinutes(30).toNanos());
        cache.activePlan(PlanType.PREMIUM, date, load);
        assertThat(queries).hasValue(2);
        cache.activePlan(PlanType.PREMIUM, date.plusDays(1), load);
        assertThat(queries).hasValue(3);
        cache.invalidateAfterCommit();
        cache.activePlan(PlanType.PREMIUM, date.plusDays(1), load);
        assertThat(queries).hasValue(4);
    }
}
