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

class DlocalGoSubscriptionLifecycleTest {
    final StoreRepository stores=mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions=mock(PaymentSubscriptionRepository.class);
    final PaymentGatewayRouter router=mock(PaymentGatewayRouter.class);
    final DlocalGoGateway gateway=mock(DlocalGoGateway.class);
    final PaymentNotificationService notifications=mock(PaymentNotificationService.class);
    final PaymentSubscription local=new PaymentSubscription();
    final Store store=new Store();
    final Instant end=Instant.now().plusSeconds(86400);
    final PaymentSubscriptionService service=new PaymentSubscriptionService(stores,subscriptions,mock(PlanEventRepository.class),router,
            mock(NuvemshopApiClient.class),mock(NuvemshopProperties.class),
            new MercadoPagoProperties(false,"https://api.test","","",3,BigDecimal.TEN,BigDecimal.TEN),mock(EfiGateway.class),notifications);
    @BeforeEach void setup() {
        local.setStoreId(42L); local.setProvider(PaymentProviderType.DLOCAL_GO); local.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        local.setProviderSubscriptionId("20:3"); local.setProviderCheckoutId("20"); local.setProviderPriceId("10");
        local.setExternalReference("ncf_ref"); local.setPlan(PlanType.PREMIUM); local.setCurrency("ARS");
        local.setAmountValue(new BigDecimal("5599")); local.setStatus(PaymentSubscriptionStatus.PENDING);
        store.setStoreId(42L); store.setPlan(PlanType.FREE);
        when(router.require(PaymentProviderType.DLOCAL_GO)).thenReturn(gateway);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(subscriptions.findByProviderSubscriptionId("20:3")).thenReturn(Optional.of(local));
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(local)); when(subscriptions.save(local)).thenReturn(local);
        when(stores.findByStoreId(42L)).thenReturn(Optional.of(store));
        when(gateway.getSubscription("20:3")).thenReturn(remote("active","20","ncf_ref",end,null));
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.of(invoice("paid","ARS","5599")));
    }
    @Test void confirmedSubscriptionWithoutPaymentDoesNotGrantAccess() {
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.DLOCAL_GO,"20:3");
        assertThat(local.isAccessActive()).isFalse(); verify(notifications,never()).enqueue(any(),any());
    }
    @Test void matchingPaidExecutionActivatesAndRepeatedReconciliationKeepsSamePaymentIdentity() {
        service.reconcile(42L); service.reconcile(42L);
        assertThat(local.isAccessActive()).isTrue(); assertThat(store.getPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(local.getLastPaymentId()).isEqualTo("ST-test-1"); assertThat(local.getCurrentPeriodEnd()).isEqualTo(end);
    }
    @Test void paymentCurrencyAmountReferenceAndCheckoutAreValidated() {
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.of(invoice("paid","USD","5599")));
        assertThatThrownBy(() -> service.reconcile(42L)).hasMessageContaining("Pagamento dLocal Go divergente");
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.of(invoice("paid","ARS","1")));
        assertThatThrownBy(() -> service.reconcile(42L)).hasMessageContaining("Pagamento dLocal Go divergente");
        when(gateway.getSubscription("20:3")).thenReturn(remote("active","21","ncf_other",end,null));
        assertThatThrownBy(() -> service.reconcile(42L)).hasMessageContaining("divergente");
        assertThat(local.isAccessActive()).isFalse();
    }
    @Test void paidButExpiredExecutionDoesNotGrantAnotherMonth() {
        when(gateway.getSubscription("20:3")).thenReturn(remote("active","20","ncf_ref",Instant.now().minusSeconds(1),null));
        service.reconcile(42L); assertThat(local.isAccessActive()).isFalse(); assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PAUSED);
    }
    @Test void declinedRenewalRevokesWithoutBorrowingMercadoPagoGracePeriod() {
        local.setAccessActive(true); store.setPlan(PlanType.PREMIUM);
        when(gateway.getSubscription("20:3")).thenReturn(remote("past_due","20","ncf_ref",end,null));
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.of(invoice("failed","ARS","5599")));
        service.reconcile(42L); assertThat(local.isAccessActive()).isFalse(); assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PAST_DUE);
    }
    @Test void canceledSubscriptionPreservesPaidIntervalAndLateConfirmation() {
        when(gateway.getSubscription("20:3")).thenReturn(remote("canceled","20","ncf_ref",end,end));
        service.synchronizeFromSubscription(PaymentProviderType.DLOCAL_GO,"20:3");
        assertThat(local.isAccessActive()).isTrue(); assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getCancellationEffectiveAt()).isEqualTo(end);
    }
    @Test void pendingCheckoutIsRecoveredByDedicatedPlanWithoutBrowserRedirect() {
        local.setProviderSubscriptionId(null);
        when(gateway.checkoutSubscription("20")).thenReturn(Optional.of(remote("active","20","ncf_ref",end,null)));
        service.reconcile(42L); assertThat(local.getProviderSubscriptionId()).isEqualTo("20:3"); assertThat(local.isAccessActive()).isTrue();
    }
    GatewayInvoice invoice(String status,String currency,String amount) { return new GatewayInvoice("20:3:1","20:3","ST-test-1",status,currency,new BigDecimal(amount)); }
    GatewaySubscription remote(String status,String checkout,String reference,Instant until,Instant cancellation) {
        return new GatewaySubscription("20:3",checkout,reference,status,"ARS",new BigDecimal("5599"),until,null,null,Instant.now(),until,cancellation);
    }
    @Test void fullRefundRevokesEvenCanceledPaidAccessAndDoesNotResurrectOnRepeatedEvents() {
        local.setAccessActive(true);store.setPlan(PlanType.PREMIUM);
        when(gateway.getSubscription("20:3")).thenReturn(remote("canceled","20","ncf_ref",end,end));
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.of(invoice("refunded","ARS","5599")));
        service.reconcile(42L);service.reconcile(42L);
        assertThat(local.isAccessActive()).isFalse();assertThat(store.getPlan()).isEqualTo(PlanType.FREE);
    }
    @Test void partialRefundPreservesConfirmedPaidAccess() {
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.of(invoice("partialRefund","ARS","5599")));
        service.reconcile(42L);assertThat(local.isAccessActive()).isTrue();
    }

    @Test void choosingAnotherPlanClosesPendingCheckoutWithoutWaitingForExpiration() {
        local.setProviderSubscriptionId(null);
        local.setPendingStartedAt(Instant.now());
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(gateway.getPlan("20")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("active",true),
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("active",false));
        service.preparePendingCheckout(42L,PlanType.PREMIUM_PLUS);
        verify(gateway).cancelCheckout("20");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
    }
    @Test void choosingSamePlanKeepsPendingCheckout() {
        local.setProviderSubscriptionId(null);
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(gateway.getPlan("20")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("active",true));
        service.preparePendingCheckout(42L,PlanType.PREMIUM);
        verify(gateway,never()).cancelCheckout(anyString());
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
    }
    @Test void paymentCompletedWhileClosingCheckoutIsPreserved() {
        local.setProviderSubscriptionId(null);
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(gateway.getPlan("20")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("active",true));
        when(gateway.checkoutSubscription("20")).thenReturn(Optional.empty(),Optional.of(remote("canceled","20","ncf_ref",end,end)));
        service.preparePendingCheckout(42L,PlanType.PREMIUM_PLUS);
        verify(gateway).cancelCheckout("20");
        assertThat(local.isAccessActive()).isTrue();
        assertThat(local.getLastPaymentId()).isEqualTo("ST-test-1");
    }
    @Test void failedCheckoutClosureDoesNotReleaseAnotherPurchase() {
        local.setProviderSubscriptionId(null);
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(gateway.getPlan("20")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("active",true));
        doThrow(new PaymentGatewayException("timeout")).when(gateway).cancelCheckout("20");
        assertThatThrownBy(() -> service.preparePendingCheckout(42L,PlanType.PREMIUM_PLUS)).isInstanceOf(PaymentGatewayException.class);
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
    }

    @Test void changingPlanClosesAnAlreadyCreatedUnpaidSubscription() {
        when(gateway.checkoutSubscription("20")).thenReturn(Optional.of(remote("pending","20","ncf_ref",null,null)));
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(gateway.getSubscription("20:3")).thenReturn(remote("pending","20","ncf_ref",null,null),
                remote("canceled","20","ncf_ref",null,null));
        when(gateway.getLatestInvoice("20:3")).thenReturn(Optional.empty());
        service.preparePendingCheckout(42L,PlanType.PREMIUM_PLUS);
        verify(gateway).cancelImmediately("20:3");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.isAccessActive()).isFalse();
    }
    @Test void changingPlanNeverCancelsAnExistingSubscriptionThatHasJustBeenPaid() {
        when(gateway.checkoutSubscription("20")).thenReturn(Optional.of(remote("active","20","ncf_ref",end,null)));
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        service.preparePendingCheckout(42L,PlanType.PREMIUM_PLUS);
        verify(gateway,never()).cancelImmediately(anyString());
        assertThat(local.isAccessActive()).isTrue();
        assertThat(local.getLastPaymentId()).isEqualTo("ST-test-1");
    }

    @Test void anotherPlanSelectionClosesOldCheckoutEvenWhenItHasALink() {
        local.setProviderSubscriptionId(null);
        local.setCheckoutUrl("https://checkout-sbx.dlocalgo.com/validate/subscription/test");
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(gateway.getPlan("20")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("active",true),
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("active",false));
        service.preparePendingCheckout(42L,PlanType.PREMIUM_PLUS);
        verify(gateway).cancelCheckout("20");
        assertThat(local.getCheckoutUrl()).isNull();
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getPlan()).isEqualTo(PlanType.PREMIUM);
    }

}
