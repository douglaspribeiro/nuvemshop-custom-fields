package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReplacedCheckoutLifecycleTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    final CreemGateway gateway = mock(CreemGateway.class);
    final PaymentNotificationService notifications = mock(PaymentNotificationService.class);
    final PaymentAttempt old = new PaymentAttempt();
    final PaymentSubscription current = new PaymentSubscription();
    final PaymentSubscriptionService service = new PaymentSubscriptionService(stores, subscriptions,
            mock(PlanEventRepository.class), router, mock(NuvemshopApiClient.class), mock(NuvemshopProperties.class),
            new MercadoPagoProperties(false, "https://api.test", "", "", 3, BigDecimal.TEN, BigDecimal.TEN),
            mock(EfiGateway.class), notifications, attempts);

    @BeforeEach void setup() {
        old.setStoreId(42L); old.setProvider(PaymentProviderType.CREEM); old.setEnvironment(PaymentEnvironment.SANDBOX);
        old.setStatus(PaymentAttemptStatus.REPLACED); old.setPlan(PlanType.PREMIUM);
        old.setCurrency("USD"); old.setAmountValue(BigDecimal.TEN);
        old.setExternalReference("old-reference"); old.setProviderTransactionId("ch_old");
        current.setStoreId(42L); current.setProvider(PaymentProviderType.CREEM);
        current.setExternalReference("new-reference"); current.setProviderCheckoutId("ch_new");
        current.setPlan(PlanType.PREMIUM_PLUS); current.setStatus(PaymentSubscriptionStatus.PENDING);
        when(router.require(PaymentProviderType.CREEM)).thenReturn(gateway);
        when(gateway.provider()).thenReturn(PaymentProviderType.CREEM);
        when(gateway.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(attempts.findByExternalReference("old-reference")).thenReturn(Optional.of(old));
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(current));
        when(gateway.getSubscription("sub_old")).thenReturn(remote("active"), remote("canceled"));
        when(gateway.getInvoice("pay_old")).thenReturn(invoice());
        when(gateway.getLatestInvoice("sub_old")).thenReturn(Optional.of(invoice()));
    }

    @Test void delayedPaidEventCancelsOldContractAndNeverActivatesTheNewPlan() {
        assertThat(service.synchronizeFromInvoice(PaymentProviderType.CREEM, "pay_old")).isSameAs(current);
        verify(gateway).cancelImmediately("sub_old");
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_PAID);
        assertThat(old.getLastError()).contains("reembolso");
        assertCurrentUnchanged();
    }

    @Test void repeatedAndOlderEventsDoNotEraseTheRecordedLatePayment() {
        service.synchronizeFromInvoice(PaymentProviderType.CREEM, "pay_old");
        when(gateway.getLatestInvoice("sub_old")).thenReturn(Optional.empty());
        service.synchronizeFromSubscriptionWithoutPayment(PaymentProviderType.CREEM, "sub_old");
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_PAID);
        verify(gateway,times(1)).cancelImmediately("sub_old");
        assertCurrentUnchanged();
    }

    @Test void cancellationMustBeConfirmedBeforeRecordingTheOperationAsHandled() {
        when(gateway.getSubscription("sub_old")).thenReturn(remote("active"));
        assertThatThrownBy(() -> service.synchronizeFromInvoice(PaymentProviderType.CREEM, "pay_old"))
                .isInstanceOf(PaymentGatewayException.class);
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED);
        assertCurrentUnchanged();
    }

    @Test void wrongEnvironmentCannotCancelOrModifyAnotherContract() {
        old.setEnvironment(PaymentEnvironment.PRODUCTION);
        assertThatThrownBy(() -> service.synchronizeFromInvoice(PaymentProviderType.CREEM, "pay_old"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(gateway,never()).cancelImmediately(anyString());
        assertCurrentUnchanged();
    }

    @Test void scheduledReconciliationFindsPaymentEvenWithoutWebhook() {
        when(gateway.getCheckout("ch_old")).thenReturn(new ObjectMapper().createObjectNode()
                .put("id","ch_old").put("request_id","old-reference").put("subscription","sub_old"));
        service.reconcileReplacedCheckout("old-reference");
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_PAID);
        assertCurrentUnchanged();
    }

    @Test void providerExpirationFinishesWatchingTheOldCheckout() {
        when(gateway.getCheckout("ch_old")).thenReturn(new ObjectMapper().createObjectNode()
                .put("id","ch_old").put("request_id","old-reference").put("status","expired"));
        service.reconcileReplacedCheckout("old-reference");
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_CLOSED);
        verify(gateway,never()).cancelImmediately(anyString());
        assertCurrentUnchanged();
    }

    @Test void oldCheckoutCompletedEventUsesTheOldAttemptRatherThanTheNewSubscription() {
        when(gateway.getCheckout("ch_old")).thenReturn(new ObjectMapper().createObjectNode()
                .put("id","ch_old").put("request_id","old-reference").put("subscription","sub_old"));
        assertThat(service.synchronizeCreemCheckout("ch_old")).isSameAs(current);
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_PAID);
        assertCurrentUnchanged();
    }

    @Test void dodoPollingFindsAndCancelsAContractCreatedThroughTheOldLink() {
        DodoTestGateway dodo = mock(DodoTestGateway.class);
        old.setProvider(PaymentProviderType.DODO_TEST);
        when(router.require(PaymentProviderType.DODO_TEST)).thenReturn(dodo);
        when(dodo.provider()).thenReturn(PaymentProviderType.DODO_TEST);
        when(dodo.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(dodo.getCheckout("ch_old")).thenReturn(new ObjectMapper().createObjectNode()
                .put("id","ch_old").put("payment_id","pay_old"));
        when(dodo.getInvoice("pay_old")).thenReturn(invoice());
        when(dodo.getSubscription("sub_old")).thenReturn(remote("active"),remote("cancelled"));
        service.reconcileReplacedCheckout("old-reference");
        verify(dodo).cancelImmediately("sub_old");
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_PAID);
        assertCurrentUnchanged();
    }

    @Test void dodoExpirationIsReadFromPaymentStatus() {
        DodoTestGateway dodo = mock(DodoTestGateway.class);
        old.setProvider(PaymentProviderType.DODO_TEST);
        when(router.require(PaymentProviderType.DODO_TEST)).thenReturn(dodo);
        when(dodo.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(dodo.getCheckout("ch_old")).thenReturn(new ObjectMapper().createObjectNode()
                .put("id","ch_old").put("payment_status","expired"));
        service.reconcileReplacedCheckout("old-reference");
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_CLOSED);
        assertCurrentUnchanged();
    }

    @Test void lateDodoFailureFromRetiredAttemptDoesNotCancelAgainOrChangeTheNewPurchase() {
        DodoTestGateway dodo = mock(DodoTestGateway.class);
        old.setProvider(PaymentProviderType.DODO_TEST); old.setStatus(PaymentAttemptStatus.CANCELED);
        when(router.require(PaymentProviderType.DODO_TEST)).thenReturn(dodo);
        when(dodo.provider()).thenReturn(PaymentProviderType.DODO_TEST);
        when(dodo.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(dodo.getInvoice("pay_old")).thenReturn(new GatewayInvoice("pay_old","sub_old","pay_old","failed","USD",BigDecimal.TEN));
        when(dodo.getSubscription("sub_old")).thenReturn(remote("failed"));
        assertThat(service.synchronizeFromInvoice(PaymentProviderType.DODO_TEST, "pay_old")).isSameAs(current);
        verify(dodo,never()).cancelImmediately(anyString());
        assertThat(old.getStatus()).isEqualTo(PaymentAttemptStatus.REPLACED_CLOSED);
        assertCurrentUnchanged();
    }

    private void assertCurrentUnchanged() {
        assertThat(current.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(current.getPlan()).isEqualTo(PlanType.PREMIUM_PLUS);
        assertThat(current.getProviderCheckoutId()).isEqualTo("ch_new");
        assertThat(current.isAccessActive()).isFalse();
        verify(subscriptions,never()).save(any());
        verifyNoInteractions(notifications);
    }
    private GatewaySubscription remote(String status) {
        return new GatewaySubscription("sub_old",null,"old-reference",status,"USD",BigDecimal.TEN,null);
    }
    private GatewayInvoice invoice() {
        return new GatewayInvoice("pay_old","sub_old","pay_old","paid","USD",BigDecimal.TEN);
    }
}
