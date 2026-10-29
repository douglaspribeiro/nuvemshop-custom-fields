package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class PersonalizationHistoryIntegrationTest {
    @Autowired jakarta.persistence.EntityManager entityManager;
    void nextRequest() { entityManager.flush(); entityManager.clear(); }
    @Autowired PersonalizationAdminService admin;
    @Autowired StoreTemplateService templates;
    @Autowired OrderPersonalizationCatalog catalog;
    @Autowired ReportService report;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationFieldHistoryRepository history;
    @Autowired StoreConfigurationSnapshotRepository snapshots;
    @Autowired StoreDataErasureService erasure;
    @Autowired WebhookLifecycleService webhooks;
    @MockitoBean NuvemshopApiClient api;
    final ObjectMapper json=new ObjectMapper();
    Store store() {
        var store=new Store(); store.setStoreId(997770001L); store.setAccessToken("fixture-token");
        store.setScope("read_orders"); store.setPlan(PlanType.PREMIUM);
        return stores.saveAndFlush(store);
    }
    FieldForm form(String label) {
        var form=new FieldForm();form.setLabel(label);form.setFieldType(FieldType.TEXT);form.setMaxLength(100);
        return form;
    }
    @Test void renamedAndDeletedFieldsKeepTheirSubmittedTextAndSales() throws Exception {
        var store=store();
        admin.ensureRule(store.getStoreId(),100L,"Caneta"); nextRequest();
        admin.addField(store.getStoreId(),100L,form("Nome [gravar]")); nextRequest();
        Long field=admin.requireRuleWithFields(store.getStoreId(),100L).getFields().getFirst().getId();
        admin.updateField(store.getStoreId(),100L,field,form("Mensagem")); nextRequest();
        var orders=json.readTree("""
                [{"id":123,"number":55,"payment_status":"paid","products":[
                   {"product_id":100,"quantity":1,"price":"10.00","properties":{"Nome (gravar)":"Ana","gift":"25533"}},
                   {"product_id":101,"quantity":1,"properties":{"Mensagem":"Outro app"}}
                ]}]
                """);
        when(api.listRecentOrders(store)).thenReturn(orders);
        assertThat(report.dashboard(store).personalizedOrders().getFirst().properties()).containsExactly("Nome (gravar): Ana");
        admin.deleteField(store.getStoreId(),100L,field); nextRequest();
        assertThat(report.dashboard(store).personalizedOrders().getFirst().properties()).containsExactly("Nome (gravar): Ana");
        admin.deleteRule(store.getStoreId(),100L); nextRequest();
        var sale=BackofficeSalesService.summarizeOrder(store.getStoreId(),orders.get(0),catalog.forStore(store.getStoreId()));
        assertThat(sale.getPersonalizedItems()).isEqualTo(1);
        assertThat(sale.getPersonalizedProductValue()).isEqualByComparingTo("10.00");
        assertThat(history.findByStoreId(store.getStoreId())).extracting(PersonalizationFieldHistory::getPropertyName)
                .containsExactlyInAnyOrder("Nome (gravar)","Mensagem");
    }
    @Test void existingSnapshotsRestoreNamesAndRedactionErasesHistory() throws Exception {
        var store=store();
        snapshots.saveAndFlush(new StoreConfigurationSnapshot(store.getStoreId(),Instant.parse("2026-01-01T00:00:00Z"),
                "{\"products\":[{\"productId\":100,\"fields\":[{\"label\":\"Nome antigo\"}]}]}"));
        var product=json.readTree("{\"product_id\":100,\"properties\":{\"Nome antigo\":\"Ana\"}}");
        assertThat(catalog.forStore(store.getStoreId()).productProperties(product)).containsExactly("Nome antigo: Ana");
        history.saveAndFlush(new PersonalizationFieldHistory(store.getStoreId(),100L,"Nome antigo"));
        erasure.erase(store.getStoreId());
        assertThat(history.findByStoreId(store.getStoreId())).isEmpty();
        assertThat(snapshots.findByStoreIdOrderByUninstalledAtDesc(store.getStoreId())).isEmpty();
    }
    @Test void templateApplicationAndReplacementKeepPreviousOrderPersonalizationReadable() throws Exception {
        var owner=store();
        var template=templates.duplicate(owner.getStoreId(),"catalog:caneca","Minha caneca");
        var preview=templates.preview(owner.getStoreId(),template.key(),100);
        templates.apply(owner.getStoreId(),template.key(),100,"Caneca",StoreTemplateService.Mode.APPEND,false,
                java.util.UUID.randomUUID().toString(),preview.reviewToken());nextRequest();
        var orders=json.readTree("[{\"id\":123,\"number\":55,\"payment_status\":\"paid\",\"products\":[{\"product_id\":100,\"quantity\":1,\"price\":\"10.00\",\"properties\":{\"Nome para gravar\":\"Ana\"}}]}]");
        when(api.listRecentOrders(owner)).thenReturn(orders);
        assertThat(report.dashboard(owner).personalizedOrders().getFirst().properties()).containsExactly("Nome para gravar: Ana");
        templates.delete(owner.getStoreId(),template.id(),template.revision(),true);
        var replacement=templates.create(owner.getStoreId(),"Novo modelo",null,java.util.List.of(br.com.nuvemcustomfields.dto.TemplateFieldDefinition.from(form("Mensagem"))));
        var reviewed=templates.preview(owner.getStoreId(),replacement.key(),100);
        templates.apply(owner.getStoreId(),replacement.key(),100,"Caneca",StoreTemplateService.Mode.REPLACE,true,
                java.util.UUID.randomUUID().toString(),reviewed.reviewToken());nextRequest();
        assertThat(report.dashboard(owner).personalizedOrders().getFirst().properties()).containsExactly("Nome para gravar: Ana");
    }
    @Test void productDeletionWebhookPreservesNamesWithoutKeepingTheRule() throws Exception {
        var store=store();
        admin.ensureRule(store.getStoreId(),100L,"Caneta");nextRequest();
        admin.addField(store.getStoreId(),100L,form("Mensagem"));nextRequest();
        webhooks.handle(new br.com.nuvemcustomfields.dto.WebhookPayload(store.getStoreId(),"product/deleted",100L));nextRequest();
        assertThat(admin.hasRule(store.getStoreId(),100L)).isFalse();
        assertThat(catalog.forStore(store.getStoreId()).productProperties(json.readTree(
                "{\"product_id\":100,\"properties\":{\"Mensagem\":\"Ana\"}}")))
                .containsExactly("Mensagem: Ana");
    }

    @Test void damagedHistoryIsUnavailableInsteadOfNoOrders() throws Exception {
        var store=store();
        snapshots.saveAndFlush(new StoreConfigurationSnapshot(store.getStoreId(),Instant.parse("2026-01-01T00:00:00Z"),"invalid-json"));
        when(api.listRecentOrders(store)).thenReturn(json.readTree("[]"));
        assertThat(report.dashboard(store).ordersAvailable()).isFalse();
    }
}
