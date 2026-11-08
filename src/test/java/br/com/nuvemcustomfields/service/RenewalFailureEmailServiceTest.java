package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RenewalFailureEmailServiceTest {
    final StoreRepository stores = mock(StoreRepository.class);
    final PaymentSubscriptionRepository subscriptions = mock(PaymentSubscriptionRepository.class);
    final PlanEventRepository events = mock(PlanEventRepository.class);
    final PaymentNotificationOutboxRepository history = mock(PaymentNotificationOutboxRepository.class);
    final StoreAdminEmailService emails = mock(StoreAdminEmailService.class);
    final RenewalFailureEmailService service = new RenewalFailureEmailService(stores, subscriptions, events, history, emails, "https://app.example.com");
    Store store; PaymentSubscription sub;

    @BeforeEach void setup() {
        store = new Store(); store.setStoreId(12L); store.setStoreName("Loja");
        store.setStoreEmail("owner@example.com"); store.setStoreCountryCode("BR"); store.setPlan(PlanType.FREE);
        sub = new PaymentSubscription(); sub.setStoreId(12L); sub.setProvider(PaymentProviderType.EFI);
        sub.setPlan(PlanType.PREMIUM_ULTRA);
        sub.setProviderSubscriptionId("contract"); sub.setLastPaymentId("charge");
        sub.setStatus(PaymentSubscriptionStatus.ERROR); sub.setLastPaymentStatus("unpaid");
        when(stores.findByStoreId(12L)).thenReturn(Optional.of(store));
        when(subscriptions.findByStoreId(12L)).thenReturn(Optional.of(sub));
        when(history.existsByStoreId(12L)).thenReturn(true);
    }

    @Test void previewsAndSendsTheEfiRecoveryInstructions() {
        var notice = service.preview(12L);
        assertThat(notice.eligible()).isTrue();
        assertThat(notice.body()).contains("plano gratuito (FREE)", "reativar seu plano Ultra", "mesmo plano", "após a confirmação");
        assertThat(notice.body()).contains("Suas configurações continuam salvas", "não precisa cadastrar tudo novamente")
            .doesNotContain("3 dias", "três dias");
        assertThat(notice.actionLabel()).isEqualTo("Reativar meu plano Ultra");
        assertThat(notice.actionUrl()).isEqualTo("https://app.example.com/admin/billing/reactivate?storeId=12");
        service.send(12L, notice.fingerprint());
        verify(emails).sendAction(12L, notice.subject(), notice.body(), notice.actionLabel(), notice.actionUrl());
    }

    @Test void neverEmailsRawProviderErrorsOrGuessesADeclineReason() {
        sub.setTechnicalError("secret token and card data"); sub.setLastError("HTTP 401 secret credential");
        assertThat(service.preview(12L).body()).contains("sem um motivo específico")
            .doesNotContain("secret", "401", "credential");
        sub.setLastError("insufficient_funds");
        assertThat(service.preview(12L).body()).contains("saldo ou limite insuficiente");
    }

    @Test void blocksIfPaymentHasRecoveredSinceThePageWasOpened() {
        var notice = service.preview(12L);
        sub.setAccessActive(true); store.setPlan(PlanType.PREMIUM_ULTRA);
        assertThatThrownBy(() -> service.send(12L, notice.fingerprint())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emails);
    }

    @Test void requiresPriorPaidHistoryAndARealFailedCharge() {
        when(history.existsByStoreId(12L)).thenReturn(false);
        assertThat(service.preview(12L).eligible()).isFalse();
        when(history.existsByStoreId(12L)).thenReturn(true);
        sub.setLastPaymentStatus("paid");
        assertThat(service.preview(12L).eligible()).isFalse();
    }

    @Test void excludesSandboxCourtesyAndUninstalledStores() {
        sub.setProviderEnvironment(PaymentEnvironment.SANDBOX);
        assertThat(service.preview(12L).eligible()).isFalse();
        sub.setProviderEnvironment(PaymentEnvironment.PRODUCTION); store.setCourtesyPremium(true);
        assertThat(service.preview(12L).eligible()).isFalse();
        store.setCourtesyPremium(false); store.setUninstalledAt(java.time.Instant.now());
        assertThat(service.preview(12L).eligible()).isFalse();
    }

    @Test void manualOverrideSuppressesAnAlreadyQueuedRenewalFailureNotice() {
        var notice = service.preview(12L);
        store.setPlan(PlanType.PREMIUM_PLUS);
        store.setManualPlanOverride(true);
        assertThat(service.preview(12L).eligible()).isFalse();
        assertThatThrownBy(() -> service.send(12L, notice.fingerprint())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emails);
    }

    @Test void rejectsAChangedChargeOrRecipientInsteadOfSendingAnOutdatedNotice() {
        var notice = service.preview(12L);
        sub.setLastPaymentId("new-charge");
        assertThatThrownBy(() -> service.send(12L, notice.fingerprint())).hasMessageContaining("mudou");
        verifyNoInteractions(emails);
    }

    @Test void generatesSpanishForSpanishSpeakingStores() {
        store.setStoreCountryCode("MX");
        assertThat(service.preview(12L).body()).contains("plan gratuito", "reactivar tu plan Ultra");
    }

    @Test void excludesPendingUpgradesInsteadOfCallingThemRenewalFailures() {
        sub.setUpgradePaymentPending(true);
        assertThat(service.preview(12L).eligible()).isFalse();
        assertThat(service.preview(12L).unavailableReason()).contains("upgrade pendente");
    }

    @Test void theGraceEmailStatesTheRealDeadlineAndKeepsConfigurationsSaved() {
        store.setPlan(PlanType.PREMIUM_ULTRA); sub.setAccessActive(true);
        sub.setStatus(PaymentSubscriptionStatus.PAST_DUE); sub.startRenewalFailure(java.time.Instant.now(), "charge");
        var notice=service.preview(12L);
        assertThat(notice.eligible()).isTrue();
        assertThat(notice.body()).contains("24 horas", "Regularize o pagamento até", "configurações continuam salvas")
            .doesNotContain("3 dias", "Sua assinatura voltou");
    }

    @Test void manualSendCannotDuplicateAnAlreadyQueuedAutomaticNotice() {
        var queue=mock(RenewalFailureEmailRepository.class); service.setAutomaticEmails(queue);
        sub.startRenewalFailure(java.time.Instant.now().minusSeconds(100000),"charge");
        when(queue.existsByProviderAndSubscriptionIdAndPaymentId(PaymentProviderType.EFI,"contract","charge")).thenReturn(true);
        assertThatThrownBy(() -> service.send(12L,service.preview(12L).fingerprint())).hasMessageContaining("aviso automático");
        verifyNoInteractions(emails);
    }
}
