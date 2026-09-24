package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.MercadoPagoProperties;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentSubscriptionServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    private final PlanEventRepository events = mock(PlanEventRepository.class);
    private final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final NuvemshopProperties nuvemshopProperties = mock(NuvemshopProperties.class);
    private final PaymentSubscriptionService service = new PaymentSubscriptionService(
            stores, subscriptions, events, router, mock(NuvemshopApiClient.class), nuvemshopProperties,
            new MercadoPagoProperties(true, "https://api.example.com", "token", "secret", 3,
                    new BigDecimal("19.99"), new BigDecimal("29.99"))
    );

    @Test
    void activatesPlanOnlyAfterApprovedInvoice() {
        PaymentSubscription local = local();
        Store store = store();
        GatewaySubscription remote = remote();
        when(router.require(PaymentProviderType.MERCADO_PAGO)).thenReturn(gateway);
        when(gateway.getSubscription("sub-1")).thenReturn(remote);
        when(gateway.getLatestInvoice("sub-1")).thenReturn(Optional.of(
                new GatewayInvoice("invoice-1", "sub-1", "payment-1", "approved")));
        when(subscriptions.findByProviderSubscriptionId("sub-1")).thenReturn(Optional.of(local));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.synchronizeFromSubscription(PaymentProviderType.MERCADO_PAGO, "sub-1");

        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.ACTIVE);
        assertThat(local.isAccessActive()).isTrue();
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        verify(events).save(any(PlanEvent.class));
    }

    @Test
    void keepsFreePlanWhileFirstPaymentIsPending() {
        PaymentSubscription local = local();
        Store store = store();
        when(router.require(PaymentProviderType.MERCADO_PAGO)).thenReturn(gateway);
        when(gateway.getSubscription("sub-1")).thenReturn(remote());
        when(gateway.getLatestInvoice("sub-1")).thenReturn(Optional.empty());
        when(subscriptions.findByProviderSubscriptionId("sub-1")).thenReturn(Optional.of(local));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.synchronizeFromSubscription(PaymentProviderType.MERCADO_PAGO, "sub-1");

        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(local.isAccessActive()).isFalse();
        assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        verifyNoInteractions(events);
    }

    @Test
    void replacesPendingCheckoutWhenMerchantUsesAnotherMercadoPagoEmail() {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("nuvemshop@example.com");
        PaymentSubscription pending = local();
        pending.setStatus(PaymentSubscriptionStatus.PENDING);
        pending.setPayerEmail("old@example.com");
        pending.setCheckoutUrl("https://mp.test/old");
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(router.requireForStore(store)).thenReturn(gateway);
        when(router.require(PaymentProviderType.MERCADO_PAGO)).thenReturn(gateway);
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(pending));
        when(gateway.provider()).thenReturn(PaymentProviderType.MERCADO_PAGO);
        when(gateway.amount(PlanType.PREMIUM)).thenReturn(new BigDecimal("19.99"));
        when(nuvemshopProperties.appBaseUrl()).thenReturn("https://app.test");
        when(gateway.createCheckout(eq(store), eq(PlanType.PREMIUM), anyString(),
                eq("https://app.test/admin/billing/return"), eq("payer@example.com")))
                .thenReturn(new GatewayCheckout("sub-2", "https://mp.test/new", "pending"));

        String checkoutUrl = service.startCheckout(123L, PlanType.PREMIUM, " PAYER@EXAMPLE.COM ");

        assertThat(checkoutUrl).isEqualTo("https://mp.test/new");
        assertThat(pending.getPayerEmail()).isEqualTo("payer@example.com");
        assertThat(pending.getProviderSubscriptionId()).isEqualTo("sub-2");
        verify(gateway).cancel("sub-1");
    }

    private PaymentSubscription local() {
        PaymentSubscription value = new PaymentSubscription();
        value.setStoreId(123L);
        value.setProvider(PaymentProviderType.MERCADO_PAGO);
        value.setProviderSubscriptionId("sub-1");
        value.setExternalReference("ncf_123_ref");
        value.setPlan(PlanType.PREMIUM);
        value.setCurrency("BRL");
        value.setAmountValue(new BigDecimal("19.99"));
        return value;
    }

    private Store store() {
        Store value = new Store();
        value.setStoreId(123L);
        value.setPlan(PlanType.FREE);
        return value;
    }

    private GatewaySubscription remote() {
        return new GatewaySubscription("sub-1", "ncf_123_ref", "authorized", "BRL",
                new BigDecimal("19.99"), Instant.now().plusSeconds(86400));
    }
}
