package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.service.PlanPerformanceReportService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanPerformanceReportTest {
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired StoreOrderSalesRepository sales;
    @Autowired StoreSalesSyncRepository syncs;
    @Autowired PlanPerformanceReportService reports;
    @Autowired MockMvc mvc;
    MockHttpSession session;
    final LocalDate day = LocalDate.of(2026, 10, 10);

    @BeforeEach void setup() {
        session = new MockHttpSession();
        session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
    }
    Store store(long id, PlanType plan) {
        var s = new Store(); s.setStoreId(id); s.setStoreName("Relatório " + id); s.setPlan(plan);
        return stores.saveAndFlush(s);
    }
    void field(Store s, long product, boolean enabled) {
        var r = new PersonalizationRule(); r.setStoreId(s.getStoreId()); r.setProductId(product); r.setEnabled(enabled);
        rules.saveAndFlush(r);
        var f = new PersonalizationField(); f.setRule(r); f.setLabel("Nome"); fields.saveAndFlush(f);
    }
    void order(Store s, long id, String instant, long items) {
        sales.saveAndFlush(new StoreOrderSales(s.getStoreId(), id, 3, items,
                instant == null ? null : Instant.parse(instant), null));
    }

    @Test void countsDistinctEnabledProductsAndOrdersByCurrentPlanWithinLocalDay() {
        var free = store(997669001L, PlanType.FREE); field(free, 1, true);
        // Multiple fields on one product still represent one enabled product.
        var another = new PersonalizationField(); another.setRule(rules.findByStoreIdOrderByProductNameAsc(free.getStoreId()).getFirst());
        another.setLabel("Texto"); fields.saveAndFlush(another);
        var gratis = store(997669002L, PlanType.FREE_GRATIS); field(gratis, 2, false);
        var paid = store(997669003L, PlanType.PREMIUM); field(paid, 3, true);
        var departed = store(997669004L, PlanType.FREE); departed.setUninstalledAt(Instant.now()); stores.saveAndFlush(departed);
        var erasure = store(997669005L, PlanType.FREE); erasure.setErasureRequestedAt(Instant.now()); stores.saveAndFlush(erasure);
        order(free, 1, "2026-10-10T03:00:00Z", 2);
        order(free, 2, "2026-10-11T02:59:59Z", 1);
        order(free, 3, "2026-10-10T02:59:59Z", 1);
        order(free, 4, "2026-10-11T03:00:00Z", 1);
        order(free, 5, null, 1);
        order(free, 6, "2026-10-10T12:00:00Z", 0);
        order(gratis, 1, "2026-10-10T12:00:00Z", 1);
        order(paid, 1, "2026-10-10T12:00:00Z", 1);
        order(departed, 1, "2026-10-10T12:00:00Z", 1);
        order(erasure, 1, "2026-10-10T12:00:00Z", 1);
        var sync = new StoreSalesSync(gratis.getStoreId()); sync.setComplete(true); syncs.saveAndFlush(sync);

        var report = reports.report(day, day);
        assertThat(report.freeStores()).isEqualTo(2);
        assertThat(report.freeConfigured()).isEqualTo(1);
        assertThat(report.freeNotConfigured()).isEqualTo(1);
        assertThat(report.freeSelling()).isEqualTo(2);
        assertThat(report.freeActivationRate()).isEqualByComparingTo("50.0");
        assertThat(report.freeSellingRate()).isEqualByComparingTo("100.0");
        var row = report.plans().stream().filter(p -> p.plan() == PlanType.FREE).findFirst().orElseThrow();
        assertThat(row.orders()).isEqualTo(2); assertThat(row.items()).isEqualTo(3);
        assertThat(row.synced()).isZero();
        assertThat(report.freeDetails()).extracting(r -> r.id()).containsExactly(free.getStoreId(), gratis.getStoreId());
        assertThat(report.freeDetails().getFirst().products()).isEqualTo(1);
        assertThat(report.freeDetails().getFirst().salesStatus()).contains("histórico incompleto");
    }

    @Test void rendersReportAndExportsSameRangeWithEscapedNames() throws Exception {
        var free = store(997669006L, PlanType.FREE_GRATIS);
        free.setStoreName("=HYPERLINK(\"x\");<script>alert(1)</script>"); stores.saveAndFlush(free);
        field(free, 1, true); order(free, 1, "2026-10-10T12:00:00Z", 1);
        var page = mvc.perform(get("/backoffice/reports").session(session).param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Pedidos personalizados × plano atual", "Exportar CSV", "&lt;script&gt;")
                .doesNotContain("<script>alert(1)</script>");
        var csv = mvc.perform(get("/backoffice/reports/free.csv").session(session).param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=lojas-gratuitas.csv"))
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\");<script>alert(1)</script>\"", ";2026-10-10;2026-10-10");
    }

    @Test void distinguishesUnknownHistoryAndNoRecordedOrders() {
        var unknown = store(997669007L, PlanType.FREE);
        var complete = store(997669008L, PlanType.FREE_GRATIS);
        var sync = new StoreSalesSync(complete.getStoreId()); sync.setComplete(true); syncs.saveAndFlush(sync);
        var report = reports.report(day, day);
        assertThat(report.freeDetails().stream().filter(r -> r.id().equals(unknown.getStoreId())).findFirst().orElseThrow().salesStatus())
                .isEqualTo("Histórico incompleto");
        assertThat(report.freeDetails().stream().filter(r -> r.id().equals(complete.getStoreId())).findFirst().orElseThrow().salesStatus())
                .isEqualTo("Sem pedidos no período");
        assertThat(report.plans().stream().filter(p -> p.plan() == PlanType.PREMIUM_ULTRA).findFirst().orElseThrow().sellingRate()).isNull();
    }

    @Test void validatesDatesAndProtectsPageAndExport() throws Exception {
        for (String path : new String[]{"/backoffice/reports", "/backoffice/reports/free.csv"}) {
            mvc.perform(get(path)).andExpect(redirectedUrl("/backoffice/login"));
            mvc.perform(get(path).session(session).param("from", "2026-10-11").param("to", "2026-10-10"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/backoffice/reports").session(session)).andExpect(status().isOk());
    }
}
