package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class StoreConfigurationHistoryTest {
    private static final AtomicLong IDS = new AtomicLong(993201000L);
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired StoreConfigurationSnapshotRepository snapshots;
    @Autowired WinbackCampaignRepository campaigns;
    @Autowired StoreConfigurationHistoryService history;
    @Autowired IntegrationLogService logs;
    @Autowired WebhookLifecycleService webhooks;
    @Autowired StoreErasureRequestService requests;
    @Autowired StoreDataErasureService erasure;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    long storeId;
    MockHttpSession session;

    @BeforeEach void setup() {
        storeId = IDS.incrementAndGet();
        var store = new Store(); store.setStoreId(storeId); store.setStoreName("Loja de diagnóstico");
        store.setStoreCountryCode("BR"); store.setAccessToken("secret-never-copy");
        store.setScope("read_products,write_scripts"); store.setPlan(PlanType.PREMIUM_PLUS); stores.saveAndFlush(store);
        var rule = new PersonalizationRule(); rule.setStoreId(storeId); rule.setProductId(7001L);
        rule.setProductName("Caderno personalizado"); rules.saveAndFlush(rule);
        var text = new PersonalizationField(); text.setRule(rule); text.setLabel("Nome na capa"); text.setRequired(true);
        text.setPlaceholder("Ex.: Ana Clara"); text.setMaxLength(30); text.setValidationPattern("^[A-Z ]+$"); fields.saveAndFlush(text);
        var select = new PersonalizationField(); select.setRule(rule); select.setLabel("Acabamento");
        select.setFieldType(FieldType.SELECT); select.setOptionsText("Dourado\nBranco"); select.setSortOrder(1); fields.saveAndFlush(select);
        var image = new PersonalizationField(); image.setRule(rule); image.setLabel("Capa"); image.setFieldType(FieldType.IMAGE_SELECT);
        image.setImageOptionsJson("[{\"id\":\"" + UUID.randomUUID() + "\",\"label\":\"Floral\"}]");
        image.setSortOrder(2); fields.saveAndFlush(image);
        var empty = new PersonalizationRule(); empty.setStoreId(storeId); empty.setProductId(7002L);
        empty.setProductName("Produto sem campos"); empty.setEnabled(false); rules.saveAndFlush(empty);
        logs.warn(storeId, "script.install.failed", "Não foi possível instalar o script da vitrine.");
        session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        em.clear();
    }
    void uninstall() { webhooks.handle(new WebhookPayload(storeId, "app/uninstalled", null)); em.flush(); em.clear(); }

    @Test void capturesFullConfigurationBeforeResetAndShowsFeedbackForTheSameDeparture() throws Exception {
        uninstall(); uninstall();
        var entries = snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId); assertThat(entries).hasSize(1);
        var snapshot = entries.getFirst();
        assertThat(snapshot.getConfigurationJson()).doesNotContain("secret-never-copy");
        assertThat(stores.findByStoreId(storeId).orElseThrow().getPlan()).isEqualTo(PlanType.FREE);
        var inspection = history.inspect(storeId, null, null);
        assertThat(inspection.configuration().plan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(inspection.configuration().fieldCount()).isEqualTo(3);
        assertThat(inspection.configuration().scope()).isEqualTo("read_products,write_scripts");
        assertThat(inspection.configuration().logs().stream().map(StoreConfigurationHistoryService.Log::eventType)).contains("script.install.failed");
        var campaign = campaigns.findByStoreIdAndUninstalledAt(storeId, snapshot.getUninstalledAt())
                .orElseGet(() -> new WinbackCampaign(storeId, snapshot.getUninstalledAt()));
        campaign.respond("CONFIGURATION", "Os campos não apareceram no caderno."); campaigns.saveAndFlush(campaign);
        mvc.perform(get("/backoffice/stores/" + storeId + "/configuration").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Configuração no momento da desinstalação")))
                .andExpect(content().string(containsString("Nome na capa")))
                .andExpect(content().string(containsString("Ex.: Ana Clara")))
                .andExpect(content().string(containsString("^[A-Z ]+$")))
                .andExpect(content().string(containsString("7001")))
                .andExpect(content().string(containsString("Dourado")))
                .andExpect(content().string(containsString("Floral")))
                .andExpect(content().string(containsString("Os campos não apareceram no caderno.")))
                .andExpect(content().string(containsString("Produto selecionado, mas sem campos configurados.")));
    }
    @Test void laterEditsAndReinstallationDoNotOverwriteEarlierConfigurationOrManualReason() {
        uninstall();
        requests.recordReason(storeId, "Falha na vitrine", "Cliente não encontrou os campos"); em.flush(); em.clear();
        var first = snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId).getFirst();
        var store = stores.findByStoreId(storeId).orElseThrow(); store.setUninstalledAt(null); store.setDepartureCounted(false);
        store.setDepartureReason(null); store.setDepartureJustification(null); store.setAccessToken("new-token");
        store.setPlan(PlanType.PREMIUM_ULTRA); stores.saveAndFlush(store);
        var rule = rules.findWithFieldsByStoreIdAndProductId(storeId, 7001L).orElseThrow();
        rule.setProductName("Produto renomeado"); rule.getFields().getFirst().setLabel("Novo campo"); rules.saveAndFlush(rule);
        em.clear(); uninstall();
        assertThat(snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId)).hasSize(2);
        var old = history.inspect(storeId, first.getId(), null);
        assertThat(old.configuration().products().stream().filter(p -> p.productId().equals(7001L)).findFirst().orElseThrow().productName()).isEqualTo("Caderno personalizado");
        assertThat(old.configuration().products().stream().flatMap(p -> p.fields().stream()).map(StoreConfigurationHistoryService.Field::label)).contains("Nome na capa");
        assertThat(old.manualReason()).isEqualTo("Falha na vitrine");
        assertThat(history.inspect(storeId, null, first.getUninstalledAt()).selected().getId()).isEqualTo(first.getId());
        assertThat(history.inspect(storeId, null, null).configuration().plan()).isEqualTo(PlanType.PREMIUM_ULTRA);
    }
    @Test void olderUninstallUsesClearlyLabeledRetainedDataWithoutInventingHistory() throws Exception {
        var store = stores.findByStoreId(storeId).orElseThrow(); store.setUninstalledAt(Instant.parse("2026-09-01T12:00:00Z"));
        stores.saveAndFlush(store); em.clear(); uninstall();
        assertThat(snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId)).isEmpty();
        mvc.perform(get("/backoffice/stores/" + storeId + "/configuration").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("não tem uma cópia histórica")))
                .andExpect(content().string(containsString("Caderno personalizado")));
    }
    @Test void capturesRedactArrivalBeforeRevokingAccessAndErasesAllCopiesPermanently() throws Exception {
        requests.receive(storeId, Instant.now()); em.flush(); em.clear();
        assertThat(history.inspect(storeId, null, null).configuration().plan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId)).hasSize(1);
        erasure.erase(storeId); em.flush(); em.clear();
        assertThat(snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId)).isEmpty();
        mvc.perform(get("/backoffice/stores/" + storeId + "/configuration").session(session)).andExpect(status().isNotFound());
    }
    @Test void requiresBackofficeAccessAndDoesNotAllowReadingAnotherStoresSnapshot() throws Exception {
        uninstall();
        var snapshot = snapshots.findByStoreIdOrderByUninstalledAtDesc(storeId).getFirst();
        mvc.perform(get("/backoffice/stores/" + storeId + "/configuration")).andExpect(redirectedUrl("/backoffice/login"));
        var other = new Store(); other.setStoreId(storeId + 10000); stores.saveAndFlush(other);
        mvc.perform(get("/backoffice/stores/" + other.getStoreId() + "/configuration")
                .param("snapshotId", snapshot.getId().toString()).session(session)).andExpect(status().isNotFound());
    }
}
