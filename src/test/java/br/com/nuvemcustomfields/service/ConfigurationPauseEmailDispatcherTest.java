package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.properties.SesProperties;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static br.com.nuvemcustomfields.entity.ConfigurationPauseEmail.State.*;

class ConfigurationPauseEmailDispatcherTest {
    final ConfigurationPauseEmailRepository queue=mock(ConfigurationPauseEmailRepository.class);
    final StoreRepository stores=mock(StoreRepository.class);
    final PersonalizationRuleRepository rules=mock(PersonalizationRuleRepository.class);
    final ConfigurationPlanPolicy policy=mock(ConfigurationPlanPolicy.class);
    final StoreAdminEmailService emails=mock(StoreAdminEmailService.class);
    final PaymentSubscriptionService subscriptions=mock(PaymentSubscriptionService.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    ConfigurationPauseEmailDispatcher worker; ConfigurationPauseEmail row; Store store;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        worker=new ConfigurationPauseEmailDispatcher(queue,stores,rules,policy,emails,
                new SesProperties("mail.example.com",587,"fixture","fixture","app@example.com"),manager,subscriptions,"https://app.example.com");
        store=new Store(); store.setStoreId(12L); store.setStoreCountryCode("BR");
        var rule=new PersonalizationRule(); rule.setProductId(100L); rule.setPlanPaused(true);
        row=new ConfigurationPauseEmail(12L,"100");
        when(queue.findPending(eq(PENDING),any())).thenReturn(List.of(row),List.of());
        when(queue.saveAndFlush(row)).thenReturn(row); when(queue.findById(any())).thenReturn(Optional.of(row));
        when(stores.findActiveByStoreId(12L)).thenReturn(Optional.of(store));
        when(rules.findWithFieldsByStoreIdOrderByCreatedAtAscIdAsc(12L)).thenReturn(List.of(rule));
        when(policy.evaluate(eq(store),any())).thenReturn(new ConfigurationPlanPolicy.Decision(Set.of(),Map.of()));
    }
    @Test void claimsBeforeSendingAndRecordsSuccess() {
        worker.dispatch(); var order=inOrder(queue,emails);
        order.verify(queue).saveAndFlush(row);
        order.verify(emails).sendAction(eq(12L),contains("pausadas"),contains("continuam salvos"),any(),eq("https://app.example.com/admin/billing"));
        assertThat(row.getState()).isEqualTo(SENT);
    }
    @Test void skipsRecoveredConfiguration() {
        when(policy.evaluate(eq(store),any())).thenReturn(new ConfigurationPlanPolicy.Decision(Set.of(100L),Map.of()));
        worker.dispatch(); assertThat(row.getState()).isEqualTo(SKIPPED); verifyNoInteractions(emails);
    }
    @Test void paidRecoverySkipsMailEvenWhenSomeConfigurationsStillExceedPlanLimits() {
        store.setPlan(PlanType.PREMIUM); worker.dispatch();
        assertThat(row.getState()).isEqualTo(SKIPPED); verifyNoInteractions(emails);
    }
    @Test void efiRecoveryUsesExistingSamePlanCardForm() {
        when(subscriptions.reactivationPlan(12L)).thenReturn(Optional.of(PlanType.PREMIUM_ULTRA));
        worker.dispatch(); verify(emails).sendAction(eq(12L),any(),any(),any(),
                eq("https://app.example.com/admin/billing/reactivate?storeId=12"));
    }
    @Test void smtpTimeoutNeedsReviewInsteadOfAutomaticResend() {
        doThrow(new IllegalStateException("timeout")).when(emails).sendAction(any(),any(),any(),any(),any());
        worker.dispatch(); assertThat(row.getState()).isEqualTo(REVIEW);
        verify(emails,times(1)).sendAction(any(),any(),any(),any(),any());
    }
    @Test void validationFailureIsVisibleAndNeverReportedAsSent() {
        doThrow(new IllegalArgumentException("missing recipient")).when(emails).sendAction(any(),any(),any(),any(),any());
        worker.dispatch(); assertThat(row.getState()).isEqualTo(FAILED);
    }
    @Test void spanishNoticeDescribesActualPauseAndNoFalseDeadline() {
        var text=ConfigurationPauseEmailDispatcher.notice(true,2,"100",false);
        assertThat(text.body()).contains("plan Free", "siguen guardados", "2 producto(s)", "100", "desactivadas manualmente");
        assertThat(text.body()).doesNotContain("3 días", "serán pausadas");
    }
}
