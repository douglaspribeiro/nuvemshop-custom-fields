package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.*;
import br.com.nuvemcustomfields.controller.BackofficeUpgradeCouponsController;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.background.enabled=false") @AutoConfigureMockMvc @Transactional
class DodoCouponServiceTest {
    private static final long ID=990088501L;
    @Autowired DodoCouponService service;
    @Autowired UpgradeCouponService catalog;
    @Autowired UpgradeCouponRepository coupons;
    @Autowired UpgradeCouponUseRepository uses;
    @Autowired PaymentCatalogPriceRepository prices;
    @Autowired StoreRepository stores;
    @Autowired PlanEventRepository events;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockBean DodoTestGateway gateway;
    @MockBean DodoLiveGateway live;
    @MockBean PaymentGatewayRouter router;
    @MockBean PaymentSubscriptionService payments;
    Store store; UpgradeCoupon coupon;

    @BeforeEach void setup() {
        when(gateway.provider()).thenReturn(PaymentProviderType.DODO_TEST);when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(gateway.planAvailable(any(),eq(PlanType.PREMIUM))).thenReturn(true);
        when(gateway.amount(any(),eq(PlanType.PREMIUM))).thenReturn(new BigDecimal("10.00"));
        when(gateway.currency(any())).thenReturn("USD");when(gateway.priceId(any(),any())).thenReturn("pdt_first");
        when(router.requireForStore(any())).thenReturn(gateway);
        store=new Store();store.setStoreId(ID);store.setStoreCountryCode("CO");store.setStoreCurrency("USD");store.setAccessToken("test");stores.saveAndFlush(store);
        var price=new PaymentCatalogPrice();price.setProvider(PaymentProviderType.DODO_TEST);price.setEnvironment(PaymentEnvironment.SANDBOX);
        price.setCountryCode("CO");price.setPlan(PlanType.PREMIUM);price.setCurrency("USD");price.setAmountValue(new BigDecimal("10.00"));
        price.setProviderPriceId("pdt_first");price.setEnabled(true);price.setValidatedAt(Instant.now());prices.saveAndFlush(price);
        coupon=catalog.save(null,"FIRST30",new BigDecimal("30"),PaymentEnvironment.SANDBOX,PlanType.PREMIUM,false,null,null,3,1,
                UpgradeCoupon.Purpose.FIRST_PAYMENT,PaymentProviderType.DODO);
        when(gateway.synchronizeDiscount(any(),anyList())).thenAnswer(i->{
            UpgradeCoupon c=i.getArgument(0);assertThat(c.isEnabled()).isFalse();return nativeDiscount(c);
        });
        service.synchronize(coupon,true);
        when(gateway.discountByCode("FIRST30")).thenAnswer(i->Optional.of(nativeDiscount(coupon)));
        when(gateway.previewFirstAmount(any(),eq(PlanType.PREMIUM),eq("FIRST30"))).thenReturn(new BigDecimal("7.00"));
    }
    private ObjectNode nativeDiscount(UpgradeCoupon c) {
        var node=json.createObjectNode();node.put("discount_id","dsc_first");node.put("code",c.getCode());node.put("type","percentage");
        node.put("amount",c.getDiscountPercent().movePointRight(2).intValueExact());node.put("subscription_cycles",1);
        node.putNull("per_customer_usage_limit");node.put("customer_eligibility","any");node.put("preserve_on_plan_change",false);
        node.put("times_used",0);if(c.getMaxUses()==null)node.putNull("usage_limit");else node.put("usage_limit",c.getMaxUses());
        node.putNull("starts_at");node.putNull("expires_at");node.putObject("metadata").put("ncf_coupon_id",c.getId().toString());
        node.putArray("restricted_to").add("pdt_first");return node;
    }
    private MockHttpSession merchant() {var s=new MockHttpSession();s.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,ID);return s;}

    @Test void nativeSynchronizationEnablesOnlyAfterConfirmationAndKeepsEfiIsolated() {
        assertThat(coupon.isEnabled()).isTrue();assertThat(coupon.getNativeDiscountId()).isEqualTo("dsc_first");
        assertThatThrownBy(()->catalog.firstPaymentEligible("FIRST30",ID,PaymentEnvironment.SANDBOX,PlanType.PREMIUM))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(live);
        assertThat(catalog.delete(coupon.getId())).isFalse();assertThat(coupons.findById(coupon.getId())).isPresent();
    }
    @Test void quoteUsesNativePriceWithoutReservingAnApplicationUse() throws Exception {
        var quote=service.quote(store,PlanType.PREMIUM,"first30");
        assertThat(quote.first()).isEqualByComparingTo("7.00");assertThat(quote.regular()).isEqualByComparingTo("10.00");
        assertThat(uses.findAll()).isEmpty();assertThat(coupon.getUsedCount()).isZero();
        var html=mvc.perform(get("/admin/billing/pay/dodo").session(merchant()).param("plan","PREMIUM").param("couponCode","FIRST30"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("FIRST30","7,00","10,00","Continuar en Dodo").doesNotContain("??dodo.coupon", "??admin.billing");
    }
    @Test void invalidNativeDurationNeverReachesPaymentPreview() {
        var remote=nativeDiscount(coupon);remote.put("subscription_cycles",2);
        when(gateway.discountByCode("FIRST30")).thenReturn(Optional.of(remote));
        assertThatThrownBy(()->service.quote(store,PlanType.PREMIUM,"FIRST30"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("primeira mensalidade");
        verify(gateway,never()).previewFirstAmount(any(),any(),anyString());
    }
    @Test void wrongProductOrNativeOwnerIsRejected() {
        var remote=nativeDiscount(coupon);remote.putArray("restricted_to").add("pdt_other");
        when(gateway.discountByCode("FIRST30")).thenReturn(Optional.of(remote));
        assertThatThrownBy(()->service.quote(store,PlanType.PREMIUM,"FIRST30")).isInstanceOf(IllegalArgumentException.class);
        remote=nativeDiscount(coupon);remote.putObject("metadata").put("ncf_coupon_id","99999");
        when(gateway.discountByCode("FIRST30")).thenReturn(Optional.of(remote));
        assertThatThrownBy(()->service.quote(store,PlanType.PREMIUM,"FIRST30")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void confirmedPaymentConsumesExactlyOneUseAndRenewalsDoNotConsumeAnother() {
        var quote=service.quote(store,PlanType.PREMIUM,"FIRST30");service.reserve(quote,store,"first-reference");
        var local=new PaymentSubscription();local.setStoreId(ID);local.setProvider(PaymentProviderType.DODO_TEST);local.setExternalReference("first-reference");
        var remote=new GatewaySubscription("sub_first",null,"first-reference","active","USD",new BigDecimal("10.00"),Instant.now().plusSeconds(2592000));
        var invoice=new GatewayInvoice("pay_first","sub_first","pay_first","paid","USD",new BigDecimal("7.00"));
        service.confirm(local,remote,invoice);
        service.confirm(local,remote,new GatewayInvoice("renewal","sub_first","renewal","paid","USD",new BigDecimal("10.00")));
        var use=uses.findByPaymentReference("first-reference").orElseThrow();
        assertThat(use.getStatus()).isEqualTo(UpgradeCouponUse.Status.USED);assertThat(use.getFirstPaymentId()).isEqualTo("pay_first");
        assertThat(coupon.getUsedCount()).isEqualTo(1);
    }
    @Test void paymentFailureKeepsReservationUntilTheSubscriptionIsConfirmedCancelled() {
        service.reserve(service.quote(store,PlanType.PREMIUM,"FIRST30"),store,"pending-reference");
        var local=new PaymentSubscription();local.setStoreId(ID);local.setProvider(PaymentProviderType.DODO_TEST);local.setExternalReference("pending-reference");
        var remote=new GatewaySubscription("sub_first",null,"pending-reference","active","USD",BigDecimal.TEN,null);
        service.confirm(local,remote,new GatewayInvoice("failure","sub_first","failure","unpaid"));
        assertThat(coupon.getUsedCount()).isEqualTo(1);
        service.confirm(local,new GatewaySubscription("sub_first",null,"pending-reference","cancelled","USD",BigDecimal.TEN,null),null);
        assertThat(coupon.getUsedCount()).isZero();
        assertThat(uses.findByPaymentReference("pending-reference").orElseThrow().getStatus()).isEqualTo(UpgradeCouponUse.Status.RELEASED);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"failed","expired","cancelled"})
    void confirmedTerminalSubscriptionReleasesAnUnpaidCouponForRetry(String status) {
        service.reserve(service.quote(store,PlanType.PREMIUM,"FIRST30"),store,"terminal-reference");
        var local=new PaymentSubscription();local.setStoreId(ID);local.setProvider(PaymentProviderType.DODO_TEST);local.setExternalReference("terminal-reference");
        service.confirm(local,new GatewaySubscription("sub_first",null,"terminal-reference",status,"USD",BigDecimal.TEN,null),null);
        assertThat(coupon.getUsedCount()).isZero();
        assertThat(uses.findByPaymentReference("terminal-reference").orElseThrow().getStatus()).isEqualTo(UpgradeCouponUse.Status.RELEASED);
    }

    @Test void priorPaymentPreventsAnotherWelcomeDiscount() {
        var event=new PlanEvent();event.setStoreId(ID);event.setFromPlan(PlanType.FREE);event.setToPlan(PlanType.PREMIUM);event.setSource("PAYMENT_WEBHOOK");events.saveAndFlush(event);
        assertThatThrownBy(()->service.quote(store,PlanType.PREMIUM,"FIRST30"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("primeiro pagamento");
    }
    @Test void backofficeCreatesNativeCouponBeforeActivatingIt() throws Exception {
        var s=new MockHttpSession();s.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        mvc.perform(get("/backoffice/payments/coupons").session(s)).andExpect(status().isOk());
        var token=(String)s.getAttribute(BackofficeUpgradeCouponsController.TOKEN_KEY);
        mvc.perform(post("/backoffice/payments/coupons").session(s).param("actionToken",token).param("code","NEW30")
                .param("discountPercent","30").param("environment","SANDBOX").param("provider","DODO")
                .param("purpose","FIRST_PAYMENT").param("targetPlan","PREMIUM").param("enabled","true").param("maxUses","3"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        var saved=coupons.findByCode("NEW30").orElseThrow();
        assertThat(saved.isEnabled()).isTrue();assertThat(saved.getNativeDiscountId()).isEqualTo("dsc_first");
    }
    @Test void uncertainNativeCreationLeavesCouponDisabledForAnExplicitRetry() throws Exception {
        doThrow(new PaymentGatewayException("unknown response")).when(gateway).synchronizeDiscount(any(),anyList());
        var s=new MockHttpSession();s.setAttribute(BackofficeSessionInterceptor.SESSION_KEY,true);
        mvc.perform(get("/backoffice/payments/coupons").session(s));
        mvc.perform(post("/backoffice/payments/coupons").session(s)
                .param("actionToken",(String)s.getAttribute(BackofficeUpgradeCouponsController.TOKEN_KEY))
                .param("code","FAIL30").param("discountPercent","30").param("environment","SANDBOX").param("provider","DODO")
                .param("purpose","FIRST_PAYMENT").param("targetPlan","PREMIUM").param("enabled","true"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        var saved=coupons.findByCode("FAIL30").orElseThrow();assertThat(saved.isEnabled()).isFalse();assertThat(saved.getNativeDiscountId()).isNull();
    }
    @Test void pendingCheckoutOffersResumeWithoutRevalidatingOrCreatingAnotherCoupon() throws Exception {
        var pending=new PaymentSubscription();pending.setStoreId(ID);pending.setProvider(PaymentProviderType.DODO_TEST);
        pending.setPlan(PlanType.PREMIUM);pending.setStatus(PaymentSubscriptionStatus.PENDING);
        pending.setCheckoutUrl("https://test.checkout.dodopayments.com/cks_existing");
        when(payments.find(ID)).thenReturn(Optional.of(pending));
        var html=mvc.perform(get("/admin/billing/pay/dodo").session(merchant()).param("plan","PREMIUM").param("couponCode","FIRST30"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Retomar el pago","cks_existing").doesNotContain("name=\"couponCode\"");
        verify(gateway,never()).discountByCode(anyString());
    }
}
