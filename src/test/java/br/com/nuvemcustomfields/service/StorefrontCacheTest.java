package br.com.nuvemcustomfields.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

class StorefrontCacheTest {
    @Test void expiresAfterWriteRatherThanAfterAccessAndSeparatesStoresProductsAndKinds() {
        AtomicLong clock = new AtomicLong();
        var cache = new StorefrontCache(new SimpleMeterRegistry(), Duration.ofSeconds(25), 100, clock::get);
        assertThat(cache.get("style", 1L, null, () -> "first")).isEqualTo("first");
        clock.set(Duration.ofSeconds(24).toNanos());
        assertThat(cache.get("style", 1L, null, () -> "unexpected")).isEqualTo("first");
        assertThat(cache.get("style", 2L, null, () -> "other-store")).isEqualTo("other-store");
        assertThat(cache.get("personalization", 1L, 3L, () -> "product")).isEqualTo("product");
        clock.set(Duration.ofSeconds(25).toNanos());
        assertThat(cache.get("style", 1L, null, () -> "fresh")).isEqualTo("fresh");
    }
    @Test void invalidatesOnlyAfterCommitAndBypassesTransactionalReads() {
        var cache = new StorefrontCache(new SimpleMeterRegistry(), Duration.ofSeconds(25), 100);
        cache.get("active", 1L, null, () -> true);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            cache.invalidateAfterCommit();
            assertThat(cache.get("active", 1L, null, () -> false)).isFalse();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            assertThat(cache.get("active", 1L, null, () -> false)).isTrue();
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(cache.get("active", 1L, null, () -> false)).isFalse();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
