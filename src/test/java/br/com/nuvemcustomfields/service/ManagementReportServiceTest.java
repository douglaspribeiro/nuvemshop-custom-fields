package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementReportServiceTest {

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final PersonalizationRuleRepository ruleRepository = mock(PersonalizationRuleRepository.class);
    private final PersonalizationFieldRepository fieldRepository = mock(PersonalizationFieldRepository.class);
    private final PlanEventRepository planEventRepository = mock(PlanEventRepository.class);
    private final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    private final PlanCatalogService catalog = mock(PlanCatalogService.class);
    private final java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-03T12:00:00Z"), java.time.ZoneId.of("America/Sao_Paulo"));
    private final ManagementReportService service = new ManagementReportService(
            storeRepository,
            ruleRepository,
            fieldRepository,
            planEventRepository, subscriptions, catalog, clock
    );

    @Test
    void excludesCourtesyPremiumStoresFromEstimatedMrr() {
        Store billablePremium = store(PlanType.PREMIUM, false);
        Store courtesyPremium = store(PlanType.PREMIUM, true);
        Store billablePremiumPlus = store(PlanType.PREMIUM_PLUS, false);
        Store internalFree = store(PlanType.FREE_GRATIS, false);
        when(storeRepository.findAll()).thenReturn(List.of(billablePremium, courtesyPremium, billablePremiumPlus, internalFree));
        when(ruleRepository.findAll()).thenReturn(List.of());

        var report = service.report();

        assertThat(report.freeStores()).isEqualTo(1);
        assertThat(report.premiumStores()).isEqualTo(2);
        assertThat(report.premiumPlusStores()).isEqualTo(1);
        assertThat(report.estimatedMrr()).isEqualByComparingTo(new BigDecimal("49.98"));
    }


    @Test
    void excludesInactiveSuspendedAndNonBillableStores() {
        var active = store(PlanType.PREMIUM, false);
        var inactive = store(PlanType.PREMIUM_PLUS, false);
        inactive.setUninstalledAt(java.time.Instant.now());
        var suspended = store(PlanType.PREMIUM_PLUS, false);
        suspended.setBillingSuspended(true);
        when(storeRepository.findAll()).thenReturn(List.of(active, inactive, suspended, store(PlanType.FREE_GRATIS, false)));
        when(ruleRepository.findAll()).thenReturn(List.of());
        assertThat(service.report().estimatedMrr()).isEqualByComparingTo("19.99");
    }

    @Test
    void separatesCurrenciesUsesSubscriptionsAndExcludesSandboxAndPending() {
        var br = store(PlanType.PREMIUM_ULTRA, false); br.setStoreId(1L);
        var usd = store(PlanType.PREMIUM_PLUS, false); usd.setStoreId(2L);
        var sandbox = store(PlanType.PREMIUM, false); sandbox.setStoreId(3L);
        var pending = store(PlanType.PREMIUM, false); pending.setStoreId(4L);
        var brSub = subscription(1L, "BRL", "59.90");
        brSub.applyWinbackCoupon("WELCOME", new BigDecimal("59.90"), new BigDecimal("29.95"));
        brSub.setAmountValue(new BigDecimal("29.95"));
        var usdSub = subscription(2L, "USD", "12.00");
        var testSub = subscription(3L, "BRL", "999"); testSub.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        var pendingSub = subscription(4L, "BRL", "999"); pendingSub.setStatus(PaymentSubscriptionStatus.PENDING);
        when(storeRepository.findAll()).thenReturn(List.of(br, usd, sandbox, pending));
        when(ruleRepository.findAll()).thenReturn(List.of());
        when(subscriptions.findByStoreIdIn(List.of(1L, 2L, 3L, 4L))).thenReturn(List.of(brSub, usdSub, testSub, pendingSub));
        var report = service.report();
        assertThat(report.mrrByCurrency().get("BRL")).isEqualByComparingTo("59.90");
        assertThat(report.mrrByCurrency().get("USD")).isEqualByComparingTo("12.00");
        assertThat(report.projectedPaymentsByCurrency().get("BRL")).isEqualByComparingTo("29.95");
        assertThat(report.projectedStoresByCurrency().get("BRL")).isEqualTo(1);
    }

    @Test
    void excludesOverdueAndNextMonthPaymentsFromProjection() {
        var overdue = store(PlanType.PREMIUM, false); overdue.setBillingNextExecution(java.time.LocalDate.of(2026,10,2));
        var today = store(PlanType.PREMIUM, false); today.setBillingNextExecution(java.time.LocalDate.of(2026,10,3));
        var nextMonth = store(PlanType.PREMIUM, false); nextMonth.setBillingNextExecution(java.time.LocalDate.of(2026,11,1));
        when(storeRepository.findAll()).thenReturn(List.of(overdue, today, nextMonth));
        when(ruleRepository.findAll()).thenReturn(List.of());
        var report = service.report();
        assertThat(report.projectedPaymentsByCurrency().get("BRL")).isEqualByComparingTo("19.99");
        assertThat(report.projectedStoresByCurrency().get("BRL")).isEqualTo(1);
    }

    @Test
    void usesCatalogReferenceOnlyWhenCurrencyIsCompatible() {
        var br = store(PlanType.PREMIUM, false); br.setBillingAmountValue(null);
        var ar = store(PlanType.PREMIUM, false); ar.setBillingAmountValue(null); ar.setBillingAmountCurrency("ARS");
        var reference = new PlanAsset(); reference.setCurrency("BRL"); reference.setAmount(new BigDecimal("25.00"));
        when(catalog.activePlan(PlanType.PREMIUM)).thenReturn(reference);
        when(storeRepository.findAll()).thenReturn(List.of(br, ar));
        when(ruleRepository.findAll()).thenReturn(List.of());
        assertThat(service.report().mrrByCurrency()).containsOnlyKeys("BRL");
        assertThat(service.report().estimatedMrr()).isEqualByComparingTo("25.00");
    }

    @Test
    void excludesCanceledAndCancellationPendingSubscriptions() {
        var canceled = store(PlanType.PREMIUM, false); canceled.setStoreId(1L);
        var pending = store(PlanType.PREMIUM, false); pending.setStoreId(2L);
        var sub1 = subscription(1L, "BRL", "19.99"); sub1.setStatus(PaymentSubscriptionStatus.CANCELED);
        var sub2 = subscription(2L, "BRL", "19.99"); sub2.setCancellationPending(true);
        when(storeRepository.findAll()).thenReturn(List.of(canceled, pending));
        when(subscriptions.findByStoreIdIn(List.of(1L, 2L))).thenReturn(List.of(sub1, sub2));
        when(ruleRepository.findAll()).thenReturn(List.of());
        assertThat(service.report().mrrByCurrency()).isEmpty();
        assertThat(service.report().projectedPaymentsByCurrency()).isEmpty();
    }

    private PaymentSubscription subscription(long storeId, String currency, String amount) {
        var subscription = new PaymentSubscription();
        subscription.setStoreId(storeId); subscription.setCurrency(currency);
        subscription.setAmountValue(new BigDecimal(amount)); subscription.setStatus(PaymentSubscriptionStatus.ACTIVE);
        subscription.setAccessActive(true); subscription.setNextPaymentAt(java.time.Instant.parse("2026-10-10T12:00:00Z"));
        return subscription;
    }

    private Store store(PlanType plan, boolean courtesyPremium) {
        Store store = new Store();
        store.setPlan(plan);
        store.setBillingAmountCurrency("BRL");
        store.setBillingAmountValue(plan == PlanType.PREMIUM ? new BigDecimal("19.99") : new BigDecimal("29.99"));
        store.setCourtesyPremium(courtesyPremium);
        return store;
    }
}
