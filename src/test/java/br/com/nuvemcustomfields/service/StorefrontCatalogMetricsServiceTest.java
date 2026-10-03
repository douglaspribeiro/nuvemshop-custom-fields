package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.StorefrontTrafficMetricsFilter;
import br.com.nuvemcustomfields.dto.PersonalizedProductMetadata;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StorefrontCatalogMetricsServiceTest {
    @Test
    void namesRefreshWithoutResettingCountersAndObsoleteMetadataIsRemoved() {
        var registry = new SimpleMeterRegistry();
        var stores = mock(StoreRepository.class);
        var rules = mock(PersonalizationRuleRepository.class);
        var store = new Store();
        store.setStoreId(123L);
        store.setStoreName("Minha loja");
        when(stores.findAll()).thenReturn(List.of(store));
        when(rules.findActiveProductMetadata()).thenReturn(List.of(
                new PersonalizedProductMetadata(123L, "Minha loja", 456L, "Camiseta")));
        var service = new StorefrontCatalogMetricsService(registry, stores, rules);
        service.refresh();
        assertThat(registry.get("ncf.store.info").tag("store_name", "Minha loja").gauge().value()).isEqualTo(1);
        var counter = registry.get(StorefrontTrafficMetricsFilter.PRODUCT_REQUEST_METRIC).counter();
        assertThat(counter.count()).isZero();
        counter.increment();
        store.setStoreName("Novo nome");
        when(rules.findActiveProductMetadata()).thenReturn(List.of(
                new PersonalizedProductMetadata(123L, "Novo nome", 456L, "Camiseta nova")));
        service.refresh();
        assertThat(registry.find("ncf.store.info").tag("store_name", "Minha loja").gauge()).isNull();
        assertThat(registry.get("ncf.personalized.product.info").tag("product_name", "Camiseta nova").gauge().value()).isEqualTo(1);
        assertThat(counter.count()).isEqualTo(1);
        when(rules.findActiveProductMetadata()).thenReturn(List.of());
        when(stores.findAll()).thenReturn(List.of());
        service.refresh();
        assertThat(registry.find("ncf.store.info").gauge()).isNull();
        assertThat(registry.find("ncf.personalized.product.info").gauge()).isNull();
    }

    @Test
    void fallbackNamesAndDatabaseFailuresPreserveLastCatalog() {
        var registry = new SimpleMeterRegistry();
        var stores = mock(StoreRepository.class);
        var rules = mock(PersonalizationRuleRepository.class);
        var store = new Store();
        store.setStoreId(123L);
        when(stores.findAll()).thenReturn(List.of(store));
        when(rules.findActiveProductMetadata()).thenReturn(List.of(
                new PersonalizedProductMetadata(123L, null, 456L, " ")));
        var service = new StorefrontCatalogMetricsService(registry, stores, rules);
        service.refresh();
        assertThat(registry.get("ncf.personalized.product.info").tag("product_name", "Produto 456").gauge().value()).isEqualTo(1);
        when(stores.findAll()).thenReturn(List.of());
        when(rules.findActiveProductMetadata()).thenThrow(new DataAccessResourceFailureException("unavailable"));
        service.refresh();
        assertThat(registry.get("ncf.store.info").tag("store_name", "Loja 123").gauge().value()).isEqualTo(1);
    }
}
