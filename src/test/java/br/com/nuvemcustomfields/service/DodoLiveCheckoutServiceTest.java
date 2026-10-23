package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DodoLiveCheckoutServiceTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    final DodoLiveGateway gateway = mock(DodoLiveGateway.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final Store store = new Store();
    PaymentSubscription local;
    PaymentAttempt attempt;
    DodoLiveCheckoutService service;
    @BeforeEach void setup() {
        store.setStoreId(42L); store.setStoreCountryCode("MX");
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(router.requireForStore(store)).thenReturn(gateway); when(gateway.provider()).thenReturn(PaymentProviderType.DODO);
        when(gateway.environment()).thenReturn(PaymentEnvironment.PRODUCTION); when(gateway.planAvailable(store,PlanType.PREMIUM)).thenReturn(true);
        when(gateway.priceId(store,PlanType.PREMIUM)).thenReturn("pdt_test"); when(gateway.currency(store)).thenReturn("USD");
        when(gateway.amount(store,PlanType.PREMIUM)).thenReturn(new BigDecimal("9.99"));
        when(subscriptions.findByStoreId(42L)).thenAnswer(i -> Optional.ofNullable(local));
        when(subscriptions.findByExternalReference(anyString())).thenAnswer(i -> Optional.ofNullable(local));
        when(subscriptions.saveAndFlush(any())).thenAnswer(i -> { local = i.getArgument(0); return local; });
        when(attempts.findByExternalReference(anyString())).thenAnswer(i -> Optional.ofNullable(attempt));
        when(attempts.saveAndFlush(any())).thenAnswer(i -> { attempt = i.getArgument(0); return attempt; });
        var properties = mock(NuvemshopProperties.class); when(properties.appBaseUrl()).thenReturn("https://app.test");
        service = new DodoLiveCheckoutService(stores,subscriptions,attempts,router,gateway,properties,manager);
    }
    @Test void commitsReservationBeforeRemoteCallAndReusesTheSameCheckout() {
        when(gateway.createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString())).thenAnswer(i -> {
            assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CREATING);
            verify(manager).commit(any());
            return new GatewayCheckout(null,"ch_1","https://checkout.dodopayments.com/ch_1","pending");
        });
        assertThat(service.start(42L,PlanType.PREMIUM)).isEqualTo("https://checkout.dodopayments.com/ch_1");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.OPEN);
        assertThat(service.start(42L,PlanType.PREMIUM)).isEqualTo("https://checkout.dodopayments.com/ch_1");
        verify(gateway,times(1)).createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString());
    }
    @Test void unknownResponseKeepsReservationAndBlocksAnotherCharge() {
        when(gateway.createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString())).thenThrow(new PaymentGatewayException("timeout"));
        assertThatThrownBy(() -> service.start(42L,PlanType.PREMIUM)).isInstanceOf(PaymentGatewayException.class);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
        assertThat(local.getExternalReference()).isEqualTo(attempt.getExternalReference());
        when(attempts.findFirstByStoreIdAndStatusInOrderByCreatedAtDesc(eq(42L),any())).thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.start(42L,PlanType.PREMIUM)).hasMessageContaining("conciliada");
        verify(gateway,times(1)).createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString());
    }
    @Test void startsCheckoutWhileTemporaryCourtesyIsStillActive() {
        store.setPremiumBonusStartedAt(java.time.Instant.now().minusSeconds(26 * 86_400));
        store.setPremiumBonusExpiresAt(java.time.Instant.now().plusSeconds(4 * 86_400));
        store.setPremiumBonusPlan(PlanType.PREMIUM);
        when(gateway.planAvailable(store,PlanType.PREMIUM_PLUS)).thenReturn(true);
        when(gateway.priceId(store,PlanType.PREMIUM_PLUS)).thenReturn("pdt_pro");
        when(gateway.amount(store,PlanType.PREMIUM_PLUS)).thenReturn(new BigDecimal("19.99"));
        when(gateway.createCheckout(eq(store),eq(PlanType.PREMIUM_PLUS),anyString(),anyString()))
                .thenReturn(new GatewayCheckout(null,"ch_courtesy","https://checkout.dodopayments.com/ch_courtesy","pending"));

        assertThat(service.start(42L,PlanType.PREMIUM_PLUS)).isEqualTo("https://checkout.dodopayments.com/ch_courtesy");
        assertThat(store.isCourtesyPremium()).isTrue();
    }
    @Test void neverReplacesAnExistingActiveContract() {
        local = new PaymentSubscription(); local.setStoreId(42L); local.setProvider(PaymentProviderType.EFI);
        local.setStatus(PaymentSubscriptionStatus.ACTIVE); local.setAccessActive(true);
        assertThatThrownBy(() -> service.start(42L,PlanType.PREMIUM)).hasMessageContaining("assinatura anterior");
        verify(gateway,never()).createCheckout(any(),any(),anyString(),anyString());
    }

    @Test void retiresOldProviderBeforeReservingDodoLiveCheckout() {
        local = new PaymentSubscription(); local.setStoreId(42L); local.setProvider(PaymentProviderType.PADDLE);
        local.setPlan(PlanType.PREMIUM); local.setStatus(PaymentSubscriptionStatus.PENDING);
        var coordinator = mock(PaymentSubscriptionService.class);
        @SuppressWarnings("unchecked")
        var provider = (org.springframework.beans.factory.ObjectProvider<PaymentSubscriptionService>)
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(coordinator);
        service.setSubscriptionService(provider);
        doAnswer(i -> {
            local.setStatus(PaymentSubscriptionStatus.CANCELED); return null;
        }).when(coordinator).preparePendingCheckout(42L, PlanType.PREMIUM);
        when(gateway.createCheckout(eq(store), eq(PlanType.PREMIUM), anyString(), anyString()))
                .thenReturn(new GatewayCheckout(null, "ch_1", "https://checkout.dodopayments.com/ch_1", "pending"));
        assertThat(service.start(42L, PlanType.PREMIUM)).isEqualTo("https://checkout.dodopayments.com/ch_1");
        var order = inOrder(coordinator, gateway);
        order.verify(coordinator).preparePendingCheckout(42L, PlanType.PREMIUM);
        order.verify(gateway).createCheckout(eq(store), eq(PlanType.PREMIUM), anyString(), anyString());
        assertThat(local.getProvider()).isEqualTo(PaymentProviderType.DODO);
    }
    @Test void selectingAnotherPlanCreatesCheckoutForTheSelectedPlanAndRetainsOldIdentity() {
        local = new PaymentSubscription(); local.setStoreId(42L);
        local.setProvider(PaymentProviderType.DODO); local.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        local.setPlan(PlanType.PREMIUM_PLUS); local.setStatus(PaymentSubscriptionStatus.PENDING);
        local.setCountryCode("AR"); local.setCurrency("USD"); local.setAmountValue(new BigDecimal("19.99"));
        local.setExternalReference("existing-reference"); local.setProviderCheckoutId("existing-checkout");
        local.setCheckoutUrl("https://checkout.dodopayments.com/ch_1");
        when(gateway.createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString()))
                .thenReturn(new GatewayCheckout(null,"new-checkout","https://checkout.test/new-selected-plan","pending"));
        assertThat(service.start(42L,PlanType.PREMIUM)).isEqualTo("https://checkout.test/new-selected-plan");
        assertThat(local.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(local.getExternalReference()).isNotEqualTo("existing-reference");
        assertThat(local.getProviderCheckoutId()).isEqualTo("new-checkout");
        verify(gateway).createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString());
        verify(attempts).saveAndFlush(argThat(a -> "existing-reference".equals(a.getExternalReference())
                && a.getStatus()==PaymentAttemptStatus.REPLACED && a.getPlan()==PlanType.PREMIUM_PLUS
                && "existing-checkout".equals(a.getProviderTransactionId())));
    }
    @Test void pendingAttemptWithoutRemoteResourcesCanStartPurchase() {
        local = new PaymentSubscription(); local.setStoreId(42L);
        local.setProvider(PaymentProviderType.DODO); local.setPlan(PlanType.PREMIUM_PLUS);
        local.setStatus(PaymentSubscriptionStatus.PENDING);
        when(gateway.createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString()))
                .thenReturn(new GatewayCheckout(null,"new-checkout","https://checkout.dodopayments.com/ch_1","pending"));
        assertThat(service.start(42L,PlanType.PREMIUM)).isEqualTo("https://checkout.dodopayments.com/ch_1");
        assertThat(local.getPlan()).isEqualTo(PlanType.PREMIUM);
        verify(gateway).createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString());
    }

}
