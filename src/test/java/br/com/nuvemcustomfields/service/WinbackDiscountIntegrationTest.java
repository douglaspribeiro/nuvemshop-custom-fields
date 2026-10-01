package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"winback.discount.enabled=true", "winback.discount.allow-production=false",
        "notifications.ses.host=smtp.example.test", "notifications.ses.port=587", "notifications.ses.username=test",
        "notifications.ses.password=test", "notifications.ses.from=support@example.test",
        "notifications.ses-events.configuration-set=tracking-test"})
@AutoConfigureMockMvc
@Transactional
class WinbackDiscountIntegrationTest {
    private static final long ID = 991122440L;
    private static final BigDecimal REGULAR = new BigDecimal("19.99");
    private static final BigDecimal FIRST = new BigDecimal("9.99");
    @Autowired StoreRepository stores;
    @Autowired WinbackCampaignRepository campaigns;
    @Autowired WinbackCouponRepository coupons;
    @Autowired WinbackEmailRepository emails;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired PaymentNotificationOutboxRepository notifications;
    @Autowired WinbackCampaignService campaignService;
    @Autowired WinbackTrackingService tracking;
    @Autowired WinbackDiscountService discounts;
    @Autowired PaymentSubscriptionService payments;
    @Autowired StoreDataErasureService erasure;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @MockBean EfiGateway efi;
    @MockBean PaymentGatewayRouter router;
    @MockBean JavaMailSender sender;
    Store store;
    WinbackCampaign campaign;
    WinbackEmail feedback;

    @BeforeEach
    void setup() {
        when(efi.sandbox()).thenReturn(true); when(efi.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(efi.configured()).thenReturn(true); when(efi.provider()).thenReturn(PaymentProviderType.EFI);
        when(efi.supports(any())).thenAnswer(i -> "BR".equals(((Store)i.getArgument(0)).getStoreCountryCode()));
        when(efi.amount(PlanType.PREMIUM)).thenReturn(REGULAR); when(efi.planId(PlanType.PREMIUM)).thenReturn("plan-1");
        when(router.forStore(any())).thenReturn(Optional.of(efi)); when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        store = new Store(); store.setStoreId(ID); store.setStoreName("Loja com desconto");
        store.setStoreCountryCode("BR"); store.setStoreCurrency("BRL"); store.setStoreEmail("owner@example.test");
        store.setUninstalledAt(Instant.now().minusSeconds(1000)); stores.saveAndFlush(store);
        campaign = tracking.record(store);
        feedback = new WinbackEmail(campaign.getId(), "FEEDBACK"); feedback.sent(); emails.saveAndFlush(feedback);
    }

    private WinbackCoupon issue() {
        campaignService.respond(feedback.getId(), "PRICE", "", false);
        return coupons.findByStoreId(ID).orElseThrow();
    }
    private void reinstall() {
        store.setUninstalledAt(null); store.setAccessToken("test-access-token"); stores.saveAndFlush(store);
        tracking.reinstalled(ID);
    }
    private void gatewayAccepts(String status) {
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(), anyString(), eq(FIRST))).thenReturn("promo-sub");
        when(efi.pay(eq("promo-sub"), any(), anyString())).thenReturn(json.valueToTree(Map.of("charge_id", "charge-1", "status", status)));
        when(efi.getSubscription("promo-sub")).thenReturn(new GatewaySubscription("promo-sub", "plan-1", null,
                "active", "BRL", "paid".equals(status) ? REGULAR : FIRST, Instant.now().plusSeconds(2592000)));
        when(efi.getLatestInvoice("promo-sub")).thenReturn(Optional.of(new GatewayInvoice("charge-1", "promo-sub", "charge-1", status)));
        when(efi.getCharge("promo-sub", "charge-1")).thenReturn(new GatewayInvoice("charge-1", "promo-sub", "charge-1", status));
    }
    private void pay(String code) {
        payments.payWithEfi(ID, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Lojista", "12345678901", "owner@example.test", "11999999999", "1990-01-01"),
                "testPaymentToken1234567890", code);
    }

    @Test
    void couponNeedsSameStoreReinstallationAndExpiresWithoutBeingUsed() {
        var coupon = issue();
        assertThat(discounts.quote(store, PlanType.PREMIUM).discounted()).isFalse();
        reinstall();
        assertThat(discounts.quote(store, PlanType.PREMIUM).firstAmount()).isEqualByComparingTo(FIRST);
        var other = new Store(); other.setStoreId(ID + 1); other.setStoreCountryCode("BR");
        assertThatThrownBy(() -> discounts.reserve(other, PlanType.PREMIUM, coupon.getCode(), "other-ref"))
                .isInstanceOf(IllegalArgumentException.class);
        org.springframework.test.util.ReflectionTestUtils.setField(coupon, "expiresAt", Instant.now().minusSeconds(1));
        assertThat(discounts.quote(store, PlanType.PREMIUM).discounted()).isFalse();
        assertThat(coupon.getUsedAt()).isNull();
    }

    @Test
    void approvedFirstChargeUsesCouponRestoresPriceAndRecordsActualAmount() {
        var coupon = issue(); reinstall(); gatewayAccepts("paid"); pay(coupon.getCode()); em.flush();
        var local = subscriptions.findByStoreId(ID).orElseThrow();
        assertThat(local.getAmountValue()).isEqualByComparingTo(REGULAR);
        assertThat(local.getWinbackInitialAmount()).isEqualByComparingTo(FIRST);
        assertThat(local.isWinbackRestorePending()).isFalse();
        assertThat(coupon.getUsedAt()).isNotNull(); assertThat(coupon.getPriceRestoredAt()).isNotNull();
        assertThat(campaign.getConvertedAt()).isNotNull();
        assertThat(notifications.findAll()).singleElement().extracting(PaymentNotificationOutbox::getAmountValue)
                .isEqualTo(FIRST);
        verify(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
        assertThat(discounts.quote(store, PlanType.PREMIUM).discounted()).isFalse();
        assertThat(tracking.list(0, "", "coupon-used", null, null).getTotalElements()).isEqualTo(1);
    }

    @Test
    void pendingChargeKeepsCouponUnusedAndRestoresOnlyAfterApproval() {
        var coupon = issue(); reinstall(); gatewayAccepts("waiting"); pay(coupon.getCode());
        assertThat(coupon.getCheckoutAt()).isNotNull(); assertThat(coupon.getUsedAt()).isNull();
        assertThat(coupon.getPriceRestoredAt()).isNull(); assertThat(campaign.getConvertedAt()).isNull();
        assertThat(notifications.count()).isZero();
        verify(efi, never()).updateRecurringAmount(anyString(), any(), any());
        when(efi.getLatestInvoice("promo-sub")).thenReturn(Optional.of(new GatewayInvoice("charge-1", "promo-sub", "charge-1", "paid")));
        payments.reconcile(ID);
        assertThat(coupon.getPriceRestoredAt()).isNotNull(); assertThat(coupon.getUsedAt()).isNotNull();
        verify(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
    }

    @Test
    void restorationFailurePersistsTaskAndRetryDoesNotChargeAgain() {
        var coupon = issue(); reinstall(); gatewayAccepts("paid");
        doThrow(new PaymentGatewayException("simulated")).when(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
        pay(coupon.getCode());
        var local = subscriptions.findByStoreId(ID).orElseThrow();
        assertThat(local.isWinbackRestorePending()).isTrue(); assertThat(coupon.getUsedAt()).isNotNull();
        doNothing().when(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
        discounts.retryRestore(ID);
        assertThat(local.isWinbackRestorePending()).isFalse();
        verify(efi, times(1)).pay(eq("promo-sub"), any(), anyString());
    }

    @Test
    void expiredOrPreviouslyPaidStoreDoesNotGetAutomaticCoupon() {
        campaign.setPaidBeforeDeparture(true);
        campaignService.respond(feedback.getId(), "PRICE", "", false);
        assertThat(coupons.findByStoreId(ID)).isEmpty();
        assertThat(emails.findByCampaignIdAndStep(campaign.getId(), "FOLLOWUP")).isPresent();
    }

    @Test
    void productionRemainsBlockedUntilExplicitlyConfigured() {
        when(efi.sandbox()).thenReturn(false); when(efi.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        campaignService.respond(feedback.getId(), "PRICE", "", false);
        assertThat(coupons.findByStoreId(ID)).isEmpty();
    }

    @Test
    void repeatingFeedbackDoesNotCreateAnotherCouponOrFollowup() {
        var coupon = issue(); campaignService.respond(feedback.getId(), "OTHER", "Outro motivo", false);
        assertThat(coupons.findByStoreId(ID)).get().extracting(WinbackCoupon::getCode).isEqualTo(coupon.getCode());
        assertThat(emails.findByCampaignIdOrderByCreatedAtAsc(campaign.getId())).hasSize(2);
    }

    @Test
    void publicOfferAndCheckoutExplainFirstMonthAndFollowingMonths() throws Exception {
        var coupon = issue();
        mvc.perform(get("/winback/" + feedback.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString(coupon.getCode())))
                .andExpect(content().string(containsString("primeira mensalidade")));
        reinstall();
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, ID);
        mvc.perform(get("/admin/billing/pay").param("plan", "PREMIUM").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("A partir da segunda")))
                .andExpect(content().string(containsString(coupon.getCode())));
    }

    @Test
    void featureRequestNeedsDetailsAndDeliveredStateQueuesOnlyOneNotice() throws Exception {
        mvc.perform(post("/winback/" + feedback.getId()).param("reason", "MISSING_FEATURE"))
                .andExpect(status().isBadRequest());
        campaignService.respond(feedback.getId(), "MISSING_FEATURE", "Upload de arquivos", false);
        assertThat(campaign.getFeatureStatus()).isEqualTo("NOVA");
        var session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        mvc.perform(get("/backoffice/winback/" + campaign.getId()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Solicitação de funcionalidade")));
        campaignService.featureStatus(campaign.getId(), "ENTREGUE"); campaignService.featureStatus(campaign.getId(), "ENTREGUE");
        assertThat(emails.findByCampaignIdOrderByCreatedAtAsc(campaign.getId())).hasSize(3);
    }

    @Test
    void erasureRemovesCouponAndConversion() {
        var coupon = issue(); reinstall(); gatewayAccepts("paid"); pay(coupon.getCode());
        erasure.erase(ID); em.clear();
        assertThat(coupons.findById(coupon.getCode())).isEmpty(); assertThat(campaigns.findById(campaign.getId())).isEmpty();
        assertThat(subscriptions.findByStoreId(ID)).isEmpty();
    }

    @Test
    void sendsOfferOnceWithClearFirstMonthTermsAndTrackingTags() throws Exception {
        issue();
        var followup = emails.findByCampaignIdAndStep(campaign.getId(), "FOLLOWUP").orElseThrow();
        String id = campaignService.prepareFollowup(followup.getId());
        campaignService.send(id);
        assertThat(campaignService.prepareFollowup(id)).isNull();
        var capture = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, times(1)).send(capture.capture());
        assertThat(capture.getValue().getSubject()).isEqualTo("Seu desconto para voltar ao Campos Personalizados");
        assertThat(capture.getValue().getHeader("X-SES-MESSAGE-TAGS", null)).contains("winback_email=" + id);
        assertThat(mailText(capture.getValue().getContent())).contains("50%", "primeira mensalidade", "segunda mensalidade", "30 dias");
    }

    @Test
    void optOutCancelsPreparedFollowup() throws Exception {
        issue();
        var followup = emails.findByCampaignIdAndStep(campaign.getId(), "FOLLOWUP").orElseThrow();
        campaign.optOut(); campaignService.send(campaignService.prepareFollowup(followup.getId()));
        assertThat(followup.getStatus()).isEqualTo("CANCELLED");
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void deliveredFeatureNotificationWorksAfterReinstallation() throws Exception {
        campaignService.respond(feedback.getId(), "MISSING_FEATURE", "Upload", false);
        campaignService.featureStatus(campaign.getId(), "ENTREGUE"); reinstall();
        var notice = emails.findByCampaignIdAndStep(campaign.getId(), "FEATURE_DELIVERED").orElseThrow();
        campaignService.send(campaignService.prepareFollowup(notice.getId()));
        var capture = org.mockito.ArgumentCaptor.forClass(MimeMessage.class); verify(sender).send(capture.capture());
        assertThat(capture.getValue().getSubject()).contains("funcionalidade", "disponível");
    }

    @Test
    void discoversFirstChargeAfterLostPaymentResponseAndRestoresWithoutRecharging() {
        var coupon = issue(); reinstall(); gatewayAccepts("waiting");
        when(efi.pay(eq("promo-sub"), any(), anyString())).thenThrow(new PaymentGatewayException("lost response"));
        assertThatThrownBy(() -> pay(coupon.getCode())).isInstanceOf(PaymentGatewayException.class);
        var local = subscriptions.findByStoreId(ID).orElseThrow();
        assertThat(local.getWinbackFirstPaymentId()).isNull(); assertThat(local.isWinbackRestorePending()).isTrue();
        when(efi.getFirstInvoice("promo-sub")).thenReturn(Optional.of(new GatewayInvoice("charge-1", "promo-sub", "charge-1", "paid")));
        discounts.retryRestore(ID);
        assertThat(local.isWinbackRestorePending()).isFalse(); assertThat(coupon.getUsedAt()).isNotNull();
        verify(efi, times(1)).pay(eq("promo-sub"), any(), anyString());
    }

    @Test
    void erasureStopsReducedRecurrenceWhenRestorationFails() {
        var coupon = issue(); reinstall(); gatewayAccepts("paid");
        doThrow(new PaymentGatewayException("simulated")).when(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
        pay(coupon.getCode());
        erasure.erase(ID); em.clear();
        verify(efi).cancel("promo-sub");
        assertThat(coupons.findById(coupon.getCode())).isEmpty(); assertThat(subscriptions.findByStoreId(ID)).isEmpty();
    }

    @Test
    void restorationDuringErasureFlushesBeforeDeletingFinancialRows() {
        var coupon = issue(); reinstall(); gatewayAccepts("paid");
        doThrow(new PaymentGatewayException("simulated")).when(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
        pay(coupon.getCode());
        doNothing().when(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
        erasure.erase(ID); em.flush(); em.clear();
        assertThat(stores.findByStoreId(ID)).isEmpty(); assertThat(coupons.findById(coupon.getCode())).isEmpty();
        verify(efi, never()).cancel("promo-sub");
    }

    @Test
    void declinedFirstChargeStopsPromotionalRecurrenceAndDoesNotUseCoupon() {
        var coupon = issue(); reinstall(); gatewayAccepts("unpaid");
        when(efi.getSubscription("promo-sub")).thenReturn(
                new GatewaySubscription("promo-sub", "plan-1", null, "active", "BRL", FIRST, null),
                new GatewaySubscription("promo-sub", "plan-1", null, "canceled", "BRL", FIRST, null));
        assertThatThrownBy(() -> pay(coupon.getCode())).isInstanceOf(PaymentGatewayException.class);
        assertThat(coupon.getUsedAt()).isNull(); assertThat(coupon.getRecurrenceStoppedAt()).isNotNull();
        assertThat(subscriptions.findByStoreId(ID).orElseThrow().isWinbackRestorePending()).isFalse();
        verify(efi).cancel("promo-sub"); verify(efi, never()).updateRecurringAmount(anyString(), any(), any());
    }

    @Test
    void twoStoresCanUseTheSameEfiPlanWithSeparateCouponsAndSubscriptions() {
        var firstCoupon = issue(); reinstall(); gatewayAccepts("paid"); pay(firstCoupon.getCode());
        var other = new Store(); other.setStoreId(ID + 1); other.setStoreCountryCode("BR");
        other.setStoreCurrency("BRL"); other.setStoreEmail("other@example.test");
        other.setUninstalledAt(Instant.now().minusSeconds(1000)); stores.saveAndFlush(other);
        var otherCampaign = tracking.record(other);
        var otherFeedback = new WinbackEmail(otherCampaign.getId(), "FEEDBACK"); otherFeedback.sent(); emails.saveAndFlush(otherFeedback);
        campaignService.respond(otherFeedback.getId(), "PRICE", "", false);
        var otherCoupon = coupons.findByStoreId(ID + 1).orElseThrow();
        other.setUninstalledAt(null); stores.saveAndFlush(other); tracking.reinstalled(ID + 1);
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(), anyString(), eq(FIRST))).thenReturn("promo-sub-2");
        when(efi.pay(eq("promo-sub-2"), any(), anyString())).thenReturn(json.valueToTree(Map.of("charge_id", "charge-2", "status", "paid")));
        when(efi.getSubscription("promo-sub-2")).thenReturn(new GatewaySubscription("promo-sub-2", "plan-1", null, "active", "BRL", REGULAR, null));
        when(efi.getLatestInvoice("promo-sub-2")).thenReturn(Optional.of(new GatewayInvoice("charge-2", "promo-sub-2", "charge-2", "paid")));
        payments.payWithEfi(ID + 1, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Outro lojista", "12345678901", "other@example.test", "11999999999", "1990-01-01"),
                "testPaymentToken1234567890", otherCoupon.getCode());
        em.flush();
        assertThat(subscriptions.findByStoreId(ID).orElseThrow().getProviderPriceId()).isEqualTo("plan-1");
        assertThat(subscriptions.findByStoreId(ID + 1).orElseThrow().getProviderPriceId()).isEqualTo("plan-1");
        assertThat(subscriptions.findByStoreId(ID + 1).orElseThrow().getProviderCheckoutId()).isNull();
        assertThat(otherCoupon.getUsedAt()).isNotNull();
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void newlyConfirmedPaymentCommitsEvenIfPriceRestorationFails() {
        var coupon = issue(); reinstall(); gatewayAccepts("waiting");
        when(efi.pay(eq("promo-sub"), any(), anyString())).thenThrow(new PaymentGatewayException("lost response"));
        try {
            assertThatThrownBy(() -> pay(coupon.getCode())).isInstanceOf(PaymentGatewayException.class);
            when(efi.getFirstInvoice("promo-sub")).thenReturn(Optional.of(new GatewayInvoice("charge-1", "promo-sub", "charge-1", "paid")));
            doThrow(new PaymentGatewayException("restore unavailable")).when(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
            assertThatThrownBy(() -> discounts.retryRestore(ID)).isInstanceOf(PaymentGatewayException.class);
            assertThat(coupons.findById(coupon.getCode()).orElseThrow().getUsedAt()).isNotNull();
            assertThat(subscriptions.findByStoreId(ID).orElseThrow().isWinbackRestorePending()).isTrue();
        } finally {
            doNothing().when(efi).updateRecurringAmount("promo-sub", PlanType.PREMIUM, REGULAR);
            erasure.erase(ID);
        }
    }

    private String mailText(Object content) throws Exception {
        if (content instanceof String text) return text;
        if (content instanceof jakarta.mail.Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) text.append(mailText(multipart.getBodyPart(i).getContent()));
            return text.toString();
        }
        return "";
    }
}
