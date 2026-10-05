package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.service.PaymentSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UltraBillingPageTest {
    @Autowired MockMvc mvc;
    @Autowired StoreRepository stores;
    @MockitoBean PaymentSubscriptionService payments;
    @MockitoBean br.com.nuvemcustomfields.service.EfiUpgradeService upgrades;
    private MockHttpSession session;
    private PaymentSubscription subscription;

    @BeforeEach void setup() {
        stores.findByStoreId(991120L).ifPresent(stores::delete);
        stores.flush();
        Store store = new Store();
        store.setStoreId(991120L); store.setAccessToken("test"); store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL"); store.setPlan(PlanType.PREMIUM_PLUS); store.setStoreName("Ultra test");
        stores.save(store);
        session = new MockHttpSession();
        session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, store.getStoreId());
        subscription = new PaymentSubscription();
        subscription.setStoreId(store.getStoreId()); subscription.setProvider(PaymentProviderType.EFI);
        subscription.setPlan(PlanType.PREMIUM_PLUS); subscription.setStatus(PaymentSubscriptionStatus.ACTIVE);
        subscription.setAccessActive(true); subscription.setCurrency("BRL");
        subscription.setAmountValue(new BigDecimal("29.99"));
        when(payments.find(store.getStoreId())).thenReturn(Optional.of(subscription));
        when(payments.available(any())).thenReturn(true);
        when(payments.anyGatewayEnabled()).thenReturn(true);
        when(payments.currency(any())).thenReturn("BRL");
        when(payments.amount(any(), eq(PlanType.PREMIUM))).thenReturn(new BigDecimal("19.99"));
        when(payments.amount(any(), eq(PlanType.PREMIUM_PLUS))).thenReturn(new BigDecimal("29.99"));
        when(payments.amount(any(), eq(PlanType.PREMIUM_ULTRA))).thenReturn(new BigDecimal("59.90"));
        when(payments.upgradeAmount(any(), eq(PlanType.PREMIUM_ULTRA))).thenReturn(new BigDecimal("59.90"));
        when(payments.planAvailable(any(), eq(PlanType.PREMIUM_ULTRA))).thenReturn(true);
    }

    @Autowired br.com.nuvemcustomfields.service.PlanCatalogService planCatalog;

    @Test
    @org.springframework.transaction.annotation.Transactional
    void billingComparisonShowsConfiguredLimits() throws Exception {
        planCatalog.createVersion(PlanType.PREMIUM_PLUS, "Pro", "", "PREMIUM_PLUS", "BRL",
                new BigDecimal("29.99"), 75, 6, planCatalog.today(), null);
        String plans = mvc.perform(get("/admin/billing").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(plans).contains("75 produtos personalizados", "6 campos por produto", "1 produto personalizado")
                .doesNotContain("50 produtos personalizados");
    }

    @Test void activeProCanReachUltraConfirmationAndSubmitReviewedPrice() throws Exception {
        String plans = mvc.perform(get("/admin/billing").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(plans).contains("Atendimento prioritário", "/admin/billing/upgrade?plan=PREMIUM_ULTRA");
        mvc.perform(get("/admin/billing/upgrade").param("plan", "PREMIUM_ULTRA").session(session))
                .andExpect(redirectedUrl("/admin/billing/upgrade/efi?plan=PREMIUM_ULTRA"));
        mvc.perform(post("/admin/billing/upgrade").session(session).param("plan", "PREMIUM_ULTRA").param("amount", "59.90"))
                .andExpect(redirectedUrl("/admin/billing/upgrade/efi?plan=PREMIUM_ULTRA"));
        verify(payments, never()).upgrade(anyLong(),any(),any());
    }

    @Test void disabledUltraCatalogHidesPurchaseWhileOtherPlansStillRender() throws Exception {
        when(payments.planAvailable(any(), eq(PlanType.PREMIUM_ULTRA))).thenReturn(false);
        String plans = mvc.perform(get("/admin/billing").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(plans).contains("50 produtos personalizados", "Ultra").doesNotContain("/admin/billing/upgrade?plan=PREMIUM_ULTRA");
        mvc.perform(get("/admin/billing/upgrade").session(session).param("plan", "PREMIUM_ULTRA"))
                .andExpect(redirectedUrl("/admin/billing"));
    }

    @Test void paymentAndUpgradePagesRenderAnalyticsWithAmountsAndAnonymousContext() throws Exception {
        when(payments.provider(any())).thenReturn(Optional.of(PaymentProviderType.EFI));
        when(payments.efiQuote(any(), any())).thenReturn(new br.com.nuvemcustomfields.service.WinbackDiscountService.Quote(
                "VOLTA", new BigDecimal("59.90"), new BigDecimal("29.95")));
        String payment = mvc.perform(get("/admin/billing/pay").param("plan", "PREMIUM_ULTRA").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(payment).contains("billing-analytics.js", "step: \"payment\"").doesNotContain("GA4_API_SECRET");
        var a = new UpgradeAdjustment();
        a.setStoreId(991120L); a.setSourcePlan(PlanType.PREMIUM_PLUS); a.setTargetPlan(PlanType.PREMIUM_ULTRA);
        a.setSourceAmount(new BigDecimal("29.99")); a.setRegularAmount(new BigDecimal("59.90"));
        a.setDueAmount(new BigDecimal("8.97")); a.setTargetProrated(new BigDecimal("20.00"));
        a.setDiscountAmount(BigDecimal.ZERO); a.setCreditAmount(new BigDecimal("11.03"));
        a.setPeriodEnd(java.time.Instant.now().plusSeconds(86400)); a.setEnvironment(PaymentEnvironment.SANDBOX);
        when(upgrades.quote(eq(991120L),eq(PlanType.PREMIUM_ULTRA),isNull())).thenReturn(a);
        when(upgrades.owned(991120L,a.getId())).thenReturn(a);
        String review = mvc.perform(get("/admin/billing/upgrade/efi").param("plan", "PREMIUM_ULTRA").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(review).contains("billing-analytics.js", "upgrade_review", "8.97", "59.90", "sandbox: true");
        String upgradePayment = mvc.perform(get("/admin/billing/upgrade/efi/pay").param("id",a.getId()).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(upgradePayment).contains("billing-analytics.js", "8.97", "flow: \"upgrade\"");
    }

    @Test void otherProviderUpgradeConfirmationRendersAnalytics() throws Exception {
        subscription.setProvider(PaymentProviderType.MERCADO_PAGO);
        String page = mvc.perform(get("/admin/billing/upgrade").param("plan","PREMIUM_ULTRA").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("billing-analytics.js", "upgrade_review", "MERCADO_PAGO");
    }
}
