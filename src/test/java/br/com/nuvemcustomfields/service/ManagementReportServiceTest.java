package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanAsset;
import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementReportServiceTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PersonalizationRuleRepository ruleRepository = mock(PersonalizationRuleRepository.class);
    private final PersonalizationFieldRepository fieldRepository = mock(PersonalizationFieldRepository.class);
    private final PlanEventRepository planEventRepository = mock(PlanEventRepository.class);
    private final PlanCatalogService planCatalogService = mock(PlanCatalogService.class);
    private final ManagementReportService service = new ManagementReportService(
            storeRepository,
            ruleRepository,
            fieldRepository,
            planEventRepository,
            planCatalogService
    );

    @Test
    void excludesCourtesyPremiumStoresFromEstimatedMrr() {
        Store billablePremium = store(PlanType.PREMIUM, false);
        Store courtesyPremium = store(PlanType.PREMIUM, true);
        Store billablePremiumPlus = store(PlanType.PREMIUM_PLUS, false);
        when(storeRepository.findAll()).thenReturn(List.of(billablePremium, courtesyPremium, billablePremiumPlus));
        when(ruleRepository.findAll()).thenReturn(List.of());
        when(planCatalogService.activePlansByType()).thenReturn(Map.of(
                PlanType.FREE, plan("0.00"),
                PlanType.PREMIUM, plan("9.99"),
                PlanType.PREMIUM_PLUS, plan("19.99")
        ));

        var report = service.report();

        assertThat(report.premiumStores()).isEqualTo(2);
        assertThat(report.premiumPlusStores()).isEqualTo(1);
        assertThat(report.estimatedMrr()).isEqualByComparingTo(new BigDecimal("29.98"));
    }

    @Test
    void projectsPaymentsForCurrentMonthFromBillingSnapshots() {
        Store dueThisMonth = store(PlanType.PREMIUM, false);
        dueThisMonth.setBillingNextExecution(LocalDate.now().withDayOfMonth(15));
        dueThisMonth.setBillingAmountValue(new BigDecimal("11.90"));
        Store dueNextMonth = store(PlanType.PREMIUM_PLUS, false);
        dueNextMonth.setBillingNextExecution(LocalDate.now().plusMonths(1));
        Store suspended = store(PlanType.PREMIUM, false);
        suspended.setBillingSuspended(true);
        suspended.setBillingNextExecution(LocalDate.now());
        suspended.setBillingAmountValue(new BigDecimal("9.99"));
        when(storeRepository.findAll()).thenReturn(List.of(dueThisMonth, dueNextMonth, suspended));
        when(ruleRepository.findAll()).thenReturn(List.of());
        when(planCatalogService.activePlansByType()).thenReturn(Map.of(
                PlanType.FREE, plan("0.00"),
                PlanType.PREMIUM, plan("9.99"),
                PlanType.PREMIUM_PLUS, plan("19.99")
        ));

        var report = service.report();

        assertThat(report.projectedMonth()).isEqualTo(YearMonth.now());
        assertThat(report.projectedMonthPaymentCount()).isEqualTo(1);
        assertThat(report.projectedMonthPayments()).isEqualByComparingTo("11.90");
    }

    private Store store(PlanType plan, boolean courtesyPremium) {
        Store store = new Store();
        store.setPlan(plan);
        store.setCourtesyPremium(courtesyPremium);
        return store;
    }

    private PlanAsset plan(String amount) {
        PlanAsset plan = new PlanAsset();
        plan.setAmount(new BigDecimal(amount));
        return plan;
    }
}
