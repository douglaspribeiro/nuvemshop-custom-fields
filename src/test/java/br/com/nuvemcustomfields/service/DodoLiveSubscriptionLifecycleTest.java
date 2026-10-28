package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DodoLiveSubscriptionLifecycleTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PaymentCatalogPriceRepository catalog = mock(PaymentCatalogPriceRepository.class);
    final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    final DodoLiveGateway gateway = mock(DodoLiveGateway.class);
    final PaymentNotificationService notifications = mock(PaymentNotificationService.class);
    final PaymentSubscription local = new PaymentSubscription();
    final Store store = new Store();
    final Instant end = Instant.now().plusSeconds(86400);
    final PaymentSubscriptionService service = new PaymentSubscriptionService(stores,subscriptions,mock(PlanEventRepository.class),router,
            mock(NuvemshopApiClient.class),mock(NuvemshopProperties.class),
            new MercadoPagoProperties(false,"https://api.test","","",3,BigDecimal.TEN,BigDecimal.TEN),mock(EfiGateway.class),notifications);
    @BeforeEach void setup() {
        local.setStoreId(42L); local.setProvider(PaymentProviderType.DODO); local.setProviderEnvironment(PaymentEnvironment.PRODUCTION);
        local.setProviderSubscriptionId("sub_1"); local.setProviderCheckoutId("ch_1"); local.setProviderPriceId("prod_1");
        local.setExternalReference("ncf_ref"); local.setPlan(PlanType.PREMIUM); local.setCurrency("USD");
        local.setCountryCode("MX");
        local.setAmountValue(new BigDecimal("9.99")); local.setStatus(PaymentSubscriptionStatus.PENDING);
        store.setStoreId(42L); store.setPlan(PlanType.FREE);
        when(router.require(PaymentProviderType.DODO)).thenReturn(gateway); when(gateway.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        when(subscriptions.findByProviderSubscriptionId("sub_1")).thenReturn(Optional.of(local));
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(local)); when(subscriptions.save(local)).thenReturn(local);
        when(stores.findByStoreId(42L)).thenReturn(Optional.of(store));
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","prod_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","paid")));
        service.setPaymentCatalog(catalog);
        var price = new PaymentCatalogPrice(); price.setProvider(PaymentProviderType.DODO); price.setEnvironment(PaymentEnvironment.PRODUCTION);
        price.setCountryCode("MX"); price.setPlan(PlanType.PREMIUM); price.setProviderPriceId("prod_current"); price.setValidatedAt(Instant.now());
        when(catalog.findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(PaymentProviderType.DODO,PaymentEnvironment.PRODUCTION,"MX",PlanType.PREMIUM)).thenReturn(Optional.of(price));
    }
    @Test void activeEventDoesNotGrantUnpaidAccess() {
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.DODO,"sub_1");
        assertThat(local.isAccessActive()).isFalse(); assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        verify(notifications,never()).enqueue(any(),any());
    }
    @Test void confirmedPaidInvoiceActivatesOnlyMatchingProductAndCurrency() {
        service.synchronizeFromSubscription(PaymentProviderType.DODO,"sub_1");
        assertThat(local.isAccessActive()).isTrue(); assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(local.getCurrentPeriodEnd()).isEqualTo(end); verify(notifications).enqueue(eq(local),any());
    }
    @Test void mismatchedProductCannotGrantAccessButDodoLocalizedCurrencyCan() {
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","prod_foreign","9.99",null));
        assertThatThrownBy(() -> service.synchronizeFromSubscription(PaymentProviderType.DODO,"sub_1")).hasMessageContaining("Produto ou referência Dodo divergente");
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","prod_1","5.36",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(new GatewayInvoice("tran_1","sub_1","tran_1","paid","MXN",new BigDecimal("99.00"))));
        service.synchronizeFromSubscription(PaymentProviderType.DODO,"sub_1");
        assertThat(local.isAccessActive()).isTrue();
    }
    @Test void canceledImmediatelyAndUnpaidRevokeExistingAccess() {
        active(); when(gateway.getSubscription("sub_1")).thenReturn(remote("cancelled","prod_1","9.99",null));
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.DODO,"sub_1"); assertThat(local.isAccessActive()).isFalse();
        active(); when(gateway.getSubscription("sub_1")).thenReturn(remote("paused","prod_1","9.99",null));
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.DODO,"sub_1"); assertThat(local.isAccessActive()).isFalse();
    }
    @Test void scheduledCancellationKeepsConfirmedPaidPeriodEvenWhenPaidWebhookWasLate() {
        when(gateway.getSubscription("sub_1")).thenReturn(remote("scheduled_cancel","prod_1","9.99",end));
        service.synchronizeFromSubscription(PaymentProviderType.DODO,"sub_1");
        assertThat(local.isAccessActive()).isTrue(); assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getNextPaymentAt()).isEqualTo(end);
    }
    @Test void fullRefundRevokesButPartialRefundPreservesAccess() {
        active(); when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","partialRefund")));
        service.synchronizeFromSubscription(PaymentProviderType.DODO,"sub_1"); assertThat(local.isAccessActive()).isTrue();
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","refunded")));
        service.synchronizeFromSubscription(PaymentProviderType.DODO,"sub_1"); assertThat(local.isAccessActive()).isFalse();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"failed", "expired"})
    void terminalRemoteStateClosesLegacyPausedContractWithoutCancelRequest(String status) {
        local.setStatus(PaymentSubscriptionStatus.PAUSED);
        when(gateway.getSubscription("sub_1")).thenReturn(remote(status,"prod_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","failed")));
        service.preparePendingCheckout(42L, PlanType.PREMIUM);
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getProviderStatus()).isEqualTo(status);
        assertThat(local.getLastPaymentStatus()).isEqualTo("failed");
        assertThat(local.isAccessActive()).isFalse();
        verify(gateway,never()).cancel(anyString());
        verify(gateway,never()).cancelImmediately(anyString());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"paused", "past_due", "pending", "active"})
    void recoverableRemoteContractIsNotRetiredToAllowANewCharge(String status) {
        local.setStatus(PaymentSubscriptionStatus.PAUSED);
        when(gateway.getSubscription("sub_1")).thenReturn(remote(status,"prod_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","failed")));
        service.preparePendingCheckout(42L, PlanType.PREMIUM);
        assertThat(local.getStatus()).isNotEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getProviderSubscriptionId()).isEqualTo("sub_1");
        verify(gateway,never()).cancelImmediately(anyString());
    }
    @Test void preflightFailureOrWrongEnvironmentCannotRetireContract() {
        local.setStatus(PaymentSubscriptionStatus.PAUSED);
        when(gateway.getSubscription("sub_1")).thenThrow(new PaymentGatewayException("timeout"));
        assertThatThrownBy(() -> service.preparePendingCheckout(42L, PlanType.PREMIUM)).isInstanceOf(PaymentGatewayException.class);
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PAUSED);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        assertThatThrownBy(() -> service.preparePendingCheckout(42L, PlanType.PREMIUM)).hasMessageContaining("ambiente");
    }
    void active() { local.setStatus(PaymentSubscriptionStatus.ACTIVE); local.setAccessActive(true); local.setNextPaymentAt(end); store.setPlan(PlanType.PREMIUM); }
    GatewaySubscription remote(String status,String product,String amount,Instant cancellation) {
        return new GatewaySubscription("sub_1",null,"ncf_ref",status,"USD",new BigDecimal(amount),end,"cust_1",product,Instant.now(),end,cancellation);
    }
    GatewayInvoice invoice(String id,String status) { return new GatewayInvoice(id,"sub_1",id,status,"USD",new BigDecimal("9.99")); }
}
