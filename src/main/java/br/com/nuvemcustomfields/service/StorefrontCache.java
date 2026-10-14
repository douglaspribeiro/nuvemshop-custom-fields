package br.com.nuvemcustomfields.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Only immutable response DTOs and booleans; never managed JPA entities. */
@Component
public class StorefrontCache {
    private final Cache<Key, Object> entries;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @org.springframework.beans.factory.annotation.Autowired
    public StorefrontCache(MeterRegistry registry,
            @Value("${storefront.cache.ttl:25s}") Duration ttl,
            @Value("${storefront.cache.maximum-size:10000}") long maximumSize) {
        this(registry, ttl, maximumSize, com.github.benmanes.caffeine.cache.Ticker.systemTicker());
    }

    StorefrontCache(MeterRegistry registry, Duration ttl, long maximumSize,
            com.github.benmanes.caffeine.cache.Ticker ticker) {
        entries = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maximumSize).ticker(ticker).recordStats().build();
        CaffeineCacheMetrics.monitor(registry, entries, "storefront");
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String kind, Long storeId, Long productId, Supplier<T> loader) {
        // A transactional read must not publish uncommitted data into the shared cache.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return loader.get();
        lock.readLock().lock();
        try {
            return (T) entries.get(new Key(kind, storeId, productId), ignored -> loader.get());
        } finally {
            lock.readLock().unlock();
        }
    }

    public void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate(); }
            });
        } else invalidate();
    }

    private void invalidate() {
        lock.writeLock().lock();
        try { entries.invalidateAll(); }
        finally { lock.writeLock().unlock(); }
    }

    private record Key(String kind, Long storeId, Long productId) {}
}
