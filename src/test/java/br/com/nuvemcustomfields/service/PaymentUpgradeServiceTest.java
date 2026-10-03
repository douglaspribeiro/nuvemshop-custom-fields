package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PaymentUpgradeServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    private final PlanEventRepository events = mock(PlanEventRepository.class);
    private final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final Store store = new Store();
    private final PaymentSubscription subscription = new PaymentSubscription();
    private final BigDecimal newAmount = new BigDecimal("59.90");
    private final PaymentSubscriptionService service = new PaymentSubscriptionService(stores, subscriptions, events, router,
            mock(NuvemshopApiClient.class), mock(NuvemshopProperties.class), mock(MercadoPagoProperties.class),
            mock(EfiGateway.class), mock(PaymentNotificationService.class));

    @BeforeEach void setup() {
        store.setStoreId(123L); store.setPlan(PlanType.PREMIUM_PLUS);
        store.setStoreCountryCode("BR"); store.setStoreCurrency("BRL");
        subscription.setStoreId(123L); subscription.setPlan(PlanType.PREMIUM_PLUS);
        subscription.setProvider(PaymentProviderType.EFI); subscription.setProviderSubscriptionId("sub-1");
        subscription.setProviderPriceId("old-price"); subscription.setCurrency("BRL");
        subscription.setAmountValue(new BigDecimal("29.99")); subscription.setExternalReference("ref-1");
        subscription.setStatus(PaymentSubscriptionStatus.ACTIVE); subscription.setAccessActive(true);
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(subscription));
        when(subscriptions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(router.require(PaymentProviderType.EFI)).thenReturn(gateway);
        when(gateway.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        when(gateway.planAvailable(store, PlanType.PREMIUM_ULTRA)).thenReturn(true);
        when(gateway.amount(PlanType.PREMIUM_ULTRA)).thenReturn(newAmount);
        when(gateway.priceId(store, PlanType.PREMIUM_ULTRA)).thenReturn("new-price");
    }

    private GatewaySubscription remote(String price, String amount) {
        return new GatewaySubscription("sub-1", price, "ref-1", "active", "BRL",
                new BigDecimal(amount), Instant.now().plusSeconds(86400));
    }

    @Test void changesExistingSubscriptionOnlyAfterProviderConfirmation() {
        when(gateway.getSubscription("sub-1")).thenReturn(remote("old-price", "29.99"), remote("new-price", "59.90"));
        service.upgrade(123L, PlanType.PREMIUM_ULTRA, newAmount);
        assertThat(subscription.getProviderSubscriptionId()).isEqualTo("sub-1");
        assertThat(subscription.isAccessActive()).isTrue();
        assertThat(subscription.getStatus()).isEqualTo(PaymentSubscriptionStatus.ACTIVE);
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM_ULTRA);
        assertThat(subscription.getUpgradePlan()).isNull();
        verify(gateway).changeSubscriptionPlan("sub-1", store, PlanType.PREMIUM_ULTRA);
        verify(gateway, never()).cancel(anyString());
        verify(gateway, never()).cancelImmediately(anyString());
        verify(gateway, never()).createCheckout(any(), any(), anyString(), anyString());
        verify(events).save(argThat(event -> event.getToPlan() == PlanType.PREMIUM_ULTRA
                && "PAYMENT_UPGRADE".equals(event.getSource())));
    }

    @Test void uncertainResponseRetainsOldAccessAndCanBeReconciledWithoutAnotherUpdate() {
        when(gateway.getSubscription("sub-1")).thenReturn(remote("old-price", "29.99"));
        doThrow(new PaymentGatewayException("timeout")).when(gateway).changeSubscriptionPlan(anyString(), any(), any());
        assertThatThrownBy(() -> service.upgrade(123L, PlanType.PREMIUM_ULTRA, newAmount))
                .isInstanceOf(PaymentGatewayException.class);
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(subscription.isAccessActive()).isTrue();
        assertThat(subscription.getUpgradePlan()).isEqualTo(PlanType.PREMIUM_ULTRA);
        when(gateway.getSubscription("sub-1")).thenReturn(remote("new-price", "59.90"));
        service.reconcile(123L);
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM_ULTRA);
        verify(gateway, times(1)).changeSubscriptionPlan(anyString(), any(), any());
    }

    @Test void rejectsPriceChangesAndDowngradesBeforeUpdatingGateway() {
        assertThatThrownBy(() -> service.upgrade(123L, PlanType.PREMIUM_ULTRA, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.upgrade(123L, PlanType.PREMIUM, newAmount))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gateway, never()).changeSubscriptionPlan(anyString(), any(), any());
    }

    @Test void blocksUpgradeDuringCancellation() {
        subscription.setCancellationPending(true);
        assertThatThrownBy(() -> service.upgrade(123L, PlanType.PREMIUM_ULTRA, newAmount))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @Test void mismatchedRemotePriceCannotGrantUltra() {
        when(gateway.getSubscription("sub-1")).thenReturn(remote("old-price", "29.99"), remote("unexpected-price", "59.90"));
        assertThatThrownBy(() -> service.upgrade(123L, PlanType.PREMIUM_ULTRA, newAmount))
                .isInstanceOf(PaymentGatewayException.class);
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(subscription.getUpgradePlan()).isEqualTo(PlanType.PREMIUM_ULTRA);
    }
}
