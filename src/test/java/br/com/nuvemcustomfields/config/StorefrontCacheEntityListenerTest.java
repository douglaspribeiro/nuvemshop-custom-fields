package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.service.StorefrontCache;
import br.com.nuvemcustomfields.service.PlanCatalogCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StorefrontCacheEntityListenerTest {
    static Stream<Arguments> planChanges() {
        return Stream.of(PostPersist.class, PostUpdate.class, PostRemove.class)
                .flatMap(event -> Stream.of(true, false).map(commit -> Arguments.of(event, commit)));
    }

    @ParameterizedTest(name = "{0}: whole plan catalog invalidation, commit={1}")
    @MethodSource("planChanges")
    void planChangesInvalidateAllTypesAndDatesOnlyAfterCommit(
            Class<? extends Annotation> event, boolean commit) throws Exception {
        var registry = new SimpleMeterRegistry();
        try {
            var plans = new PlanCatalogCache(registry, Duration.ofHours(24));
            var beans = new StaticListableBeanFactory();
            beans.addBean("plans", plans);
            var listener = new StorefrontCacheEntityListener(beans.getBeanProvider(StorefrontCache.class),
                    beans.getBeanProvider(PlanCatalogCache.class));
            var queries = new java.util.concurrent.atomic.AtomicInteger();
            var date = java.time.LocalDate.of(2026, 10, 15);
            Runnable readCatalog = () -> {
                for (var type : br.com.nuvemcustomfields.entity.PlanType.values()) {
                    for (var day : java.util.List.of(date, date.plusDays(1))) {
                        plans.activePlan(type, day, () -> {
                            queries.incrementAndGet();
                            var plan = new br.com.nuvemcustomfields.entity.PlanAsset();
                            plan.setPlanType(type);
                            return plan;
                        });
                    }
                }
            };
            int entries = br.com.nuvemcustomfields.entity.PlanType.values().length * 2;
            readCatalog.run();
            assertThat(queries).hasValue(entries);
            assertThat(br.com.nuvemcustomfields.entity.PlanAsset.class.getAnnotation(EntityListeners.class).value())
                    .contains(StorefrontCacheEntityListener.class);
            var callback = Arrays.stream(StorefrontCacheEntityListener.class.getMethods())
                    .filter(method -> method.isAnnotationPresent(event)).findFirst().orElseThrow();
            TransactionSynchronizationManager.initSynchronization();
            try {
                callback.invoke(listener, new br.com.nuvemcustomfields.entity.PlanAsset());
                readCatalog.run();
                assertThat(queries).hasValue(entries);
                var synchronizations = TransactionSynchronizationManager.getSynchronizations();
                assertThat(synchronizations).isNotEmpty();
                if (commit) synchronizations.forEach(TransactionSynchronization::afterCommit);
                synchronizations.forEach(s -> s.afterCompletion(commit
                        ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            readCatalog.run();
            assertThat(queries).hasValue(commit ? entries * 2 : entries);
        } finally {
            registry.close();
        }
    }

    static Stream<Arguments> productChanges() {
        return Stream.of(PersonalizationRule.class, PersonalizationField.class)
                .flatMap(entity -> Stream.of(PostPersist.class, PostUpdate.class, PostRemove.class)
                        .map(event -> Arguments.of(entity, event)));
    }

    @ParameterizedTest(name = "{0}: {1} invalidates only after commit")
    @MethodSource("productChanges")
    void productAndFieldChangesInvalidateCachedResponsesAfterCommit(
            Class<?> entityType, Class<? extends Annotation> event) throws Exception {
        checkLifecycle(entityType, event, true);
    }

    @ParameterizedTest(name = "{0}: {1} preserves cache on rollback")
    @MethodSource("productChanges")
    void rolledBackProductAndFieldChangesPreserveCachedResponses(
            Class<?> entityType, Class<? extends Annotation> event) throws Exception {
        checkLifecycle(entityType, event, false);
    }

    private void checkLifecycle(Class<?> entityType, Class<? extends Annotation> event, boolean commit)
            throws Exception {
        var registry = new SimpleMeterRegistry();
        try {
            var cache = new StorefrontCache(registry, Duration.ofMinutes(1), 100);
            var beans = new StaticListableBeanFactory();
            beans.addBean("cache", cache);
            var listener = new StorefrontCacheEntityListener(beans.getBeanProvider(StorefrontCache.class),
                    beans.getBeanProvider(PlanCatalogCache.class));
            Object original = new Object();
            assertThat(cache.get("personalization", 1L, 10L, () -> original)).isSameAs(original);
            // Exercise the callback registered on the entity, rather than invoking cache invalidation directly.
            assertThat(entityType.getAnnotation(EntityListeners.class).value())
                    .contains(StorefrontCacheEntityListener.class);
            var callback = Arrays.stream(StorefrontCacheEntityListener.class.getMethods())
                    .filter(method -> method.isAnnotationPresent(event)).findFirst().orElseThrow();
            TransactionSynchronizationManager.initSynchronization();
            try {
                var rule = new PersonalizationRule(); rule.setStoreId(1L);
                var field = new PersonalizationField(); field.setRule(rule);
                callback.invoke(listener, entityType == PersonalizationRule.class ? rule : field);
                assertThat(cache.get("personalization", 1L, 10L, Object::new)).isSameAs(original);
                var synchronizations = TransactionSynchronizationManager.getSynchronizations();
                assertThat(synchronizations).isNotEmpty();
                if (commit) synchronizations.forEach(TransactionSynchronization::afterCommit);
                synchronizations.forEach(s -> s.afterCompletion(commit
                        ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
                var next = cache.get("personalization", 1L, 10L, Object::new);
                if (commit) assertThat(next).isNotSameAs(original);
                else assertThat(next).isSameAs(original);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
        } finally {
            registry.close();
        }
    }
}
