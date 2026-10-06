package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.properties.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentPendingCheckoutTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    final PaymentGatewayRouter router = mock(PaymentGatewayRouter.class);
    final PaddleGateway paddle = mock(PaddleGateway.class);
    final CreemGateway creem = mock(CreemGateway.class);
    final PaymentSubscription local = new PaymentSubscription();
    final PaymentAttempt attempt = new PaymentAttempt();
    final PaymentSubscriptionService service = new PaymentSubscriptionService(stores, subscriptions,
            mock(PlanEventRepository.class), router, mock(NuvemshopApiClient.class), mock(NuvemshopProperties.class),
            mock(MercadoPagoProperties.class), mock(EfiGateway.class), mock(PaymentNotificationService.class), attempts, paddle);

    @BeforeEach void setup() {
        local.setStoreId(42L); local.setProvider(PaymentProviderType.PADDLE);
        local.setProviderEnvironment(PaymentEnvironment.SANDBOX); local.setPlan(PlanType.PREMIUM);
        local.setStatus(PaymentSubscriptionStatus.PENDING); local.setCurrency("USD"); local.setAmountValue(new BigDecimal("4.99"));
        local.setExternalReference("ncf_ref"); local.setProviderCheckoutId("txn_old");
        local.setCheckoutUrl("https://app.test/checkout/paddle?token=old");
        local.setPendingStartedAt(Instant.now().minusSeconds(1801));
        attempt.setStatus(PaymentAttemptStatus.OPEN); attempt.setExpiresAt(Instant.now().minusSeconds(1));
        var store = new Store(); store.setStoreId(42L);
        when(stores.findActiveByStoreIdForUpdate(42L)).thenReturn(Optional.of(store));
        when(subscriptions.findByStoreId(42L)).thenReturn(Optional.of(local));
        when(subscriptions.findByExternalReference("ncf_ref")).thenReturn(Optional.of(local));
        when(subscriptions.save(local)).thenReturn(local);
        when(attempts.findByExternalReference("ncf_ref")).thenReturn(Optional.of(attempt));
        when(paddle.configured()).thenReturn(true); when(paddle.environment()).thenReturn(PaymentEnvironment.SANDBOX);
        when(paddle.getTransaction("txn_old")).thenReturn(remote("ready"), remote("canceled"));
        when(router.require(PaymentProviderType.CREEM)).thenReturn(creem);
        when(creem.environment()).thenReturn(PaymentEnvironment.SANDBOX);
    }

    @Test void expiredPaddleIsCanceledRemotelyBeforeAnotherGatewayCanStart() {
        assertThat(service.expirePendingCheckout(42L)).isTrue();
        var order = inOrder(paddle, subscriptions);
        order.verify(paddle).cancelCheckout("txn_old");
        order.verify(paddle).getTransaction("txn_old");
        order.verify(subscriptions).save(local);
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getCheckoutUrl()).isNull();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CANCELED);
    }

    @Test void recentCheckoutCanBeResumedWithoutCanceling() {
        local.setPendingStartedAt(Instant.now()); attempt.setExpiresAt(Instant.now().plusSeconds(1800));
        assertThat(service.expirePendingCheckout(42L)).isFalse();
        verify(paddle, never()).getTransaction(anyString());
        assertThat(local.getCheckoutUrl()).isNotBlank();
    }

    @Test void expiredTokenIsRetiredEvenBeforeThirtyMinutes() {
        local.setPendingStartedAt(Instant.now().minusSeconds(600));
        assertThat(service.expirePendingCheckout(42L)).isTrue();
        verify(paddle).cancelCheckout("txn_old");
    }

    @Test void paidCheckoutWithoutSubscriptionIsPreserved() {
        when(paddle.getTransaction("txn_old")).thenReturn(remote("paid"));
        assertThatThrownBy(() -> service.expirePendingCheckout(42L)).hasMessageContaining("pagamento anterior foi recebido");
        verify(paddle, never()).cancelCheckout(anyString());
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.OPEN);
    }

    @Test void cancellationRaceDoesNotDiscardPaymentOrReleaseNewCheckout() {
        when(paddle.getTransaction("txn_old")).thenReturn(remote("ready"), remote("completed"));
        assertThatThrownBy(() -> service.expirePendingCheckout(42L)).hasMessageContaining("encerramento");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        verify(subscriptions, never()).save(any());
    }

    @Test void apiFailurePreservesPendingAttempt() {
        when(paddle.getTransaction("txn_old")).thenThrow(new PaymentGatewayException("API unavailable"));
        assertThatThrownBy(() -> service.expirePendingCheckout(42L)).isInstanceOf(PaymentGatewayException.class);
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        verify(paddle, never()).cancelCheckout(anyString());
    }

    @Test void foreignTransactionCannotBeCanceled() {
        when(paddle.getTransaction("txn_old")).thenReturn(new PaddleGateway.PaddleTransaction("txn_old", null,
                "other_ref", "ready", "USD", new BigDecimal("4.99"), null));
        assertThatThrownBy(() -> service.expirePendingCheckout(42L)).hasMessageContaining("divergente");
        verify(paddle, never()).cancelCheckout(anyString());
    }

    @Test void confirmedExpiredCreemReleasesNewCheckout() throws Exception {
        creemCheckout("expired");
        assertThat(service.expirePendingCheckout(42L)).isTrue();
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getCheckoutUrl()).isNull();
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CANCELED);
    }

    @Test void oldSandboxCheckoutCannotBlockNewGatewayAfterEnvironmentChange() {
        when(paddle.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        assertThat(service.expirePendingCheckout(42L)).isTrue();
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.CANCELED);
        assertThat(local.getProviderStatus()).isEqualTo("sandbox_timeout");
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.CANCELED);
        verify(paddle, never()).getTransaction(anyString());
        verify(paddle, never()).cancelCheckout(anyString());
    }

    @Test void productionCheckoutCannotBeDiscardedWithoutItsCredentials() {
        local.setProviderEnvironment(PaymentEnvironment.PRODUCTION);
        assertThatThrownBy(() -> service.expirePendingCheckout(42L)).hasMessageContaining("Não foi possível verificar");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        verify(subscriptions, never()).save(any());
    }

    @Test void sandboxSubscriptionOrConfirmedPaymentIsPreservedWhenCredentialsChange() {
        when(paddle.environment()).thenReturn(PaymentEnvironment.PRODUCTION);
        local.setProviderSubscriptionId("sub_old");
        assertThatThrownBy(() -> service.expirePendingCheckout(42L)).hasMessageContaining("Não foi possível verificar");
        local.setProviderSubscriptionId(null); local.setLastPaymentStatus("paid");
        assertThatThrownBy(() -> service.expirePendingCheckout(42L)).hasMessageContaining("Não foi possível verificar");
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
    }

    @Test void openCreemIsResumedEvenAfterLocalTimeout() throws Exception {
        creemCheckout("pending");
        assertThat(service.expirePendingCheckout(42L)).isFalse();
        assertThat(local.getStatus()).isEqualTo(PaymentSubscriptionStatus.PENDING);
        assertThat(local.getCheckoutUrl()).isNotBlank();
        verify(creem, never()).createCheckout(any(), any(), anyString(), anyString());
    }

    @Test void unknownCreemResultCannotBeDiscardedByAge() {
        local.setProvider(PaymentProviderType.CREEM); local.setProviderCheckoutId(null);
        attempt.setStatus(PaymentAttemptStatus.UNKNOWN);
        assertThat(service.expirePendingCheckout(42L)).isFalse();
        verifyNoInteractions(creem);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
    }

    void creemCheckout(String status) throws Exception {
        local.setProvider(PaymentProviderType.CREEM); local.setProviderCheckoutId("ch_old"); local.setProviderPriceId("prod_1");
        when(creem.getCheckout("ch_old")).thenReturn(new ObjectMapper().readTree(
                "{\"id\":\"ch_old\",\"request_id\":\"ncf_ref\",\"product\":\"prod_1\",\"status\":\""+status+"\",\"metadata\":{\"store_id\":\"42\"}}"));
    }
    PaddleGateway.PaddleTransaction remote(String status) {
        return new PaddleGateway.PaddleTransaction("txn_old", null, "ncf_ref", status, "USD", new BigDecimal("4.99"), null);
    }
}
