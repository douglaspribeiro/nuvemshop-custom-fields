package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.UserJourneyAuditService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserJourneyAuditTest {
    @Autowired MockMvc mvc;
    @Autowired UserJourneyAuditRepository audits;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired UserJourneyAuditService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired br.com.nuvemcustomfields.service.WebhookLifecycleService lifecycle;
    @Autowired br.com.nuvemcustomfields.service.StoreErasureRequestService erasureRequests;
    final long storeId = 998880051L;
    MockHttpSession session;
    @BeforeEach void setup() {
        var store = new Store(); store.setStoreId(storeId); store.setAccessToken("test-secret-must-not-be-audited");
        store.setPlan(PlanType.PREMIUM); stores.saveAndFlush(store);
        session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, storeId);
    }
    @AfterEach void cleanup() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audits.deleteAll(audits.findAll().stream().filter(a -> a.storeId.equals(storeId)).toList());
            fields.deleteAll(fields.findWithRuleByStoreId(storeId));
            rules.deleteAll(rules.findByStoreIdOrderByProductNameAsc(storeId));
            stores.findByStoreId(storeId).ifPresent(stores::delete);
        });
    }
    List<UserJourneyAudit> events() { return audits.findAll().stream().filter(a -> a.storeId.equals(storeId)).sorted(Comparator.comparing(a -> a.id)).toList(); }

    @Test void recordsNavigationAndRejectedActionsWithoutSensitivePayloads() throws Exception {
        mvc.perform(get("/admin/help").session(session).param("q", "private-search-value")).andExpect(status().isOk());
        mvc.perform(post("/admin/settings/style").session(session).param("productTextColor", "invalid-secret-color"))
                .andExpect(status().is3xxRedirection());
        var events = events(); assertThat(events).hasSize(2);
        assertThat(events.get(0).action).isEqualTo("AdminController.help");
        assertThat(events.get(0).outcome).isEqualTo("COMPLETED");
        assertThat(events.get(1).outcome).isEqualTo("REJECTED");
        assertThat(events).allSatisfy(e -> {
            assertThat(e.occurredAt).isNotNull(); assertThat(e.completedAt).isAfterOrEqualTo(e.occurredAt);
            assertThat(e.actor).isEqualTo("MERCHANT");
            assertThat(e.route + e.beforeState + e.afterState).doesNotContain("private-search-value", "test-secret", "invalid-secret-color");
        });
        assertThat(events.get(0).journeyId).isEqualTo(events.get(1).journeyId).isNotEqualTo(session.getId());
    }
    @Test void recordsConfigurationRemovalWithBeforeAndAfterStateAndOperatorAttribution() throws Exception {
        var rule = new PersonalizationRule(); rule.setStoreId(storeId); rule.setProductId(41L); rule.setEnabled(true); rule = rules.saveAndFlush(rule);
        var field = new PersonalizationField(); field.setRule(rule); field.setLabel("private-label"); fields.saveAndFlush(field);
        session.setAttribute(BackofficeSessionInterceptor.STORE_MODE_SESSION_KEY, true);
        mvc.perform(post("/admin/products/41/delete").session(session)).andExpect(status().is3xxRedirection());
        var event = events().getFirst();
        assertThat(event.actor).isEqualTo("OPERATOR");
        assertThat(event.route).isEqualTo("/admin/products/{productId}/delete");
        assertThat(event.productId).isEqualTo(41L);
        assertThat(event.beforeState).contains("\"exists\":true", "\"enabled\":true", "\"type\":\"TEXT\"").doesNotContain("private-label");
        assertThat(event.afterState).isEqualTo("{\"exists\":false}");
    }
    @Test void tracksCreateEditAndDeleteFieldInOrder() throws Exception {
        var rule = new PersonalizationRule(); rule.setStoreId(storeId); rule.setProductId(42L); rule.setEnabled(true); rule = rules.saveAndFlush(rule);
        mvc.perform(post("/admin/products/42/fields").session(session).param("label", "Nome").param("fieldType", "TEXT"))
                .andExpect(status().is3xxRedirection());
        var field = fields.findByRuleIdOrderBySortOrderAscIdAsc(rule.getId()).getFirst();
        mvc.perform(post("/admin/products/42/fields/" + field.getId()).session(session)
                .param("label", "Nome completo").param("fieldType", "TEXT").param("required", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/products/42/fields/" + field.getId() + "/delete").session(session))
                .andExpect(status().is3xxRedirection());
        var events = events(); assertThat(events).hasSize(3);
        assertThat(events).extracting(e -> e.action).containsExactly("AdminController.addField", "AdminController.updateField", "AdminController.deleteField");
        assertThat(events).allMatch(e -> e.outcome.equals("COMPLETED"));
        assertThat(events.get(0).beforeState).contains("\"fields\":[]");
        assertThat(events.get(0).afterState).contains("\"required\":false");
        assertThat(events.get(1).afterState).contains("\"required\":true");
        assertThat(events.get(2).afterState).contains("\"fields\":[]");
    }
    @Test void uninstallPreservesAuditUntilOperatorExplicitlyDeletesStore() throws Exception {
        mvc.perform(get("/admin/help").session(session)).andExpect(status().isOk());
        var store = stores.findByStoreId(storeId).orElseThrow();
        // Avoid the unrelated MySQL-only aggregate UPSERT in this H2 test.
        store.setDepartureCounted(true); stores.saveAndFlush(store);
        lifecycle.handle(new br.com.nuvemcustomfields.dto.WebhookPayload(storeId, "app/uninstalled", null));
        assertThat(events()).hasSize(1);
        assertThat(stores.findByStoreId(storeId).orElseThrow().isActive()).isFalse();
        erasureRequests.receive(storeId, Instant.now());
        assertThat(events()).hasSize(1); // Redact is a pending manual request, not deletion.
        erasureRequests.eraseManually(storeId);
        assertThat(events()).isEmpty();
        assertThat(stores.findByStoreId(storeId)).isEmpty();
        mvc.perform(get("/admin/help").session(session)).andExpect(status().is3xxRedirection());
        assertThat(events()).isEmpty(); // A stale browser session cannot recreate deleted audit rows.
    }
    @Test void attemptsSurviveBusinessRollbackAndOldRowsAreNotAutomaticallyDeleted() {
        var event = new UserJourneyAudit(); event.storeId=storeId; event.journeyId=UUID.randomUUID().toString();
        event.actor="MERCHANT"; event.action="test.rollback"; event.route="/admin"; event.method="POST";
        event.occurredAt=Instant.now().minusSeconds(100L*24*3600);
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> { service.start(event, true); status.setRollbackOnly(); });
        assertThat(audits.findById(event.id)).isPresent();
        assertThat(audits.findById(event.id).orElseThrow().outcome).isEqualTo("STARTED");
        service.complete(event.id, "ERROR", 500);
        assertThat(audits.findById(event.id).orElseThrow().outcome).isEqualTo("ERROR");
        assertThat(events()).hasSize(1);
    }
}
