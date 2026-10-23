package br.com.nuvemcustomfields.service;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.entity.DlocalGoOperation.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DlocalGoUpgradeServiceTest {
    final DlocalGoOperationRepository operations=mock(DlocalGoOperationRepository.class);
    final PaymentSubscriptionRepository subscriptions=mock(PaymentSubscriptionRepository.class);
    final StoreRepository stores=mock(StoreRepository.class);
    final DlocalGoGateway gateway=mock(DlocalGoGateway.class);
    final DlocalGoFinancialService finances=mock(DlocalGoFinancialService.class);
    final PaymentSubscriptionService payments=mock(PaymentSubscriptionService.class);
    final PaymentNotificationService notifications=mock(PaymentNotificationService.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final Map<String,DlocalGoOperation> saved=new LinkedHashMap<>();
    final Store store=new Store(); final PaymentSubscription sub=new PaymentSubscription();
    final Instant start=Instant.now().minusSeconds(15*86400),end=Instant.now().plusSeconds(15*86400);
    DlocalGoUpgradeService service;
    @BeforeEach void setup() {
        store.setStoreId(42L);store.setStoreCountryCode("AR");store.setPlan(PlanType.PREMIUM);
        sub.setStoreId(42L);sub.setProvider(PaymentProviderType.DLOCAL_GO);sub.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        sub.setProviderSubscriptionId("20:3");sub.setProviderCheckoutId("20");sub.setProviderPriceId("10");sub.setExternalReference("ncf_ref");
        sub.setPlan(PlanType.PREMIUM);sub.setCurrency("ARS");sub.setAmountValue(new BigDecimal("100"));sub.setAccessActive(true);
        sub.setStatus(PaymentSubscriptionStatus.ACTIVE);sub.setLastPaymentId("ST-1");
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(sub));
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);when(gateway.planAvailable(store,PlanType.PREMIUM_PLUS)).thenReturn(true);
        when(gateway.amount(store,PlanType.PREMIUM_PLUS)).thenReturn(new BigDecimal("200"));when(gateway.priceId(store,PlanType.PREMIUM_PLUS)).thenReturn("11");
        when(gateway.getSubscription("20:3")).thenReturn(remote("100"));
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.of(new GatewayInvoice("20:3:1","20:3","ST-1","paid","ARS",new BigDecimal("100"))));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(operations.save(any())).thenAnswer(i -> {DlocalGoOperation op=i.getArgument(0);saved.put(op.getId(),op);return op;});
        when(operations.findById(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(operations.lockById(anyString())).thenAnswer(i -> Optional.ofNullable(saved.get(i.getArgument(0))));
        when(operations.findByStoreIdOrderByCreatedAtDesc(42L)).thenAnswer(i -> new ArrayList<>(saved.values()));
        var config=mock(NuvemshopProperties.class);when(config.appBaseUrl()).thenReturn("https://app.test");
        @SuppressWarnings("unchecked") ObjectProvider<PaymentSubscriptionService> provider=mock(ObjectProvider.class);when(provider.getObject()).thenReturn(payments);
        service=new DlocalGoUpgradeService(operations,subscriptions,stores,gateway,finances,provider,config,notifications,manager);
    }
    GatewaySubscription remote(String amount){return new GatewaySubscription("20:3","20","ncf_ref","active","ARS",new BigDecimal(amount),end,null,null,start,end,null);}
    @Test void quoteChargesOnlyUnusedDifferenceAndDoesNotChangeContract() {
        var op=service.quote(42L,PlanType.PREMIUM_PLUS);
        assertThat(op.getDueAmount()).isBetween(new BigDecimal("49.99"),new BigDecimal("50.00"));
        assertThat(sub.getPlan()).isEqualTo(PlanType.PREMIUM);verify(gateway,never()).updateExclusivePlan(any(),any(),any(),any());
    }
    @Test void rejectsOtherGatewayOrCurrencyBeforeAnyRemoteOperation() {
        sub.setProvider(PaymentProviderType.EFI);sub.setCurrency("BRL");
        assertThatThrownBy(() -> service.quote(42L,PlanType.PREMIUM_PLUS)).isInstanceOf(IllegalArgumentException.class);
        verify(gateway,never()).getSubscription(any());verify(operations,never()).save(any());
    }
    @Test void timeoutNeverReplaysPaymentCreationAndKeepsOriginalPlan() {
        var op=service.quote(42L,PlanType.PREMIUM_PLUS);
        when(gateway.createAdjustment(any(),any(),any(),any())).thenThrow(new PaymentGatewayException("timeout"));
        assertThatThrownBy(() -> service.pay(42L,op.getId())).isInstanceOf(PaymentGatewayException.class);
        assertThat(op.getState()).isEqualTo(State.CREATING);assertThat(sub.isUpgradePaymentPending()).isTrue();
        assertThat(service.pay(42L,op.getId())).isNull();verify(gateway,times(1)).createAdjustment(any(),any(),any(),any());
        assertThat(sub.getPlan()).isEqualTo(PlanType.PREMIUM);
    }
    @Test void paidAdjustmentUpdatesOnlyItsExclusivePlanAndThenReleasesUpgrade() throws Exception {
        var op=service.quote(42L,PlanType.PREMIUM_PLUS);op.setPaymentId("DP-1");op.setState(State.PENDING);sub.setUpgradePaymentPending(true);
        when(gateway.payment("DP-1")).thenReturn(new ObjectMapper().readTree("{\"id\":\"DP-1\",\"order_id\":\""+op.reference()+"\",\"country\":\"AR\",\"currency\":\"ARS\",\"amount\":"+op.getDueAmount()+",\"status\":\"PAID\"}"));
        when(finances.paymentStatus(op.reference(),"ARS",op.getDueAmount())).thenReturn("paid");
        service.reconcile(op.getId());
        verify(gateway).updateExclusivePlan("20","ncf_ref",new BigDecimal("100"),new BigDecimal("200"));
        verify(payments).reconcile(42L);assertThat(sub.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(sub.isUpgradePaymentPending()).isFalse();assertThat(op.getState()).isEqualTo(State.COMPLETED);
    }
    @Test void refundedAdjustmentRestoresOriginalPlanWithoutTouchingNewerCycles() throws Exception {
        var op=service.quote(42L,PlanType.PREMIUM_PLUS);op.setPaymentId("DP-1");op.setState(State.COMPLETED);
        sub.setPlan(PlanType.PREMIUM_PLUS);sub.setAmountValue(new BigDecimal("200"));
        when(gateway.payment("DP-1")).thenReturn(new ObjectMapper().readTree("{\"id\":\"DP-1\",\"order_id\":\""+op.reference()+"\",\"country\":\"AR\",\"currency\":\"ARS\",\"amount\":"+op.getDueAmount()+",\"status\":\"PAID\"}"));
        when(finances.paymentStatus(op.reference(),"ARS",op.getDueAmount())).thenReturn("refunded");
        service.reconcile(op.getId());assertThat(sub.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(op.getState()).isEqualTo(State.REVERSED);verify(gateway).updateExclusivePlan("20","ncf_ref",new BigDecimal("200"),new BigDecimal("100"));
    }
    @Test void dlocalInAnotherCurrencyIsRejectedBeforeAnyRemoteOperation() {
        sub.setCurrency("USD");
        assertThatThrownBy(() -> service.quote(42L,PlanType.PREMIUM_PLUS)).isInstanceOf(IllegalArgumentException.class);
        verify(gateway,never()).getSubscription(any());
    }
    @Test void anotherStoreCannotUseOrPayAQuotedUpgrade() {
        var op=service.quote(42L,PlanType.PREMIUM_PLUS);
        assertThatThrownBy(() -> service.owned(99L,op.getId())).isInstanceOf(IllegalArgumentException.class);
        verify(gateway,never()).createAdjustment(any(),any(),any(),any());
    }
    @Test void favorableChargebackResolutionCanReapplyTheUpgradeInTheSamePaidCycle() throws Exception {
        var op=service.quote(42L,PlanType.PREMIUM_PLUS);op.setPaymentId("DP-1");op.setState(State.REVERSED);
        when(gateway.payment("DP-1")).thenReturn(new ObjectMapper().readTree("{\"id\":\"DP-1\",\"order_id\":\""+op.reference()+"\",\"country\":\"AR\",\"currency\":\"ARS\",\"amount\":"+op.getDueAmount()+",\"status\":\"PAID\"}"));
        when(finances.paymentStatus(op.reference(),"ARS",op.getDueAmount())).thenReturn("paid");
        service.reconcile(op.getId());assertThat(op.getState()).isEqualTo(State.COMPLETED);
        assertThat(sub.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
    }

}
