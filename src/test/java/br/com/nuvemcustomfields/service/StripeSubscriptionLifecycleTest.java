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

class StripeSubscriptionLifecycleTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    final StripeGateway gateway = mock(StripeGateway.class);
    final PaymentNotificationService notifications = mock(PaymentNotificationService.class);
    final PaymentSubscription local = new PaymentSubscription();
    final Store store = new Store();
    final Instant end = Instant.now().plusSeconds(86400);
    final PaymentSubscriptionService service = new PaymentSubscriptionService(stores,subscriptions,mock(PlanEventRepository.class),router,
            mock(NuvemshopApiClient.class),mock(NuvemshopProperties.class),
            new MercadoPagoProperties(false,"https://api.test","","",3,BigDecimal.TEN,BigDecimal.TEN),mock(EfiGateway.class),notifications);
    @BeforeEach void setup() {
        local.setStoreId(42L); local.setProvider(PaymentProviderType.STRIPE); local.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        local.setProviderSubscriptionId("sub_1"); local.setProviderCheckoutId("ch_1"); local.setProviderPriceId("price_1");
        local.setExternalReference("ncf_ref"); local.setPlan(PlanType.PREMIUM); local.setCurrency("USD");
        local.setAmountValue(new BigDecimal("9.99")); local.setStatus(PaymentSubscriptionStatus.PENDING);
        store.setStoreId(42L); store.setPlan(PlanType.FREE);
        when(router.require(PaymentProviderType.STRIPE)).thenReturn(gateway); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(gateway.graceDays()).thenReturn(3);
        when(subscriptions.findByProviderSubscriptionId("sub_1")).thenReturn(Optional.of(local));
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(local)); when(subscriptions.save(local)).thenReturn(local);
        when(stores.findByStoreId(42L)).thenReturn(Optional.of(store));
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","price_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","paid")));
    }
    @Test void activeEventDoesNotGrantUnpaidAccess() {
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.isAccessActive()).isFalse(); assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        verify(notifications,never()).enqueue(any(),any());
    }
    @Test void confirmedPaidInvoiceActivatesOnlyMatchingProductAndCurrency() {
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.isAccessActive()).isTrue(); assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(local.getCurrentPeriodEnd()).isEqualTo(end); verify(notifications).enqueue(eq(local),any());
    }
    @Test void mismatchedProductOrPaymentCurrencyCannotGrantAccess() {
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","price_foreign","9.99",null));
        assertThatThrownBy(() -> service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1")).hasMessageContaining("Preço ou referência Stripe divergente");
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","price_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(new GatewayInvoice("tran_1","sub_1","tran_1","paid","EUR",BigDecimal.TEN)));
        assertThatThrownBy(() -> service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1")).hasMessageContaining("Pagamento Stripe divergente");
        assertThat(local.isAccessActive()).isFalse();
    }
    @Test void canceledImmediatelyAndUnpaidRevokeExistingAccess() {
        active(); when(gateway.getSubscription("sub_1")).thenReturn(remote("canceled","price_1","9.99",null));
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.STRIPE,"sub_1"); assertThat(local.isAccessActive()).isFalse();
        active(); when(gateway.getSubscription("sub_1")).thenReturn(remote("unpaid","price_1","9.99",null));
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.STRIPE,"sub_1"); assertThat(local.isAccessActive()).isFalse();
    }
    @Test void scheduledCancellationKeepsConfirmedPaidPeriodEvenWhenPaidWebhookWasLate() {
        when(gateway.getSubscription("sub_1")).thenReturn(remote("scheduled_cancel","price_1","9.99",end));
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.isAccessActive()).isTrue(); assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getNextPaymentAt()).isEqualTo(end);
    }
    @Test void fullRefundRevokesButPartialRefundPreservesAccess() {
        active(); when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","partialRefund")));
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1"); assertThat(local.isAccessActive()).isTrue();
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","refunded")));
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1"); assertThat(local.isAccessActive()).isFalse();
    }
    @Test void activeSubscriptionWithoutPaidInvoiceCannotGrantAccess() {
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.empty());
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.isAccessActive()).isFalse(); assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
    }
    @Test void incompleteCheckoutRemainsPendingUntilItExpiresAndThenAllowsANewAttempt() {
        when(gateway.getSubscription("sub_1")).thenReturn(remote("incomplete","price_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("in_1","open")));
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING); assertThat(local.isAccessActive()).isFalse();
        when(gateway.getSubscription("sub_1")).thenReturn(remote("incomplete_expired","price_1","9.99",null));
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED); assertThat(local.getCheckoutUrl()).isNull();
    }
    @Test void failedRenewalExpiresAtTheConfiguredGracePeriod() {
        active(); when(gateway.graceDays()).thenReturn(0);
        when(gateway.getSubscription("sub_1")).thenReturn(remote("past_due","price_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("in_renewal","open")));
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.isAccessActive()).isFalse(); assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PAST_DUE);
    }
    @Test void fullRefundRevokesEvenWhenCancellationIsScheduledInTheFuture() {
        active(); when(gateway.getSubscription("sub_1")).thenReturn(remote("active","price_1","9.99",end));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("in_refund","refunded")));
        service.synchronizeFromSubscription(PaymentProviderType.STRIPE,"sub_1");
        assertThat(local.isAccessActive()).isFalse(); assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
    }
    @Test void webhookRecoversCheckoutWithUnknownResponseUsingItsCommittedReference() throws Exception {
        local.setProviderSubscriptionId(null); local.setProviderCheckoutId(null);
        when(subscriptions.findByExternalReference("ncf_ref")).thenReturn(Optional.of(local));
        when(gateway.getCheckout("cs_test_1")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"id":"cs_test_1","mode":"subscription","client_reference_id":"ncf_ref","subscription":"sub_1",
             "metadata":{"external_reference":"ncf_ref","store_id":"42","country_code":"AR","plan":"PREMIUM","price_id":"price_1"}}
            """));
        service.synchronizeStripeCheckout("cs_test_1");
        assertThat(local.isAccessActive()).isTrue(); assertThat(local.getProviderSubscriptionId()).isEqualTo("sub_1");
        assertThat(local.getProviderCheckoutId()).isEqualTo("cs_test_1");
    }
    @Test void forgedCheckoutMetadataCannotAttachAnotherStoresSubscription() throws Exception {
        when(subscriptions.findByExternalReference("ncf_ref")).thenReturn(Optional.of(local));
        when(gateway.getCheckout("cs_test_1")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"id":"cs_test_1","mode":"subscription","client_reference_id":"ncf_ref","subscription":"sub_other",
             "metadata":{"external_reference":"ncf_ref","store_id":"999","country_code":"AR","plan":"PREMIUM","price_id":"price_1"}}
            """));
        assertThatThrownBy(() -> service.synchronizeStripeCheckout("cs_test_1")).hasMessageContaining("divergente");
        verify(gateway,never()).getSubscription("sub_other"); assertThat(local.isAccessActive()).isFalse();
    }
    void active() { local.setStatus(PaymentSubscriptionStatus.ACTIVE); local.setAccessActive(true); local.setNextPaymentAt(end); store.setPlan(PlanType.PREMIUM); }
    GatewaySubscription remote(String status,String product,String amount,Instant cancellation) {
        return new GatewaySubscription("sub_1",null,"ncf_ref",status,"USD",new BigDecimal(amount),end,"cust_1",product,Instant.now(),end,cancellation);
    }
    GatewayInvoice invoice(String id,String status) { return new GatewayInvoice(id,"sub_1",id,status,"USD",new BigDecimal("9.99")); }
}
