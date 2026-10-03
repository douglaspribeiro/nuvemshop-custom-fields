package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.StorefrontTrafficMetricsFilter;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.StorefrontCatalogMetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalizedProductMetricsTest {
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired StorefrontCatalogMetricsService catalog;
    @Autowired MeterRegistry registry;
    @Autowired MockMvc mvc;

    @Test
    void onlyConfiguredActiveProductsCountAndCatalogNamesComeFromDatabase() throws Exception {
        var store = new Store();
        store.setStoreId(9988776601L);
        store.setStoreName("Loja de teste");
        stores.saveAndFlush(store);
        var rule = new PersonalizationRule();
        rule.setStoreId(store.getStoreId());
        rule.setProductId(123456L);
        rule.setProductName("Produto personalizado");
        var field = new PersonalizationField();
        field.setRule(rule);
        field.setLabel("Nome");
        rule.getFields().add(field);
        rules.saveAndFlush(rule);
        catalog.refresh();
        assertThat(registry.get("ncf.personalized.product.info")
                .tags("store_id", store.getStoreId().toString(), "product_name", "Produto personalizado")
                .gauge().value()).isEqualTo(1);
        var counter = registry.get(StorefrontTrafficMetricsFilter.PRODUCT_REQUEST_METRIC)
                .tags("store_id", store.getStoreId().toString(), "product_id", "123456").counter();
        double before = counter.count();
        read(store.getStoreId(), 123456L);
        assertThat(counter.count()).isEqualTo(before + 1);
        read(store.getStoreId(), 999999L);
        assertThat(registry.find(StorefrontTrafficMetricsFilter.PRODUCT_REQUEST_METRIC)
                .tags("store_id", store.getStoreId().toString(), "product_id", "999999").counter()).isNull();
        rule.setEnabled(false);
        rules.saveAndFlush(rule);
        read(store.getStoreId(), 123456L);
        assertThat(counter.count()).isEqualTo(before + 1);
        catalog.refresh();
        assertThat(registry.find("ncf.personalized.product.info")
                .tag("store_id", store.getStoreId().toString()).gauge()).isNull();
        rule.setEnabled(true);
        rules.saveAndFlush(rule);
        store.setErasureRequestedAt(Instant.now());
        stores.saveAndFlush(store);
        read(store.getStoreId(), 123456L);
        assertThat(counter.count()).isEqualTo(before + 1);
        catalog.refresh();
        assertThat(registry.find("ncf.personalized.product.info")
                .tag("store_id", store.getStoreId().toString()).gauge()).isNull();
    }

    private void read(Long storeId, Long productId) throws Exception {
        mvc.perform(get("/public/stores/{storeId}/personalization", storeId)
                .param("productId", productId.toString())).andExpect(status().isOk());
    }
}
