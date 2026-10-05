package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.GatewayInvoice;
import br.com.nuvemcustomfields.repository.Ga4OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class Ga4BillingServiceTest {
    Ga4OutboxRepository outbox=mock(Ga4OutboxRepository.class);
    Ga4Client client=mock(Ga4Client.class);
    ObjectMapper mapper=new ObjectMapper();
    Ga4BillingService service=new Ga4BillingService(outbox,client,mapper);
    @BeforeEach void setup(){
        when(client.configured()).thenReturn(true);
        var request=new MockHttpServletRequest();
        request.setParameter("gaClientId","123.456");request.setParameter("gaSessionId","123456789");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
    PaymentSubscription subscription(){
        var sub=new PaymentSubscription();sub.setProvider(PaymentProviderType.EFI);sub.setPlan(PlanType.PREMIUM_PLUS);
        sub.setCurrency("BRL");sub.setAmountValue(new BigDecimal("29.99"));sub.captureAnalytics();return sub;
    }
    UpgradeAdjustment adjustment(){
        var a=new UpgradeAdjustment();a.setEnvironment(PaymentEnvironment.PRODUCTION);
        a.setSourcePlan(PlanType.PREMIUM_PLUS);a.setTargetPlan(PlanType.PREMIUM_ULTRA);
        a.setState(UpgradeAdjustment.State.COMPLETED);a.setDueAmount(new BigDecimal("8.97"));
        a.setRegularAmount(new BigDecimal("59.90"));a.setCouponCode("UPGRADE10");a.captureAnalytics();return a;
    }
    @Test void correlatesConfirmedPurchaseAndKeepsRenewalsOutOfPurchaseFunnel() throws Exception {
        var sub=subscription();
        service.payment(sub,new GatewayInvoice("1","sub","1","paid"));
        service.payment(sub,new GatewayInvoice("2","sub","2","paid"));
        var saved=ArgumentCaptor.forClass(Ga4Outbox.class);verify(outbox,times(2)).save(saved.capture());
        var purchase=mapper.readTree(saved.getAllValues().getFirst().getPayload());
        assertThat(purchase.path("client_id").asText()).isEqualTo("123.456");
        assertThat(purchase.at("/events/0/name").asText()).isEqualTo("purchase");
        assertThat(purchase.at("/events/0/params/session_id").asText()).isEqualTo("123456789");
        var renewal=mapper.readTree(saved.getAllValues().getLast().getPayload());
        assertThat(renewal.at("/events/0/name").asText()).isEqualTo("subscription_renewal");
        assertThat(renewal.at("/events/0/params/session_id").isMissingNode()).isTrue();
    }
    @Test void upgradeUsesAdjustmentRatherThanMonthlyPriceAndIncludesCoupon() throws Exception {
        var a=adjustment();service.upgrade(a);
        var saved=ArgumentCaptor.forClass(Ga4Outbox.class);verify(outbox,times(2)).save(saved.capture());
        var purchase=mapper.readTree(saved.getAllValues().getFirst().getPayload());
        assertThat(purchase.at("/events/0/params/value").decimalValue()).isEqualByComparingTo("8.97");
        assertThat(purchase.at("/events/0/params/items/0/price").decimalValue()).isEqualByComparingTo("8.97");
        assertThat(purchase.at("/events/0/params/recurring_amount").decimalValue()).isEqualByComparingTo("59.90");
        assertThat(purchase.at("/events/0/params/coupon").asText()).isEqualTo("UPGRADE10");
        assertThat(purchase.at("/events/0/params/flow_type").asText()).isEqualTo("upgrade");
    }
    @Test void zeroChargeUpgradeIsRecordedWithZeroRevenue() throws Exception {
        var a=adjustment();a.setDueAmount(BigDecimal.ZERO);service.upgrade(a);
        var saved=ArgumentCaptor.forClass(Ga4Outbox.class);verify(outbox,times(2)).save(saved.capture());
        assertThat(mapper.readTree(saved.getAllValues().getFirst().getPayload()).at("/events/0/params/value").decimalValue()).isZero();
    }
    @Test void skipsPendingSandboxMissingBrowserAndDuplicateEvents(){
        var sub=subscription();service.payment(sub,new GatewayInvoice("1","sub","1","waiting"));
        sub.setProviderEnvironment(PaymentEnvironment.SANDBOX);service.payment(sub,new GatewayInvoice("1","sub","1","paid"));
        var a=adjustment();a.setState(UpgradeAdjustment.State.PAYMENT_PENDING);service.upgrade(a);
        RequestContextHolder.resetRequestAttributes();a=adjustment();service.upgrade(a);
        when(outbox.existsByEventKey(anyString())).thenReturn(true);
        service.payment(subscription(),new GatewayInvoice("1","sub","1","paid"));
        verify(outbox,never()).save(any());
    }
    @Test void couponFirstMonthUsesDiscountedAmountThenFullRenewal() throws Exception {
        var sub=subscription();sub.applyWinbackCoupon("VOLTA",new BigDecimal("29.99"),new BigDecimal("14.99"));
        service.payment(sub,new GatewayInvoice("1","sub","1","paid"));
        service.payment(sub,new GatewayInvoice("2","sub","2","paid"));
        var saved=ArgumentCaptor.forClass(Ga4Outbox.class);verify(outbox,times(2)).save(saved.capture());
        assertThat(mapper.readTree(saved.getAllValues().getFirst().getPayload()).at("/events/0/params/value").decimalValue()).isEqualByComparingTo("14.99");
        assertThat(mapper.readTree(saved.getAllValues().getLast().getPayload()).at("/events/0/params/value").decimalValue()).isEqualByComparingTo("29.99");
    }
    @Test void retriesDeliveryAndDoesNotLogOrCopyProviderErrorsIntoPayload(){
        var e=new Ga4Outbox("key","{}");when(outbox.findDue(any(),any())).thenReturn(List.of(e));
        doThrow(new IllegalStateException("secret-url")).when(client).send(any());service.deliverDue();
        assertThat(e.getAttempts()).isEqualTo(1);assertThat(e.getDeliveredAt()).isNull();
        doNothing().when(client).send(any());service.deliverDue();assertThat(e.getDeliveredAt()).isNotNull();
    }
    @Test void contextRejectsPersonalDataAndTokens(){
        assertThat(Ga4Context.validated("email@example.com","123").clientId()).isNull();
        assertThat(Ga4Context.validated("123.456","token").sessionId()).isNull();
    }
    @Test void planChangeWithoutConfirmedChargeDoesNotCreateRevenue() throws Exception {
        service.planChanged(subscription(),PlanType.PREMIUM);
        var saved=ArgumentCaptor.forClass(Ga4Outbox.class);verify(outbox).save(saved.capture());
        var event=mapper.readTree(saved.getValue().getPayload());
        assertThat(event.at("/events/0/name").asText()).isEqualTo("upgrade_completed");
        assertThat(event.at("/events/0/params/value").isMissingNode()).isTrue();
    }
}
