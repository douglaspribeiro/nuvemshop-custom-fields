package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDate;
import java.util.function.Supplier;

@Component
public class PlanCatalogCache {
    private final StorefrontCache cache;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanCatalogCache(MeterRegistry registry, @Value("${plans.cache.ttl:30m}") Duration ttl) {
        this(registry, ttl, com.github.benmanes.caffeine.cache.Ticker.systemTicker());
    }

    PlanCatalogCache(MeterRegistry registry, Duration ttl, com.github.benmanes.caffeine.cache.Ticker ticker) {
        cache = new StorefrontCache(registry, ttl, 128, ticker, "plans");
    }

    public PlanAsset activePlan(PlanType type, LocalDate date, Supplier<PlanAsset> loader) {
        // Transactional callers retain managed entity semantics and see their own writes.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            return loader.get();
        PlanAsset.Snapshot snapshot = cache.get("plan", (long) type.ordinal(), date.toEpochDay(),
                () -> loader.get().snapshot());
        return snapshot.toAsset();
    }

    public void invalidateAfterCommit() { cache.invalidateAfterCommit(); }
}
