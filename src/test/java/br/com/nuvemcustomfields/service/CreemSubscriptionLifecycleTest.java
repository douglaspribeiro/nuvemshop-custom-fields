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

class CreemSubscriptionLifecycleTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    final CreemGateway gateway = mock(CreemGateway.class);
    final PaymentNotificationService notifications = mock(PaymentNotificationService.class);
    final PaymentSubscription local = new PaymentSubscription();
    final Store store = new Store();
    final Instant end = Instant.now().plusSeconds(86400);
    final PaymentSubscriptionService service = new PaymentSubscriptionService(stores,subscriptions,mock(PlanEventRepository.class),router,
            mock(NuvemshopApiClient.class),mock(NuvemshopProperties.class),
            new MercadoPagoProperties(false,"https://api.test","","",3,BigDecimal.TEN,BigDecimal.TEN),mock(EfiGateway.class),notifications);
    @BeforeEach void setup() {
        local.setStoreId(42L); local.setProvider(PaymentProviderType.CREEM); local.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        local.setProviderSubscriptionId("sub_1"); local.setProviderCheckoutId("ch_1"); local.setProviderPriceId("prod_1");
        local.setExternalReference("ncf_ref"); local.setPlan(PlanType.PREMIUM); local.setCurrency("USD");
        local.setAmountValue(new BigDecimal("9.99")); local.setStatus(PaymentSubscriptionStatus.PENDING);
        store.setStoreId(42L); store.setPlan(PlanType.FREE);
        when(router.require(PaymentProviderType.CREEM)).thenReturn(gateway); when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(gateway.graceDays()).thenReturn(3);
        when(subscriptions.findByProviderSubscriptionId("sub_1")).thenReturn(Optional.of(local));
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(local)); when(subscriptions.save(local)).thenReturn(local);
        when(stores.findByStoreId(42L)).thenReturn(Optional.of(store));
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","prod_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","paid")));
    }
    @Test void activeEventDoesNotGrantUnpaidAccess() {
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.CREEM,"sub_1");
        assertThat(local.isAccessActive()).isFalse(); assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
        verify(notifications,never()).enqueue(any(),any());
    }
    @Test void confirmedPaidInvoiceActivatesOnlyMatchingProductAndCurrency() {
        service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1");
        assertThat(local.isAccessActive()).isTrue(); assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(local.getCurrentPeriodEnd()).isEqualTo(end); verify(notifications).enqueue(eq(local),any());
    }
    @Test void mismatchedProductOrPaymentCurrencyCannotGrantAccess() {
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","prod_foreign","9.99",null));
        assertThatThrownBy(() -> service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1")).hasMessageContaining("Produto Creem divergente");
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","prod_1","9.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(new GatewayInvoice("tran_1","sub_1","tran_1","paid","EUR",BigDecimal.TEN)));
        assertThatThrownBy(() -> service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1")).hasMessageContaining("Pagamento Creem divergente");
        assertThat(local.isAccessActive()).isFalse();
    }
    @Test void canceledImmediatelyAndUnpaidRevokeExistingAccess() {
        active(); when(gateway.getSubscription("sub_1")).thenReturn(remote("canceled","prod_1","9.99",null));
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.CREEM,"sub_1"); assertThat(local.isAccessActive()).isFalse();
        active(); when(gateway.getSubscription("sub_1")).thenReturn(remote("unpaid","prod_1","9.99",null));
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.CREEM,"sub_1"); assertThat(local.isAccessActive()).isFalse();
    }
    @Test void scheduledCancellationKeepsConfirmedPaidPeriodEvenWhenPaidWebhookWasLate() {
        when(gateway.getSubscription("sub_1")).thenReturn(remote("scheduled_cancel","prod_1","9.99",end));
        service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1");
        assertThat(local.isAccessActive()).isTrue(); assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getNextPaymentAt()).isEqualTo(end);
    }
    @Test void fullRefundRevokesButPartialRefundPreservesAccess() {
        active(); when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","partialRefund")));
        service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1"); assertThat(local.isAccessActive()).isTrue();
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_1","refunded")));
        service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1"); assertThat(local.isAccessActive()).isFalse();
    }
    @Test void upgradeCannotUseThePreviousPaidInvoiceToReleaseTheNewPlan() {
        active(); local.setLastPaymentId("tran_old"); local.requestUpgrade(PlanType.PREMIUM_PLUS,new BigDecimal("19.99"),"prod_2");
        when(gateway.getSubscription("sub_1")).thenReturn(remote("active","prod_2","19.99",null));
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_old","paid")));
        assertThatThrownBy(() -> service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1")).hasMessageContaining("pagamento proporcional");
        assertThat(local.getPlan()).isEqualTo(PlanType.PREMIUM); assertThat(local.getUpgradePlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        when(gateway.getLatestInvoice("sub_1")).thenReturn(Optional.of(invoice("tran_upgrade","paid")));
        service.synchronizeFromSubscription(PaymentProviderType.CREEM,"sub_1");
        assertThat(local.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS); assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        verify(notifications).enqueueUpgrade(eq(store), eq(local), eq(PlanType.PREMIUM),
                argThat(invoice -> "tran_upgrade".equals(invoice.paymentId())));
        verify(notifications, never()).enqueue(any(), any());
    }
    void active() { local.setStatus(PaymentSubscriptionStatus.ACTIVE); local.setAccessActive(true); local.setNextPaymentAt(end); store.setPlan(PlanType.PREMIUM); }
    GatewaySubscription remote(String status,String product,String amount,Instant cancellation) {
        return new GatewaySubscription("sub_1",null,"ncf_ref",status,"USD",new BigDecimal(amount),end,"cust_1",product,Instant.now(),end,cancellation);
    }
    GatewayInvoice invoice(String id,String status) { return new GatewayInvoice(id,"sub_1",id,status,"USD",new BigDecimal("9.99")); }
}
