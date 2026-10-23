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

class StripeCheckoutServiceTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    final StripeGateway gateway = mock(StripeGateway.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    final Store store = new Store();
    PaymentSubscription local;
    PaymentAttempt attempt;
    StripeCheckoutService service;
    @BeforeEach void setup() {
        store.setStoreId(42L); store.setStoreCountryCode("AR");
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(manager.getTransaction(any())).thenAnswer(i -> new SimpleTransactionStatus());
        when(router.requireForStore(store)).thenReturn(gateway); when(gateway.provider()).thenReturn(PaymentProviderType.STRIPE);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX); when(gateway.planAvailable(store,PlanType.PREMIUM)).thenReturn(true);
        when(gateway.priceId(store,PlanType.PREMIUM)).thenReturn("price_1"); when(gateway.currency(store)).thenReturn("USD");
        when(gateway.amount(store,PlanType.PREMIUM)).thenReturn(new BigDecimal("9.99"));
        when(subscriptions.findByStoreId(42L)).thenAnswer(i -> Optional.ofNullable(local));
        when(subscriptions.findByExternalReference(anyString())).thenAnswer(i -> Optional.ofNullable(local));
        when(subscriptions.saveAndFlush(any())).thenAnswer(i -> { local = i.getArgument(0); return local; });
        when(attempts.findByExternalReference(anyString())).thenAnswer(i -> Optional.ofNullable(attempt));
        when(attempts.saveAndFlush(any())).thenAnswer(i -> { attempt = i.getArgument(0); return attempt; });
        var properties = mock(NuvemshopProperties.class); when(properties.appBaseUrl()).thenReturn("https://app.test");
        service = new StripeCheckoutService(stores,subscriptions,attempts,router,gateway,properties,manager);
    }
    @Test void commitsReservationBeforeRemoteCallAndReusesTheSameCheckout() {
        when(gateway.createCheckout(eq(store),eq(PlanType.PREMIUM),anyString(),anyString())).thenAnswer(i -> {
            assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
            assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CREATING);
            verify(manager).commit(any());
            return new GatewayCheckout(null,"ch_1","https://checkout.stripe.com/ch_1","pending");
        });
        assertThat(service.start(42L,PlanType.PREMIUM)).isEqualTo("https://checkout.stripe.com/ch_1");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.OPEN);
        assertThat(service.start(42L,PlanType.PREMIUM)).isEqualTo("https://checkout.stripe.com/ch_1");
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
    @Test void neverReplacesAnExistingActiveContract() {
        local = new PaymentSubscription(); local.setStoreId(42L); local.setProvider(PaymentProviderType.EFI);
        local.setStatus(PaymentSubscriptionStatus.ACTIVE); local.setAccessActive(true);
        assertThatThrownBy(() -> service.start(42L,PlanType.PREMIUM)).hasMessageContaining("assinatura anterior");
        verify(gateway,never()).createCheckout(any(),any(),anyString(),anyString());
    }

    @Test void retiresOldProviderBeforeReservingStripeCheckout() {
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
                .thenReturn(new GatewayCheckout(null, "ch_1", "https://checkout.stripe.com/ch_1", "pending"));
        assertThat(service.start(42L, PlanType.PREMIUM)).isEqualTo("https://checkout.stripe.com/ch_1");
        var order = inOrder(coordinator, gateway);
        order.verify(coordinator).preparePendingCheckout(42L, PlanType.PREMIUM);
        order.verify(gateway).createCheckout(eq(store), eq(PlanType.PREMIUM), anyString(), anyString());
        assertThat(local.getProvider()).isEqualTo(PaymentProviderType.STRIPE);
    }
}
