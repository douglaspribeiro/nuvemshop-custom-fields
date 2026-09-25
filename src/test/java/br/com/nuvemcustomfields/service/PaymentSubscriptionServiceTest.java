package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.MercadoPagoProperties;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import br.com.nuvemcustomfields.repository.PlanEventRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PaymentSubscriptionServiceTest {
    private final StoreRepository stores = mock(StoreRepository.class);
    private final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    private final PlanEventRepository events = mock(PlanEventRepository.class);
    private final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final EfiGateway efi = mock(EfiGateway.class);
    private final NuvemshopProperties nuvemshopProperties = mock(NuvemshopProperties.class);
    private final PaymentSubscriptionService service = new PaymentSubscriptionService(
            stores, subscriptions, events, router, mock(NuvemshopApiClient.class), nuvemshopProperties,
            new MercadoPagoProperties(true, "https://api.example.com", "token", "secret", 3,
            new BigDecimal("19.99"), new BigDecimal("29.99")), efi
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
    void activatesEfiPlanOnlyAfterPaidCharge() {
        PaymentSubscription local = local();
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderCheckoutId("plan-1");
        Store store = store();
        GatewaySubscription remote = new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                "active", "BRL", new BigDecimal("19.99"), Instant.now().plusSeconds(86400));
        when(router.require(PaymentProviderType.EFI)).thenReturn(gateway);
        when(gateway.getSubscription("sub-1")).thenReturn(remote);
        when(gateway.getLatestInvoice("sub-1")).thenReturn(Optional.of(
                new GatewayInvoice("charge-1", "sub-1", "charge-1", "paid")));
        when(subscriptions.findByProviderSubscriptionId("sub-1")).thenReturn(Optional.of(local));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.synchronizeFromSubscription(PaymentProviderType.EFI, "sub-1");

        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.ACTIVE);
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        verify(events).save(any(PlanEvent.class));
    }

    @Test
    void reconcilesEfiUsingChargeIdSavedFromPaymentWhenHistoryIsEmpty() {
        PaymentSubscription local = local();
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderCheckoutId("plan-1");
        local.setLastPaymentId("12345");
        Store store = store();
        GatewaySubscription remote = new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                "active", "BRL", new BigDecimal("19.99"), Instant.now().plusSeconds(86400));
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(local));
        when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        when(efi.getSubscription("sub-1")).thenReturn(remote);
        when(efi.getLatestInvoice("sub-1")).thenReturn(Optional.empty());
        when(efi.getCharge("sub-1", "12345"))
                .thenReturn(new GatewayInvoice("12345", "sub-1", "12345", "settled"));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.reconcile(123L);

        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.ACTIVE);
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(local.getLastPaymentStatus()).isEqualTo("settled");
    }

    @Test
    void cancelsQueuedPendingSubscriptionBeforeClearingLocalPendingStatus() {
        PaymentSubscription local = local();
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderCheckoutId("plan-1");
        local.setCancellationPending(true);
        Store store = store();
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(local));
        when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        when(efi.getSubscription("sub-1")).thenReturn(
                new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                        "active", "BRL", new BigDecimal("19.99"), null),
                new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                        "canceled", "BRL", new BigDecimal("19.99"), null));
        when(efi.getLatestInvoice("sub-1")).thenReturn(Optional.empty());
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.reconcile(123L);

        var order = inOrder(efi);
        order.verify(efi).getSubscription("sub-1");
        order.verify(efi).cancel("sub-1");
        order.verify(efi).getSubscription("sub-1");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.isCancellationPending()).isFalse();
        assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
    }

    @Test
    void cancelingPendingPaymentDoesNotRemoveCourtesyPlan() {
        PaymentSubscription local = local();
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderCheckoutId("plan-1");
        local.setCancellationPending(true);
        Store store = store();
        store.setPlan(PlanType.PREMIUM);
        store.setCourtesyPremium(true);
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(local));
        when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        when(efi.getSubscription("sub-1")).thenReturn(
                new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                        "active", "BRL", new BigDecimal("19.99"), null),
                new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                        "canceled", "BRL", new BigDecimal("19.99"), null));
        when(efi.getLatestInvoice("sub-1")).thenReturn(Optional.empty());
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.reconcile(123L);

        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(store.isCourtesyPremium()).isTrue();
        verify(events, never()).save(any(PlanEvent.class));
    }

    @Test
    void queuedCancellationRejectsMismatchedRemoteSubscription() {
        PaymentSubscription local = local();
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderCheckoutId("plan-1");
        local.setCancellationPending(true);
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(local));
        when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        when(efi.getSubscription("sub-1")).thenReturn(new GatewaySubscription("sub-1", "plan-1", "another-store",
                "active", "BRL", new BigDecimal("19.99"), null));

        assertThatThrownBy(() -> service.reconcile(123L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Referencia externa");
        verify(efi, never()).cancel(anyString());
        assertThat(local.isCancellationPending()).isTrue();
    }

    @Test
    void queuedCancellationStaysPendingUntilRemoteConfirmsCancellation() {
        PaymentSubscription local = local();
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderCheckoutId("plan-1");
        local.setCancellationPending(true);
        GatewaySubscription active = new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                "active", "BRL", new BigDecimal("19.99"), null);
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(local));
        when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        when(efi.getSubscription("sub-1")).thenReturn(active);

        assertThatThrownBy(() -> service.reconcile(123L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Aguardando a confirmação");
        verify(efi).cancel("sub-1");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(local.isCancellationPending()).isTrue();
    }

    @Test
    void marksRejectedFirstEfiChargeAsErrorWithoutActivatingPlan() {
        PaymentSubscription local = local();
        local.setProvider(PaymentProviderType.EFI);
        local.setProviderCheckoutId("plan-1");
        Store store = store();
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(local));
        when(router.require(PaymentProviderType.EFI)).thenReturn(efi);
        when(efi.getSubscription("sub-1")).thenReturn(new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref",
                "active", "BRL", new BigDecimal("19.99"), Instant.now().plusSeconds(86400)));
        when(efi.getLatestInvoice("sub-1")).thenReturn(Optional.of(
                new GatewayInvoice("12345", "sub-1", "12345", "unpaid")));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.reconcile(123L);

        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.ERROR);
        assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        verifyNoInteractions(events);
    }

    @Test
    void activatesEfiPlanFromDocumentedPaymentResponse() throws Exception {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("loja@example.com");
        AtomicReference<PaymentSubscription> saved = new AtomicReference<>();
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.saveAndFlush(any(PaymentSubscription.class))).thenAnswer(invocation -> {
            PaymentSubscription subscription = invocation.getArgument(0);
            saved.set(subscription);
            return subscription;
        });
        when(subscriptions.save(any(PaymentSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(efi.configured()).thenReturn(true);
        when(efi.supports(store)).thenReturn(true);
        when(efi.amount(PlanType.PREMIUM)).thenReturn(new BigDecimal("19.99"));
        when(efi.planId(PlanType.PREMIUM)).thenReturn("72050");
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(), anyString())).thenReturn("sub-1");
        when(efi.pay(eq("sub-1"), any(), eq("12345678901234567890"))).thenReturn(
                new ObjectMapper().readTree("{\"charge_id\":12345,\"status\":\"waiting\",\"payment\":\"credit_card\"}"));
        when(efi.getSubscription("sub-1")).thenAnswer(invocation ->
                new GatewaySubscription("sub-1", "72050", saved.get().getExternalReference(),
                        "active", "BRL", new BigDecimal("19.99"), Instant.now().plusSeconds(86400)));
        when(efi.getLatestInvoice("sub-1")).thenReturn(Optional.of(
                new GatewayInvoice("12345", "sub-1", "12345", "paid")));

        service.payWithEfi(123L, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Cliente Teste", "12345678901", "cliente@example.com",
                        "11999999999", "1990-01-01"), "12345678901234567890");

        assertThat(saved.get().getStatus()).isEqualTo(PaymentSubscriptionStatus.ACTIVE);
        assertThat(saved.get().getLastPaymentId()).isEqualTo("12345");
        assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        verify(events).save(any(PlanEvent.class));
    }

    @Test
    void keepsCheckoutPendingWhenAcceptedPaymentHasNoChargeYet() throws Exception {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("loja@example.com");
        AtomicReference<PaymentSubscription> saved = new AtomicReference<>();
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.saveAndFlush(any(PaymentSubscription.class))).thenAnswer(invocation -> {
            PaymentSubscription subscription = invocation.getArgument(0);
            saved.set(subscription);
            return subscription;
        });
        when(subscriptions.save(any(PaymentSubscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(efi.configured()).thenReturn(true);
        when(efi.supports(store)).thenReturn(true);
        when(efi.amount(PlanType.PREMIUM)).thenReturn(new BigDecimal("19.99"));
        when(efi.planId(PlanType.PREMIUM)).thenReturn("72050");
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(), anyString())).thenReturn("sub-1");
        when(efi.pay(eq("sub-1"), any(), eq("12345678901234567890"))).thenReturn(
                new ObjectMapper().readTree("{\"subscription_id\":\"sub-1\",\"status\":\"active\"}"));
        when(efi.getSubscription("sub-1")).thenAnswer(invocation ->
                new GatewaySubscription("sub-1", "72050", saved.get().getExternalReference(),
                        "active", "BRL", new BigDecimal("19.99"), Instant.now().plusSeconds(86400)));
        when(efi.getLatestInvoice("sub-1")).thenReturn(Optional.empty());

        service.payWithEfi(123L, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Cliente Teste", "12345678901", "cliente@example.com",
                        "11999999999", "1990-01-01"), "12345678901234567890");

        assertThat(saved.get().getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(saved.get().getLastError()).isNull();
        assertThat(saved.get().getProviderSubscriptionId()).isEqualTo("sub-1");
        verify(efi, times(1)).pay(eq("sub-1"), any(), anyString());
        verifyNoInteractions(events);
    }

    @Test
    void doesNotReportCardFailureWhenReadAfterAcceptedPaymentFails() throws Exception {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("loja@example.com");
        AtomicReference<PaymentSubscription> saved = new AtomicReference<>();
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(subscriptions.saveAndFlush(any(PaymentSubscription.class))).thenAnswer(invocation -> {
            PaymentSubscription subscription = invocation.getArgument(0);
            saved.set(subscription);
            return subscription;
        });
        when(efi.configured()).thenReturn(true);
        when(efi.supports(store)).thenReturn(true);
        when(efi.amount(PlanType.PREMIUM)).thenReturn(new BigDecimal("19.99"));
        when(efi.planId(PlanType.PREMIUM)).thenReturn("72050");
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(), anyString())).thenReturn("sub-1");
        when(efi.pay(eq("sub-1"), any(), eq("12345678901234567890"))).thenReturn(
                new ObjectMapper().readTree("{\"subscription_id\":\"sub-1\",\"status\":\"active\",\"charge\":{\"id\":12345,\"status\":\"waiting\"}}"));
        when(efi.getSubscription("sub-1")).thenThrow(new PaymentGatewayException("Consulta temporariamente indisponível"));

        service.payWithEfi(123L, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Cliente Teste", "12345678901", "cliente@example.com",
                        "11999999999", "1990-01-01"), "12345678901234567890");

        assertThat(saved.get().getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(saved.get().getProviderSubscriptionId()).isEqualTo("sub-1");
        assertThat(saved.get().getLastError()).isNull();
        verify(efi, times(1)).pay(eq("sub-1"), any(), anyString());
    }

    @Test
    void sendsPublicEfi3NotificationUrlWhenCreatingSubscription() {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("loja@example.com");
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(efi.configured()).thenReturn(true);
        when(efi.supports(store)).thenReturn(true);
        when(efi.amount(PlanType.PREMIUM)).thenReturn(new BigDecimal("19.99"));
        when(nuvemshopProperties.appBaseUrl()).thenReturn("https://campos-personalizados.wzhub.pro");
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(),
                eq("https://campos-personalizados.wzhub.pro/prod/webhooks/efi3")))
                .thenThrow(new PaymentGatewayException("Efí indisponível"));

        assertThatThrownBy(() -> service.payWithEfi(123L, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Cliente Teste", "12345678901", "cliente@example.com",
                        "11999999999", "1990-01-01"), "12345678901234567890"))
                .isInstanceOf(PaymentGatewayException.class);

        verify(efi).createSubscription(eq(PlanType.PREMIUM), anyString(),
                eq("https://campos-personalizados.wzhub.pro/prod/webhooks/efi3"));
    }

    @Test
    void replacesPendingMercadoPagoCheckoutWhenLegacyGatewayIsDisabled() {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("loja@example.com");
        PaymentSubscription pending = local();
        pending.setProviderSubscriptionId(null);
        pending.setProviderCheckoutId("old-mp-plan");
        pending.setStatus(PaymentSubscriptionStatus.PENDING);
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(pending));
        when(efi.configured()).thenReturn(true);
        when(efi.supports(store)).thenReturn(true);
        when(efi.amount(PlanType.PREMIUM)).thenReturn(new BigDecimal("19.99"));
        when(nuvemshopProperties.appBaseUrl()).thenReturn("https://app.example.com");
        when(efi.createSubscription(eq(PlanType.PREMIUM), anyString(),
                eq("https://app.example.com/prod/webhooks/efi3")))
                .thenThrow(new PaymentGatewayException("Falha simulada da Efí"));

        assertThatThrownBy(() -> service.payWithEfi(123L, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Cliente Teste", "12345678901", "cliente@example.com",
                        "11999999999", "1990-01-01"), "12345678901234567890"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessage("Falha simulada da Efí");

        assertThat(pending.getProvider()).isEqualTo(PaymentProviderType.EFI);
        assertThat(pending.getProviderCheckoutId()).isNull();
        verify(router, never()).require(PaymentProviderType.MERCADO_PAGO);
        verify(efi).createSubscription(eq(PlanType.PREMIUM), anyString(), anyString());
    }

    @Test
    void blocksMigrationWhenPendingMercadoPagoSubscriptionCannotBeChecked() {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("loja@example.com");
        PaymentSubscription pending = local();
        pending.setStatus(PaymentSubscriptionStatus.PENDING);
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(pending));
        when(efi.configured()).thenReturn(true);
        when(efi.supports(store)).thenReturn(true);

        assertThatThrownBy(() -> service.payWithEfi(123L, PlanType.PREMIUM,
                new EfiGateway.EfiPayer("Cliente Teste", "12345678901", "cliente@example.com",
                        "11999999999", "1990-01-01"), "12345678901234567890"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("assinatura pendente no provedor anterior");

        verify(efi, never()).createSubscription(any(), anyString(), anyString());
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
    void replacesLegacyPendingSubscriptionWithCheckoutPlanWithoutPayerEmail() {
        Store store = store();
        store.setStoreCountryCode("BR");
        store.setStoreCurrency("BRL");
        store.setStoreEmail("nuvemshop@example.com");
        PaymentSubscription pending = local();
        pending.setStatus(PaymentSubscriptionStatus.PENDING);
        pending.setCheckoutUrl("https://mp.test/old");
        when(stores.findActiveByStoreIdForUpdate(123L)).thenReturn(Optional.of(store));
        when(router.requireForStore(store)).thenReturn(gateway);
        when(router.require(PaymentProviderType.MERCADO_PAGO)).thenReturn(gateway);
        when(subscriptions.findByStoreId(123L)).thenReturn(Optional.of(pending));
        when(gateway.provider()).thenReturn(PaymentProviderType.MERCADO_PAGO);
        when(gateway.amount(PlanType.PREMIUM)).thenReturn(new BigDecimal("19.99"));
        when(nuvemshopProperties.appBaseUrl()).thenReturn("https://app.test");
        when(gateway.createCheckout(eq(store), eq(PlanType.PREMIUM), anyString(),
                eq("https://app.test/admin/billing/return")))
                .thenReturn(new GatewayCheckout(null, "plan-2", "https://mp.test/new", "active"));

        String checkoutUrl = service.startCheckout(123L, PlanType.PREMIUM);

        assertThat(checkoutUrl).isEqualTo("https://mp.test/new");
        assertThat(pending.getPayerEmail()).isNull();
        assertThat(pending.getProviderSubscriptionId()).isNull();
        assertThat(pending.getProviderCheckoutId()).isEqualTo("plan-2");
        verify(gateway).cancel("sub-1");
    }

    @Test
    void associatesWebhookSubscriptionByCheckoutPlanId() {
        PaymentSubscription local = local();
        local.setProviderSubscriptionId(null);
        local.setProviderCheckoutId("plan-1");
        Store store = store();
        GatewaySubscription remote = remote();
        when(router.require(PaymentProviderType.MERCADO_PAGO)).thenReturn(gateway);
        when(gateway.getSubscription("sub-1")).thenReturn(remote);
        when(gateway.getLatestInvoice("sub-1")).thenReturn(Optional.empty());
        when(subscriptions.findByProviderSubscriptionId("sub-1")).thenReturn(Optional.empty());
        when(subscriptions.findByProviderCheckoutId("plan-1")).thenReturn(Optional.of(local));
        when(stores.findByStoreId(123L)).thenReturn(Optional.of(store));
        when(subscriptions.save(local)).thenReturn(local);

        service.synchronizeFromSubscription(PaymentProviderType.MERCADO_PAGO, "sub-1");

        assertThat(local.getProviderSubscriptionId()).isEqualTo("sub-1");
        assertThat(local.getProviderCheckoutId()).isEqualTo("plan-1");
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
        return new GatewaySubscription("sub-1", "plan-1", "ncf_123_ref", "authorized", "BRL",
                new BigDecimal("19.99"), Instant.now().plusSeconds(86400));
    }
}
