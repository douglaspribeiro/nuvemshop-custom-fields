package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.background.enabled=false", "winback.discount.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class EfiFirstPaymentCouponTest {
    private static final long ID = 990087001L;
    private static final BigDecimal REGULAR = new BigDecimal("19.99"), FIRST = new BigDecimal("13.99");
    @Autowired StoreRepository stores;
    @Autowired UpgradeCouponRepository coupons;
    @Autowired UpgradeCouponUseRepository uses;
    @Autowired UpgradeCouponService catalog;
    @Autowired PlanEventRepository planEvents;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired PaymentSubscriptionService payments;
    @Autowired WinbackDiscountService discounts;
    @Autowired StoreDataErasureService erasure;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockBean EfiGateway efi;
    @MockBean PaymentGatewayRouter router;
    Store store;
    UpgradeCoupon coupon;

    @BeforeEach void setup() {
        when(efi.configured()).thenReturn(true); when(efi.supports(any())).thenReturn(true);
        when(efi.provider()).thenReturn(PaymentProviderType.EFI); when(efi.sandbox()).thenReturn(true);
        when(efi.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(efi.amount(PlanType.PREMIUM)).thenReturn(REGULAR); when(efi.planId(PlanType.PREMIUM)).thenReturn("plan-1");
        when(router.forStore(any())).thenReturn(Optional.of(efi)); when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        store = new Store(); store.setStoreId(ID); store.setStoreCountryCode("BR"); store.setStoreCurrency("BRL");
        store.setStoreEmail("owner@example.test"); store.setAccessToken("test"); stores.saveAndFlush(store);
        coupon = catalog.save(null, "BEMVINDO30", new BigDecimal("30"), PaymentEnvironment.SANDBOX,
                PlanType.PREMIUM, true, null, null, 3, 1, UpgradeCoupon.Purpose.FIRST_PAYMENT);
    }

    private void gateway(String status) {
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(), anyString(), eq(FIRST))).thenReturn("first-sub");
        when(efi.pay(eq("first-sub"), any(), anyString())).thenReturn(json.valueToTree(Map.of("charge_id", "charge-1", "status", status)));
        when(efi.getSubscription("first-sub")).thenReturn(new GatewaySubscription("first-sub", "plan-1", null,
                "active", "BRL", status.equals("paid") ? REGULAR : FIRST, Instant.now().plusSeconds(2592000)));
        when(efi.getLatestInvoice("first-sub")).thenReturn(Optional.of(new GatewayInvoice("charge-1", "first-sub", "charge-1", status)));
        when(efi.getCharge("first-sub", "charge-1")).thenReturn(new GatewayInvoice("charge-1", "first-sub", "charge-1", status));
    }
    private void pay() {
        payments.payWithEfi(ID, PlanType.PREMIUM, new EfiGateway.EfiPayer("Lojista", "12345678901",
                "owner@example.test", "11999999999", "1990-01-01"), "testPaymentToken1234567890", coupon.getCode(), FIRST, REGULAR);
    }
    private MockHttpSession session() {
        var session = new MockHttpSession(); session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY, ID); return session;
    }

    @Test void checkoutAcceptsCodeShowsFirstAndRegularAmountsWithoutConsumingUse() throws Exception {
        var html = mvc.perform(get("/admin/billing/pay").param("plan", "PREMIUM")
                .param("couponCode", " bemvindo30 ").session(session())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Aplicar cupom", "BEMVINDO30", "13,99", "19,99", "A partir da segunda", "quotedFirstAmount")
                .doesNotContain("Cupom de reconquista: 50%", "??checkout.coupon");
        assertThat(coupon.getUsedCount()).isZero(); assertThat(uses.findAll()).isEmpty();
    }

    @Test void invalidCodeShowsErrorAndDoesNotEnablePaymentWithAnUnappliedDiscount() throws Exception {
        var html = mvc.perform(get("/admin/billing/pay").param("plan", "PREMIUM")
                .param("couponCode", "INEXISTENTE").session(session())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Cupom inválido", "disabled=\"disabled\"");
        assertThat(uses.findAll()).isEmpty();
    }

    @Test void reviewedCouponIsSubmittedAndOnlyFirstChargeIsDiscounted() throws Exception {
        gateway("paid");
        mvc.perform(post("/admin/billing/pay").session(session()).param("plan", "PREMIUM")
                .param("payerName", "Lojista").param("cpf", "12345678901").param("payerEmail", "owner@example.test")
                .param("phone", "11999999999").param("birth", "1990-01-01").param("paymentToken", "testPaymentToken1234567890")
                .param("couponCode", coupon.getCode()).param("quotedFirstAmount", FIRST.toPlainString())
                .param("quotedRegularAmount", REGULAR.toPlainString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/billing/processing"));
        verify(efi).createSubscription(eq(PlanType.PREMIUM), anyString(), anyString(), eq(FIRST));
        verify(efi).updateRecurringAmount("first-sub", PlanType.PREMIUM, REGULAR);
        var use = uses.findByCouponId(coupon.getId()).getFirst();
        assertThat(use.getStatus()).isEqualTo(UpgradeCouponUse.Status.USED);
        assertThat(use.getUsedAt()).isNotNull(); assertThat(use.getPriceRestoredAt()).isNotNull();
        assertThat(use.getFirstAmount()).isEqualByComparingTo(FIRST); assertThat(coupon.getUsedCount()).isEqualTo(1);
        assertThat(subscriptions.findByStoreId(ID).orElseThrow().getAmountValue()).isEqualByComparingTo(REGULAR);
        assertThatThrownBy(() -> payments.efiQuote(store, PlanType.PREMIUM, coupon.getCode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("primeiro pagamento");
    }

    @Test void changedCouponCannotChargeAnAmountDifferentFromTheReviewedTotal() {
        coupon.setDiscountPercent(new BigDecimal("20")); coupons.saveAndFlush(coupon);
        assertThatThrownBy(this::pay).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mudou");
        assertThat(coupon.getUsedCount()).isZero(); assertThat(uses.findAll()).isEmpty();
        verify(efi, never()).createSubscription(any(), anyString(), anyString(), any());
    }

    @Test void pendingChargeKeepsReservationAndPreventsAnotherCheckout() {
        gateway("waiting"); pay();
        var use = uses.findByCouponId(coupon.getId()).getFirst();
        assertThat(use.getStatus()).isEqualTo(UpgradeCouponUse.Status.RESERVED); assertThat(use.getUsedAt()).isNull();
        assertThatThrownBy(this::pay).isInstanceOf(IllegalStateException.class);
        verify(efi, times(1)).pay(anyString(), any(), anyString());
        verify(efi, never()).updateRecurringAmount(anyString(), any(), any());
        assertThat(coupon.getUsedCount()).isEqualTo(1);
    }

    @Test void declinedChargeReleasesUseOnlyAfterReducedRecurrenceIsCancelled() {
        gateway("unpaid");
        when(efi.getSubscription("first-sub")).thenReturn(
                new GatewaySubscription("first-sub", "plan-1", null, "active", "BRL", FIRST, null),
                new GatewaySubscription("first-sub", "plan-1", null, "canceled", "BRL", FIRST, null));
        assertThatThrownBy(this::pay).isInstanceOf(PaymentGatewayException.class);
        var use = uses.findByCouponId(coupon.getId()).getFirst();
        assertThat(use.getStatus()).isEqualTo(UpgradeCouponUse.Status.RELEASED);
        assertThat(use.getRecurrenceStoppedAt()).isNotNull(); assertThat(coupon.getUsedCount()).isZero();
        verify(efi).cancel("first-sub");
    }

    @Test void lostPaymentResponseKeepsReservationAndReconcilesWithoutChargingAgain() {
        gateway("waiting");
        when(efi.pay(eq("first-sub"), any(), anyString())).thenThrow(new PaymentGatewayException("response lost"));
        assertThatThrownBy(this::pay).isInstanceOf(PaymentGatewayException.class);
        assertThat(coupon.getUsedCount()).isEqualTo(1);
        when(efi.getFirstInvoice("first-sub")).thenReturn(Optional.of(new GatewayInvoice("charge-1", "first-sub", "charge-1", "paid")));
        discounts.retryRestore(ID);
        assertThat(uses.findByCouponId(coupon.getId()).getFirst().getStatus()).isEqualTo(UpgradeCouponUse.Status.USED);
        assertThat(subscriptions.findByStoreId(ID).orElseThrow().isWinbackRestorePending()).isFalse();
        verify(efi, times(1)).pay(anyString(), any(), anyString());
    }

    @Test void failureToRestoreRegularPriceLeavesDurableTaskAndRetryDoesNotChargeAgain() {
        gateway("paid");
        doThrow(new PaymentGatewayException("temporary failure")).when(efi).updateRecurringAmount("first-sub", PlanType.PREMIUM, REGULAR);
        pay();
        assertThat(subscriptions.findByStoreId(ID).orElseThrow().isWinbackRestorePending()).isTrue();
        doNothing().when(efi).updateRecurringAmount("first-sub", PlanType.PREMIUM, REGULAR);
        discounts.retryRestore(ID);
        assertThat(subscriptions.findByStoreId(ID).orElseThrow().isWinbackRestorePending()).isFalse();
        assertThat(uses.findByCouponId(coupon.getId()).getFirst().getPriceRestoredAt()).isNotNull();
        verify(efi, times(1)).pay(anyString(), any(), anyString());
    }

    @Test void couponsCannotCrossPurposePlanEnvironmentOrValidity() {
        assertThatThrownBy(() -> catalog.eligible(coupon.getCode(), ID, PaymentEnvironment.SANDBOX, PlanType.PREMIUM))
                .isInstanceOf(IllegalArgumentException.class);
        coupon.setEnvironment(PaymentEnvironment.PRODUCTION); coupons.saveAndFlush(coupon);
        assertThatThrownBy(() -> payments.efiQuote(store, PlanType.PREMIUM, coupon.getCode())).isInstanceOf(IllegalArgumentException.class);
        coupon.setEnvironment(PaymentEnvironment.SANDBOX); coupon.setTargetPlan(PlanType.PREMIUM_PLUS); coupons.saveAndFlush(coupon);
        assertThatThrownBy(() -> payments.efiQuote(store, PlanType.PREMIUM, coupon.getCode())).isInstanceOf(IllegalArgumentException.class);
        coupon.setTargetPlan(PlanType.PREMIUM); coupon.setEndsAt(Instant.now().minusSeconds(1)); coupons.saveAndFlush(coupon);
        assertThatThrownBy(() -> payments.efiQuote(store, PlanType.PREMIUM, coupon.getCode())).isInstanceOf(IllegalArgumentException.class);
        coupon.setEndsAt(null); coupon.setPurpose(UpgradeCoupon.Purpose.UPGRADE); coupons.saveAndFlush(coupon);
        assertThatThrownBy(() -> payments.efiQuote(store, PlanType.PREMIUM, coupon.getCode())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aPreviouslyPaidStoreCannotUseAFirstPaymentCoupon() {
        var event = new PlanEvent(); event.setStoreId(ID); event.setFromPlan(PlanType.FREE);
        event.setToPlan(PlanType.PREMIUM); event.setSource("EFI_CHECKOUT"); planEvents.saveAndFlush(event);
        assertThatThrownBy(() -> payments.efiQuote(store, PlanType.PREMIUM, coupon.getCode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("primeiro pagamento");
        assertThatThrownBy(this::pay).isInstanceOf(IllegalArgumentException.class);
        verify(efi, never()).pay(anyString(), any(), anyString());
    }

    @Test void freeFirstMonthAndReservedWinbackNamespaceCannotBeCreated() {
        assertThatThrownBy(() -> catalog.save(null, "GRATIS", new BigDecimal("100"), PaymentEnvironment.SANDBOX,
                PlanType.PREMIUM, true, null, null, null, 1, UpgradeCoupon.Purpose.FIRST_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.save(null, "VOLTE50-RESERVADO", new BigDecimal("50"), PaymentEnvironment.SANDBOX,
                PlanType.PREMIUM, true, null, null, null, 1, UpgradeCoupon.Purpose.FIRST_PAYMENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void erasureCancelsUnrestoredDiscountBeforeRemovingUseBindings() {
        gateway("waiting"); pay();
        when(efi.getCharge("first-sub", "charge-1")).thenReturn(new GatewayInvoice("charge-1", "first-sub", "charge-1", "unpaid"));
        when(efi.getSubscription("first-sub")).thenReturn(
                new GatewaySubscription("first-sub", "plan-1", null, "canceled", "BRL", FIRST, null));
        erasure.erase(ID);
        assertThat(uses.findByCouponId(coupon.getId())).isEmpty(); assertThat(stores.findByStoreId(ID)).isEmpty();
        assertThat(coupons.findById(coupon.getId()).orElseThrow().getUsedCount()).isZero();
    }
}
