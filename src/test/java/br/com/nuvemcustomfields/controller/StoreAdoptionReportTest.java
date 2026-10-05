package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.StoreAdoptionReportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreAdoptionReportTest {
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired StoreOrderSalesRepository sales;
    @Autowired StoreSalesSyncRepository syncs;
    @Autowired WinbackCampaignRepository campaigns;
    @Autowired IntegrationLogRepository logs;
    @Autowired StoreAdoptionReportService reports;
    @Autowired MockMvc mvc;
    MockHttpSession session;

    @BeforeEach void setup(){
        session=new MockHttpSession();session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
    }
    Store store(long id,String name,String installed,String uninstalled){
        var s=new Store();s.setStoreId(id);s.setStoreName("adoção-test "+name);s.setStoreCurrency("BRL");
        ReflectionTestUtils.setField(s,"installedAt",Instant.parse(installed));
        if(uninstalled!=null)s.setUninstalledAt(Instant.parse(uninstalled));
        return stores.saveAndFlush(s);
    }
    void field(Store s,long product,boolean enabled){
        var r=new PersonalizationRule();r.setStoreId(s.getStoreId());r.setProductId(product);r.setEnabled(enabled);rules.saveAndFlush(r);
        var f=new PersonalizationField();f.setRule(r);f.setLabel("Nome");fields.saveAndFlush(f);
    }
    PaymentSubscription subscription(Store store,PaymentEnvironment environment){
        var sub=new PaymentSubscription();sub.setStoreId(store.getStoreId());sub.setProvider(PaymentProviderType.EFI);
        sub.setProviderEnvironment(environment);sub.setPlan(PlanType.PREMIUM);sub.setCurrency("BRL");
        sub.setAmountValue(new BigDecimal("19.99"));sub.setExternalReference("adoption-test-"+store.getStoreId());
        sub.setStatus(PaymentSubscriptionStatus.ACTIVE);sub.setAccessActive(true);return subscriptions.saveAndFlush(sub);
    }
    @Test void countsDepartedStoresAndSeparatesIncompleteHistoryAndCurrentPaidParticipation() throws Exception {
        var active=store(997665001L,"ativa","2026-09-01T12:00:00Z",null);field(active,1,true);field(active,2,true);
        subscription(active,PaymentEnvironment.PRODUCTION);
        var departed=store(997665002L,"desinstalada","2026-09-10T12:00:00Z","2026-09-13T12:00:00Z");field(departed,3,false);
        departed.setDepartureReason("Difícil de configurar e/ou utilizar");departed.setDepartureJustification("Não encontrei a opção <script>alert(1)</script>");stores.saveAndFlush(departed);
        var sold=store(997665003L,"vendeu","2026-09-20T12:00:00Z","2026-10-01T12:00:00Z");
        sales.saveAndFlush(new StoreOrderSales(sold.getStoreId(),1L,2,1));
        var report=reports.report(null,null,"adoção-test","");
        assertThat(report.total()).isEqualTo(3);assertThat(report.active()).isEqualTo(1);
        assertThat(report.uninstalled()).isEqualTo(2);assertThat(report.paidActive()).isEqualTo(1);
        assertThat(report.paidActiveRate()).isEqualByComparingTo("100.0");assertThat(report.paidBaseRate()).isEqualByComparingTo("33.3");
        assertThat(report.configuredActive()).isEqualTo(1);
        var row=report.rows().stream().filter(r->r.store().getStoreId().equals(departed.getStoreId())).findFirst().orElseThrow();
        assertThat(row.daysToDeparture()).isEqualTo(3);assertThat(row.enabledProducts()).isZero();
        assertThat(row.getSalesLabel()).isEqualTo("Histórico de vendas incompleto");
        assertThat(row.reasonSource()).isEqualTo("Nuvemshop · registro manual");
        assertThat(report.stages().stream().filter(s->s.label().equals("Venda personalizada registrada")).findFirst().orElseThrow().departed()).isEqualTo(1);
        String page=mvc.perform(get("/backoffice/reports/adoption").session(session).param("q","adoção-test"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("33,3%","Histórico de vendas incompleto","Difícil de configurar", "&lt;script&gt;")
                .doesNotContain("<script>alert(1)</script>");
    }
    @Test void excludesCourtesySandboxAndCancelledSubscriptionsFromPaidTotals(){
        var courtesy=store(997665004L,"cortesia","2026-09-01T12:00:00Z",null);courtesy.setCourtesyPremium(true);stores.saveAndFlush(courtesy);subscription(courtesy,PaymentEnvironment.PRODUCTION);
        var sandbox=store(997665005L,"sandbox","2026-09-01T12:00:00Z",null);subscription(sandbox,PaymentEnvironment.SANDBOX);
        var cancelling=store(997665006L,"cancelando","2026-09-01T12:00:00Z",null);var sub=subscription(cancelling,PaymentEnvironment.PRODUCTION);sub.setCancellationPending(true);subscriptions.saveAndFlush(sub);
        assertThat(reports.report(null,null,"adoção-test","").paidActive()).isZero();
    }
    @Test void statusFiltersOnlyDetailsAndDateFiltersUseSaoPauloBoundaries(){
        var active=store(997665007L,"ativa","2026-10-01T02:59:59Z",null);
        var departed=store(997665008L,"saída","2026-10-01T03:00:00Z","2026-10-02T03:00:00Z");
        var report=reports.report(null,null,"adoção-test","uninstalled");
        assertThat(report.total()).isEqualTo(2);assertThat(report.active()).isEqualTo(1);assertThat(report.rows()).hasSize(1);
        assertThat(report.rows().getFirst().store().getStoreId()).isEqualTo(departed.getStoreId());
        assertThat(reports.report(LocalDate.parse("2026-09-30"),LocalDate.parse("2026-09-30"),"adoção-test","").rows())
                .extracting(r->r.store().getStoreId()).containsExactly(active.getStoreId());
    }
    @Test void onlyUsesFeedbackFromTheCurrentDepartureAndHonorsManualReason(){
        var departed=store(997665009L,"motivos","2026-09-01T12:00:00Z","2026-09-13T12:00:00Z");
        var stale=new WinbackCampaign(departed.getStoreId(),Instant.parse("2026-09-10T12:00:00Z"));stale.respond("PRICE","Anterior");campaigns.saveAndFlush(stale);
        assertThat(reports.report(null,null,"adoção-test","").rows().getFirst().reason()).isEqualTo("Ainda não informado");
        var current=new WinbackCampaign(departed.getStoreId(),departed.getUninstalledAt());current.respond("CONFIGURATION","Não consegui configurar");campaigns.saveAndFlush(current);
        assertThat(reports.report(null,null,"adoção-test","").rows().getFirst().reason()).isEqualTo("Dificuldade de configuração");
        departed.setDepartureReason("Outros");stores.saveAndFlush(departed);
        assertThat(reports.report(null,null,"adoção-test","").reasons().getFirst().source()).isEqualTo("Nuvemshop · registro manual");
    }
    @Test void completedSalesSyncDistinguishesNoSaleFromUnknownAndEmptyRatesAreNotZero(){
        var active=store(997665010L,"sem vendas","2026-09-01T12:00:00Z",null);
        var sync=new StoreSalesSync(active.getStoreId());sync.setComplete(true);syncs.saveAndFlush(sync);
        assertThat(reports.report(null,null,"adoção-test","").rows().getFirst().getSalesLabel()).contains("Nenhuma venda");
        assertThat(reports.report(null,null,"adoção-test-inexistente","").paidActiveRate()).isNull();
    }
    @Test void usesExistingRenderSignalsWithoutAssumingNoSignalMeansNoUse(){
        var rendered=store(997665011L,"exibição","2026-09-01T12:00:00Z","2026-09-13T12:00:00Z");
        var log=new IntegrationLog();log.setStoreId(rendered.getStoreId());log.setLevel("INFO");
        log.setEventType("storefront.sdk.patagonia_transition_rendered");log.setMessage("produto=1");logs.saveAndFlush(log);
        var report=reports.report(null,null,"adoção-test","");
        assertThat(report.rows().getFirst().stage()).isEqualTo("Exibição reportada pela vitrine");
        assertThat(report.rows().getFirst().renderReports()).isEqualTo(1);
        assertThat(report.rows().getFirst().getSalesLabel()).isEqualTo("Histórico de vendas incompleto");
    }
    @Test void rejectsInvalidRangesAndRequiresBackofficeLogin() throws Exception {
        mvc.perform(get("/backoffice/reports/adoption")).andExpect(redirectedUrl("/backoffice/login"));
        mvc.perform(get("/backoffice/reports/adoption").session(session).param("from","2026-10-02").param("to","2026-10-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/backoffice/reports/adoption").session(session).param("q","adoção-test-inexistente")).andExpect(status().isOk());
    }
}
