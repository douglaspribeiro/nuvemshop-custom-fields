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
        var normal=EfiUpgradeService.calculate(new BigDecimal("29.99"),new BigDecimal("59.90"),new BigDecimal("0.5"),false);
        assertThat(normal.credit()).isEqualByComparingTo("15.00");assertThat(normal.due()).isEqualByComparingTo("14.95");
        var coupon=EfiUpgradeService.calculate(new BigDecimal("29.99"),new BigDecimal("59.90"),new BigDecimal("0.5"),true);
        assertThat(coupon.due()).isEqualByComparingTo("5.97");assertThat(coupon.discount()).isEqualByComparingTo("8.98");
        assertThat(EfiUpgradeService.calculate(new BigDecimal("29.99"),new BigDecimal("59.90"),BigDecimal.ZERO,true).due()).isZero();
        assertThatThrownBy(()->EfiUpgradeService.calculate(BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("1.1"),false)).isInstanceOf(IllegalArgumentException.class);
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
    }
    @Test void refusalPreservesProAndAllowsNewQuote(){
        createQuote(null);paymentStatus.set("unpaid");service.pay(storeId,quote.getId(),payer,token);
        assertThat(adjustments.findById(quote.getId()).orElseThrow().getState()).isEqualTo(UpgradeAdjustment.State.FAILED);
        assertThat(stores.findByStoreId(storeId).orElseThrow().getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(subscriptions.findByStoreId(storeId).orElseThrow().isUpgradePaymentPending()).isFalse();
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
}
