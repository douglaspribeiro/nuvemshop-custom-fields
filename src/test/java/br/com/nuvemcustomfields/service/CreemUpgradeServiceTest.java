package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreemUpgradeServiceTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final CreemGateway gateway = mock(CreemGateway.class);
    final PaymentSubscriptionService synchronization = mock(PaymentSubscriptionService.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final Store store = new Store();
    final PaymentSubscription local = new PaymentSubscription();
    CreemUpgradeService service;
    @BeforeEach void setup() {
        store.setStoreId(42L); store.setPlan(PlanType.PREMIUM);
        local.setStoreId(42L); local.setProvider(PaymentProviderType.CREEM); local.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        local.setPlan(PlanType.PREMIUM); local.setStatus(PaymentSubscriptionStatus.ACTIVE); local.setAccessActive(true);
        local.setProviderSubscriptionId("sub_1"); local.setProviderPriceId("prod_1"); local.setCurrency("USD"); local.setAmountValue(new BigDecimal("9.99"));
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store)); when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(local));
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX); when(gateway.planAvailable(store,PlanType.PREMIUM_PLUS)).thenReturn(true);
        when(gateway.currency(store)).thenReturn("USD"); when(gateway.amount(store,PlanType.PREMIUM_PLUS)).thenReturn(new BigDecimal("19.99"));
        when(gateway.priceId(store,PlanType.PREMIUM_PLUS)).thenReturn("prod_2");
        when(gateway.getSubscription("sub_1")).thenReturn(new GatewaySubscription("sub_1",null,"ncf_ref","active","USD",new BigDecimal("9.99"),Instant.now().plusSeconds(86400),"cust_1","prod_1",Instant.now(),Instant.now().plusSeconds(86400),null));
        @SuppressWarnings("unchecked") ObjectProvider<PaymentSubscriptionService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(synchronization);
        service = new CreemUpgradeService(stores,subscriptions,gateway,provider,manager);
    }
    @Test void commitsPendingTargetBeforeChargingAndWaitsForPaymentSynchronization() {
        doAnswer(i -> {
            assertThat(local.getUpgradePlan()).isEqualTo(PlanType.PREMIUM_PLUS);
            assertThat(local.getPlan()).isEqualTo(PlanType.PREMIUM); verify(manager).commit(any()); return null;
        }).when(gateway).changeSubscriptionPlan("sub_1",store,PlanType.PREMIUM_PLUS);
        service.upgrade(42L,PlanType.PREMIUM_PLUS,new BigDecimal("19.99"));
        verify(synchronization).synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1");
    }
    @Test void unknownOutcomeNeverReplaysTheCharge() {
        doThrow(new PaymentGatewayException("timeout")).when(gateway).changeSubscriptionPlan("sub_1",store,PlanType.PREMIUM_PLUS);
        assertThatThrownBy(() -> service.upgrade(42L,PlanType.PREMIUM_PLUS,new BigDecimal("19.99"))).isInstanceOf(PaymentGatewayException.class);
        assertThat(local.getPlan()).isEqualTo(PlanType.PREMIUM); assertThat(local.getUpgradePlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThatThrownBy(() -> service.upgrade(42L,PlanType.PREMIUM_PLUS,new BigDecimal("19.99"))).hasMessageContaining("Nenhuma nova cobrança");
        verify(gateway,times(1)).changeSubscriptionPlan("sub_1",store,PlanType.PREMIUM_PLUS); verifyNoInteractions(synchronization);
    }
    @Test void changedCurrencyOrUnconfirmedPriceCannotStartUpgrade() {
        when(gateway.currency(store)).thenReturn("EUR");
        assertThatThrownBy(() -> service.upgrade(42L,PlanType.PREMIUM_PLUS,new BigDecimal("19.99"))).hasMessageContaining("moeda");
        when(gateway.currency(store)).thenReturn("USD");
        assertThatThrownBy(() -> service.upgrade(42L,PlanType.PREMIUM_PLUS,BigDecimal.ONE)).hasMessageContaining("preço mudou");
        verify(gateway,never()).changeSubscriptionPlan(anyString(),any(),any());
    }
}
