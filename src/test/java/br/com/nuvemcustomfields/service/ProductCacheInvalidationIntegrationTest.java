package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.controller.PublicPersonalizationController;
import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.dto.PersonalizationResponse;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductCacheInvalidationIntegrationTest {
    private static final Long STORE = 998879L;
    private static final Long PRODUCT = 50L;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationAdminService admin;
    @Autowired PublicPersonalizationController controller;
    @Autowired StorefrontCache cache;
    @Autowired PlatformTransactionManager transactions;
    private Long fieldId;

    enum Change { CREATE_PRODUCT, RENAME_PRODUCT, DISABLE_PRODUCT, ENABLE_PRODUCT,
        DELETE_PRODUCT, CREATE_FIELD, UPDATE_FIELD, DELETE_FIELD }

    @BeforeEach void createStore() {
        var store = new Store(); store.setStoreId(STORE); store.setStoreName("Cache invalidation test");
        store.setPlan(PlanType.FREE_GRATIS);
        stores.saveAndFlush(store);
    }

    @AfterEach void cleanUp() {
        if (admin.hasRule(STORE, PRODUCT)) admin.deleteRule(STORE, PRODUCT);
        stores.findByStoreId(STORE).ifPresent(stores::delete);
    }

    @ParameterizedTest(name = "{0}: refreshes cached response immediately after commit")
    @EnumSource(Change.class)
    void committedProductChangesInvalidateWarmCache(Change change) {
        prepare(change);
        var original = read();
        assertThat(read()).isSameAs(original);
        apply(change);
        var updated = read();
        assertThat(updated).isNotSameAs(original);
        assertThat(read()).isSameAs(updated);
        switch (change) {
            case CREATE_PRODUCT -> assertThat(updated.enabled()).isFalse();
            case RENAME_PRODUCT -> assertThat(rules.findByStoreIdAndProductId(STORE, PRODUCT).orElseThrow()
                    .getProductName()).isEqualTo("Renamed product");
            case DISABLE_PRODUCT, DELETE_PRODUCT -> assertThat(updated.enabled()).isFalse();
            case ENABLE_PRODUCT -> assertThat(updated.enabled()).isTrue();
            case CREATE_FIELD -> assertThat(updated.fields()).extracting(f -> f.label())
                    .containsExactly("Original field", "New field");
            case UPDATE_FIELD -> assertThat(updated.fields()).extracting(f -> f.label())
                    .containsExactly("Updated field");
            case DELETE_FIELD -> assertThat(updated.fields()).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}: rollback preserves warm cache")
    @EnumSource(Change.class)
    void rolledBackProductChangesPreserveWarmCache(Change change) {
        prepare(change);
        var original = read();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            apply(change);
            // Flush to exercise actual JPA callbacks even though the transaction will roll back.
            rules.flush();
            status.setRollbackOnly();
        });
        assertThat(read()).isSameAs(original);
    }

    @org.junit.jupiter.api.Test
    void firstFieldReactivatesStoreAndLastFieldDisablesIt() {
        admin.ensureRule(STORE, PRODUCT, "Empty product");
        assertThat(read().enabled()).isFalse();
        var other = cache.get("personalization", STORE + 1, PRODUCT, () -> "warm");
        admin.addField(STORE, PRODUCT, form("First field"));
        assertThat(read().fields()).hasSize(1);
        assertThat(cache.get("personalization", STORE + 1, PRODUCT, () -> "unexpected")).isSameAs(other);
        long id = admin.requireRuleWithFields(STORE, PRODUCT).getFields().getFirst().getId();
        admin.deleteField(STORE, PRODUCT, id);
        assertThat(read().enabled()).isFalse();
    }

    private void prepare(Change change) {
        if (change == Change.CREATE_PRODUCT) return;
        admin.ensureRule(STORE, PRODUCT, "Original product");
        admin.addField(STORE, PRODUCT, form("Original field"));
        fieldId = admin.requireRuleWithFields(STORE, PRODUCT).getFields().getFirst().getId();
        if (change == Change.ENABLE_PRODUCT) setEnabled(false);
    }

    private void apply(Change change) {
        switch (change) {
            case CREATE_PRODUCT -> admin.ensureRule(STORE, PRODUCT, "New product");
            case RENAME_PRODUCT -> admin.ensureRule(STORE, PRODUCT, "Renamed product");
            case DISABLE_PRODUCT -> setEnabled(false);
            case ENABLE_PRODUCT -> setEnabled(true);
            case DELETE_PRODUCT -> admin.deleteRule(STORE, PRODUCT);
            case CREATE_FIELD -> admin.addField(STORE, PRODUCT, form("New field"));
            case UPDATE_FIELD -> admin.updateField(STORE, PRODUCT, fieldId, form("Updated field"));
            // This path uses a bulk delete and must invalidate without JPA entity callbacks.
            case DELETE_FIELD -> admin.deleteField(STORE, PRODUCT, fieldId);
        }
    }

    private void setEnabled(boolean enabled) {
        var rule = rules.findByStoreIdAndProductId(STORE, PRODUCT).orElseThrow();
        rule.setEnabled(enabled); rules.saveAndFlush(rule);
    }

    private PersonalizationResponse read() {
        return controller.getFields(STORE, PRODUCT, "/product", new MockHttpServletRequest());
    }

    private FieldForm form(String label) {
        var form = new FieldForm(); form.setLabel(label); return form;
    }
}
