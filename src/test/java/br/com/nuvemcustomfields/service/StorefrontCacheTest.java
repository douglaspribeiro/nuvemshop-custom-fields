package br.com.nuvemcustomfields.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;

class StorefrontCacheTest {
    @Test void storeInvalidationPreservesOtherStores() {
        var cache = new StorefrontCache(new SimpleMeterRegistry(), Duration.ofMinutes(1), 100);
        cache.get("store-state", 1L, null, () -> "empty");
        cache.get("personalization", 2L, 10L, () -> "warm");
        cache.invalidateStoreAfterCommit(1L);
        assertThat(cache.get("store-state", 1L, null, () -> "configured")).isEqualTo("configured");
        assertThat(cache.get("personalization", 2L, 10L, () -> "unexpected")).isEqualTo("warm");
    }

    @Test void expiresAfterWriteRatherThanAfterAccessAndSeparatesStoresProductsAndKinds() {
        AtomicLong clock = new AtomicLong();
        var cache = new StorefrontCache(new SimpleMeterRegistry(), Duration.ofMinutes(1), 100, clock::get);
        assertThat(cache.get("style", 1L, null, () -> "first")).isEqualTo("first");
        clock.set(Duration.ofSeconds(59).toNanos());
        assertThat(cache.get("style", 1L, null, () -> "unexpected")).isEqualTo("first");
        assertThat(cache.get("style", 2L, null, () -> "other-store")).isEqualTo("other-store");
        assertThat(cache.get("personalization", 1L, 3L, () -> "product")).isEqualTo("product");
        clock.set(Duration.ofMinutes(1).toNanos());
        assertThat(cache.get("style", 1L, null, () -> "fresh")).isEqualTo("fresh");
    }
    @Test void invalidatesOnlyAfterCommitAndBypassesTransactionalReads() {
        var cache = new StorefrontCache(new SimpleMeterRegistry(), Duration.ofMinutes(1), 100);
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

    @Test void invalidationDoesNotWaitForDatabaseLoadAndInflightResultCannotRepopulateNewGeneration() throws Exception {
        var cache = new StorefrontCache(new SimpleMeterRegistry(), Duration.ofMinutes(1), 100);
        var loading = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = executor.submit(() -> cache.get("personalization", 1L, 10L, () -> {
                loading.countDown();
                try { release.await(); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return "before-commit";
            }));
            try {
                assertThat(loading.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                // A committing transaction still owns its JDBC connection here. It must never
                // wait for a cache reader that could itself be waiting for a JDBC connection.
                executor.submit(cache::invalidateAfterCommit).get(2, java.util.concurrent.TimeUnit.SECONDS);
                var fresh = executor.submit(() -> cache.get("personalization", 1L, 10L, () -> "after-commit"));
                assertThat(fresh.get(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("after-commit");
            } finally { release.countDown(); }
            assertThat(pending.get(2, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("before-commit");
            assertThat(cache.get("personalization", 1L, 10L, () -> "unexpected")).isEqualTo("after-commit");
        }
    }
}
