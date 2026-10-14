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
                callback.invoke(listener, entityType.getConstructor().newInstance());
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
