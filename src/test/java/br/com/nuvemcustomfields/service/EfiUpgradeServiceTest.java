package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.AdminSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EfiUpgradeServiceTest {
    @Autowired EfiUpgradeService service;
    @Autowired PaymentSubscriptionService payments;
    @Autowired UpgradeAdjustmentRepository adjustments;
    @Autowired PaymentSubscriptionRepository subscriptions;
    @Autowired StoreRepository stores;
    @Autowired UpgradeCouponRepository coupons;
    @Autowired UpgradeCouponUseRepository couponUses;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockitoSpyBean EfiGateway efi;
    final Long storeId=991122L;
    Instant start,end;
    MockHttpSession session;
    AtomicBoolean changed;
    AtomicReference<String> paymentStatus;
    UpgradeAdjustment quote;
    final EfiGateway.EfiPayer payer=new EfiGateway.EfiPayer("Teste titular","12345678901","test@example.com","11999999999","1990-01-01");
    final String token="testpaymenttoken12345678901234567890";

    @BeforeEach void setup(){
        couponUses.deleteAll(couponUses.findTop100ByOrderByCreatedAtDesc().stream().filter(u->u.getStoreId().equals(storeId)).toList());
        var coupon=coupons.findByCode("BRINDE").orElseGet(UpgradeCoupon::new);
        coupon.setCode("BRINDE");coupon.setDiscountPercent(new BigDecimal("30"));coupon.setEnvironment(PaymentEnvironment.PRODUCTION);
        coupon.setTargetPlan(PlanType.PREMIUM_ULTRA);coupon.setEnabled(true);coupon.setMaxUsesPerStore(1);coupon.setUsedCount(0);
        coupon.setMaxUses(null);coupon.setStartsAt(null);coupon.setEndsAt(null);coupons.save(coupon);
        adjustments.findByStoreIdOrderByQuotedAtDesc(storeId).forEach(adjustments::delete);
        subscriptions.findByStoreId(storeId).ifPresent(subscriptions::delete);
        stores.findByStoreId(storeId).ifPresent(stores::delete);
        start=LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(15).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        end=start.plus(Duration.ofDays(30)); changed=new AtomicBoolean(false);paymentStatus=new AtomicReference<>("paid");
        Store store=new Store();store.setStoreId(storeId);store.setAccessToken("test");store.setStoreCountryCode("BR");store.setStoreCurrency("BRL");store.setPlan(PlanType.PREMIUM_PLUS);stores.save(store);
        PaymentSubscription sub=new PaymentSubscription();sub.setStoreId(storeId);sub.setProvider(PaymentProviderType.EFI);sub.setProviderEnvironment(PaymentEnvironment.PRODUCTION);
        sub.setProviderSubscriptionId("991122");sub.setProviderPriceId("2");sub.setPlan(PlanType.PREMIUM_PLUS);sub.setCurrency("BRL");sub.setAmountValue(new BigDecimal("29.99"));
        sub.setStatus(PaymentSubscriptionStatus.ACTIVE);sub.setAccessActive(true);sub.setExternalReference("test-upgrade-991122");sub.setNextPaymentAt(end);sub.setLastPaymentId("prior-paid");sub.setLastPaymentStatus("paid");subscriptions.save(sub);
        session=new MockHttpSession();session.setAttribute(AdminSessionInterceptor.STORE_SESSION_KEY,storeId);
        doReturn(PaymentEnvironment.PRODUCTION).when(efi).environment();doReturn(true).when(efi).configured();doReturn(true).when(efi).planAvailable(any(),eq(PlanType.PREMIUM_ULTRA));
        doReturn(new BigDecimal("59.90")).when(efi).amount(PlanType.PREMIUM_ULTRA);doReturn("3").when(efi).planId(PlanType.PREMIUM_ULTRA);
        doReturn(json.valueToTree(Map.of("subscription_id","991122","status","active","payment_method","credit_card","custom_id","test-upgrade-991122","value",2999,
                "plan",Map.of("plan_id",2,"interval",1),"next_execution",end.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate().toString()))).when(efi).subscriptionDetail("991122");
        doReturn(json.valueToTree(Map.of("charge_id","prior-paid","status","paid","custom_id","test-upgrade-991122","total",2999,
                "created_at",start.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate().toString()+" 12:00:00"))).when(efi).chargeDetail("prior-paid");
        doAnswer(invocation->new GatewaySubscription("991122",changed.get()?"3":"2","test-upgrade-991122","active","BRL",
                new BigDecimal(changed.get()?"59.90":"29.99"),end)).when(efi).getSubscription("991122");
        doReturn(Optional.of(new GatewayInvoice("prior-paid","991122","prior-paid","paid"))).when(efi).getLatestInvoice("991122");
        doReturn("778899").when(efi).createUpgradeCharge(any(),any(),any(),any());
        doNothing().when(efi).payUpgradeCharge(any(),any(),any());
        doAnswer(i->{changed.set(true);return null;}).when(efi).changeSubscriptionPlan(eq("991122"),eq("3"),eq("Ultra"),any());
        doReturn(json.createArrayNode()).when(efi).findUpgradeCharges(any());
    }
    void createQuote(String coupon){
        quote=service.quote(storeId,PlanType.PREMIUM_ULTRA,coupon);
        doAnswer(i->json.valueToTree(Map.of("charge_id","778899","custom_id",quote.reference(),"total",quote.getDueAmount().movePointRight(2).longValueExact(),"status",paymentStatus.get())))
                .when(efi).chargeDetail("778899");
    }

    @Test void exactHalfCycleMathAndCouponAreRoundedInCents(){
        var normal=EfiUpgradeService.calculate(new BigDecimal("29.99"),new BigDecimal("59.90"),new BigDecimal("0.5"),BigDecimal.ZERO);
        assertThat(normal.credit()).isEqualByComparingTo("15.00");assertThat(normal.due()).isEqualByComparingTo("14.95");
        var coupon=EfiUpgradeService.calculate(new BigDecimal("29.99"),new BigDecimal("59.90"),new BigDecimal("0.5"),new BigDecimal("30"));
        assertThat(coupon.due()).isEqualByComparingTo("5.97");assertThat(coupon.discount()).isEqualByComparingTo("8.98");
        assertThat(EfiUpgradeService.calculate(new BigDecimal("29.99"),new BigDecimal("59.90"),BigDecimal.ZERO,new BigDecimal("30")).due()).isZero();
        assertThatThrownBy(()->EfiUpgradeService.calculate(BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("1.1"),BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void successfulAdjustmentKeepsSingleSubscriptionAndDoesNotChargeAgain(){
        createQuote(" brinde ");service.pay(storeId,quote.getId(),payer,token);service.pay(storeId,quote.getId(),payer,token);
        var sub=subscriptions.findByStoreId(storeId).orElseThrow();
        assertThat(sub.getProviderSubscriptionId()).isEqualTo("991122");assertThat(sub.getPlan()).isEqualTo(PlanType.PREMIUM_ULTRA);
        assertThat(sub.getNextPaymentAt()).isEqualTo(end);assertThat(sub.isUpgradePaymentPending()).isFalse();
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.COMPLETED);
        verify(efi,times(1)).createUpgradeCharge(eq(quote.getDueAmount()),eq(quote.reference()),any(),contains("Ajuste proporcional"));
        verify(efi,times(1)).payUpgradeCharge(eq("778899"),eq(payer),eq(token));
        verify(efi,never()).cancel(any());verify(efi,never()).createSubscription(any(),any(),any());
        assertThat(coupons.findByCode("BRINDE").orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(couponUses.findByAdjustmentId(quote.getId()).orElseThrow().getStatus()).isEqualTo(UpgradeCouponUse.Status.USED);
    }
    @Test void refusalPreservesProAndAllowsNewQuote(){
        createQuote("BRINDE");paymentStatus.set("unpaid");service.pay(storeId,quote.getId(),payer,token);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.FAILED);
        assertThat(stores.findByStoreId(storeId).orElseThrow().getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(subscriptions.findByStoreId(storeId).orElseThrow().isUpgradePaymentPending()).isFalse();
        assertThat(coupons.findByCode("BRINDE").orElseThrow().getUsedCount()).isZero();
        service.quote(storeId,PlanType.PREMIUM_ULTRA,null);
        verify(efi,never()).changeSubscriptionPlan(any(),anyString(),anyString(),any());
    }
    @Test void paymentTimeoutIsRecoveredWithoutPayingAgain(){
        createQuote("BRINDE");doThrow(new PaymentGatewayException("timeout")).when(efi).payUpgradeCharge(any(),any(),any());
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(PaymentGatewayException.class);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.PAYMENT_PENDING);
        service.pay(storeId,quote.getId(),payer,token);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.COMPLETED);
        verify(efi,times(1)).createUpgradeCharge(any(),any(),any(),any());verify(efi,times(1)).payUpgradeCharge(any(),any(),any());
    }
    @Test void paidButChangeTimeoutRecoversWithoutAnotherPayment(){
        createQuote(null);doAnswer(i->{changed.set(true);throw new PaymentGatewayException("timeout");}).when(efi).changeSubscriptionPlan(any(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(PaymentGatewayException.class);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.CHANGE_PENDING);
        service.reconcile(quote.getId());
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.COMPLETED);
        verify(efi,times(1)).payUpgradeCharge(any(),any(),any());verify(efi,times(1)).changeSubscriptionPlan(any(),anyString(),anyString(),any());
    }
    @Test void uncertainCreationNeverCreatesSecondCharge(){
        createQuote(null);doThrow(new PaymentGatewayException("timeout")).when(efi).createUpgradeCharge(any(),any(),any(),any());
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(PaymentGatewayException.class);
        service.pay(storeId,quote.getId(),payer,token);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.CREATING);
        verify(efi,times(1)).createUpgradeCharge(any(),any(),any(),any());verify(efi,never()).payUpgradeCharge(any(),any(),any());
    }
    @Test void uncertainCreationCanBeFoundByReferenceWithoutCreatingOrPayingAgain(){
        createQuote(null);doThrow(new PaymentGatewayException("timeout")).when(efi).createUpgradeCharge(any(),any(),any(),any());
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(PaymentGatewayException.class);
        doReturn(json.valueToTree(List.of(Map.of("id",778899)))).when(efi).findUpgradeCharges(quote.reference());
        service.reconcile(quote.getId());
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.COMPLETED);
        verify(efi,times(1)).createUpgradeCharge(any(),any(),any(),any());verify(efi,never()).payUpgradeCharge(any(),any(),any());
    }
    @Test void verifiedWebhookCompletesPaymentAndDuplicatesAreHarmless(){
        createQuote("BRINDE");paymentStatus.set("waiting");service.pay(storeId,quote.getId(),payer,token);
        paymentStatus.set("paid");String notification="test-notification-token-123456";
        doReturn(json.valueToTree(List.of(Map.of("custom_id",quote.reference(),"identifiers",Map.of("charge_id",778899)))))
                .when(efi).notification(notification);
        service.receiveNotification(notification);service.receiveNotification(notification);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.COMPLETED);
        verify(efi,times(1)).createUpgradeCharge(any(),any(),any(),any());verify(efi,times(1)).payUpgradeCharge(any(),any(),any());
    }
    @Test void invalidCouponOrUnpaidCycleCannotCreateCharge(){
        assertThatThrownBy(()->service.quote(storeId,PlanType.PREMIUM_ULTRA,"INVALIDO")).isInstanceOf(IllegalArgumentException.class);
        doReturn(json.valueToTree(Map.of("charge_id","prior-paid","status","unpaid","custom_id","test-upgrade-991122","total",2999,
                "created_at",start.atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate().toString()))).when(efi).chargeDetail("prior-paid");
        assertThatThrownBy(()->service.quote(storeId,PlanType.PREMIUM_ULTRA,"BRINDE")).isInstanceOf(IllegalArgumentException.class);
        verify(efi,never()).createUpgradeCharge(any(),any(),any(),any());
    }
    @Test void backofficeShowsFinancialAttemptsButRequiresAuthentication() throws Exception{
        createQuote(null);paymentStatus.set("waiting");service.pay(storeId,quote.getId(),payer,token);
        mvc.perform(get("/backoffice/payments/upgrades")).andExpect(redirectedUrl("/backoffice/login"));
        var operator=new MockHttpSession();operator.setAttribute(br.com.nuvemcustomfields.config.BackofficeSessionInterceptor.SESSION_KEY,true);
        String html=mvc.perform(get("/backoffice/payments/upgrades").session(operator)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Ajustes de upgrade","PAYMENT_PENDING","778899",quote.reference(),"991122");
    }
    @Test void expiryPriceChangeAndCrossStoreAccessCannotCharge(){
        createQuote(null);
        assertThatThrownBy(()->service.owned(999999L,quote.getId())).isInstanceOf(IllegalArgumentException.class);
        doReturn(new BigDecimal("69.90")).when(efi).amount(PlanType.PREMIUM_ULTRA);
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(IllegalArgumentException.class);
        doReturn(new BigDecimal("59.90")).when(efi).amount(PlanType.PREMIUM_ULTRA);
        quote.setExpiresAt(Instant.now().minusSeconds(1));adjustments.save(quote);
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(IllegalArgumentException.class);
        verify(efi,never()).createUpgradeCharge(any(),any(),any(),any());
    }
    @Test void pendingPaymentBlocksConcurrentUpgradeAndCancellation(){
        createQuote(null);paymentStatus.set("waiting");service.pay(storeId,quote.getId(),payer,token);
        assertThatThrownBy(()->service.quote(storeId,PlanType.PREMIUM_ULTRA,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->payments.cancel(storeId)).isInstanceOf(IllegalArgumentException.class);
        verify(efi,never()).cancel(any());
    }
    @Test void mismatchedChargeNeverReleasesUltra(){
        createQuote(null);doReturn(json.valueToTree(Map.of("charge_id","778899","custom_id",quote.reference(),"total",1,"status","paid"))).when(efi).chargeDetail("778899");
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(PaymentGatewayException.class);
        assertThat(stores.findByStoreId(storeId).orElseThrow().getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        verify(efi,never()).changeSubscriptionPlan(any(),anyString(),anyString(),any());
    }
    @Test void previewPaymentAndHistoryExplainSingleSubscription() throws Exception{
        String preview=mvc.perform(get("/admin/billing/upgrade/efi").param("plan","PREMIUM_ULTRA").param("couponCode","BRINDE").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("uma única assinatura","Crédito do plano", "BRINDE", "Próxima mensalidade", "sem desconto nas próximas");
        assertThat(preview).contains("placeholder=\"CUPOM\"","merchant-upgrade-layout","merchant-upgrade-summary","Pagar hoje — ajuste único");
        assertThat(preview).doesNotContain("placeholder=\"BRINDE\"");
        String previewFile=System.getProperty("upgrade.previewFile");
        if(previewFile!=null){
            String cssBase=java.nio.file.Path.of("src/main/resources/static/styles").toAbsolutePath().toUri().toString();
            String offline=preview.replaceAll("href=\"/styles/([^?\"]+)\\?[^\"]*\"", "href=\""+cssBase+"$1\"");
            java.nio.file.Files.writeString(java.nio.file.Path.of(previewFile),offline);
        }
        quote=adjustments.findByStoreIdOrderByQuotedAtDesc(storeId).getFirst();
        String card=mvc.perform(get("/admin/billing/upgrade/efi/pay").param("id",quote.getId()).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(card).contains("Ajuste proporcional único","Confirmar upgrade e pagar","adjustmentId", "data-single-charge=\"true\"")
                .doesNotContain("name=\"card-number\"");
        doAnswer(i->json.valueToTree(Map.of("charge_id","778899","custom_id",quote.reference(),"total",quote.getDueAmount().movePointRight(2).longValueExact(),"status","paid"))).when(efi).chargeDetail("778899");
        service.pay(storeId,quote.getId(),payer,token);
        String done=mvc.perform(get("/admin/billing/upgrade/efi/processing").param("id",quote.getId()).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(done).contains("Upgrade concluído", "única assinatura", "59,90");
        String billing=mvc.perform(get("/admin/billing").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(billing).contains("Ajustes proporcionais de upgrade","Concluído","não novas assinaturas");
    }
    @Test void fixedCouponNoLongerWorksWithoutAnActiveRegistrationAndErrorStaysOnPreview() throws Exception {
        var c=coupons.findByCode("BRINDE").orElseThrow();c.setEnabled(false);coupons.save(c);
        assertThatThrownBy(()->service.quote(storeId,PlanType.PREMIUM_ULTRA,"BRINDE")).isInstanceOf(IllegalArgumentException.class);
        var page=mvc.perform(get("/admin/billing/upgrade/efi").param("plan","PREMIUM_ULTRA")
                .param("couponCode","BRINDE").session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Cupom inválido ou indisponível","Resumo do upgrade","placeholder=\"CUPOM\"");
        assertThat(page).doesNotContain("Cupom BRINDE aplicado");
    }
    @Test void quoteUsesConfiguredPercentageAndChangedCouponCannotStartPayment(){
        var c=coupons.findByCode("BRINDE").orElseThrow();c.setDiscountPercent(new BigDecimal("10"));coupons.save(c);
        createQuote("BRINDE");assertThat(quote.getCouponPercent()).isEqualByComparingTo("10");
        c.setDiscountPercent(new BigDecimal("20"));coupons.save(c);
        assertThatThrownBy(()->service.pay(storeId,quote.getId(),payer,token)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mudou");
        verify(efi,never()).createUpgradeCharge(any(),any(),any(),any());
        assertThat(coupons.findByCode("BRINDE").orElseThrow().getUsedCount()).isZero();
    }
    @Test void fullDiscountConfirmsUsageWithoutCreatingAnExtraCharge(){
        var c=coupons.findByCode("BRINDE").orElseThrow();c.setDiscountPercent(new BigDecimal("100"));coupons.save(c);
        createQuote("BRINDE");assertThat(quote.getDueAmount()).isZero();
        service.pay(storeId,quote.getId(),null,null);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.COMPLETED);
        assertThat(couponUses.findByAdjustmentId(quote.getId()).orElseThrow().getStatus()).isEqualTo(UpgradeCouponUse.Status.USED);
        verify(efi,never()).createUpgradeCharge(any(),any(),any(),any());
        verify(efi,never()).payUpgradeCharge(any(),any(),any());
    }
}
