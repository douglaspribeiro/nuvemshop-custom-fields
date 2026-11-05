package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivationEmailJobTest {
    static final Instant NOW=Instant.parse("2026-11-04T12:00:00Z");
    final StoreRepository stores=mock(StoreRepository.class);
    final PersonalizationRuleRepository rules=mock(PersonalizationRuleRepository.class);
    final PersonalizationFieldHistoryRepository history=mock(PersonalizationFieldHistoryRepository.class);
    final ActivationEmailOutboxRepository outbox=mock(ActivationEmailOutboxRepository.class);
    final StoreAdminEmailService mail=mock(StoreAdminEmailService.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final ActivationEmailJob job=new ActivationEmailJob(stores,rules,outbox,mail,manager,history);
    Store store;
    @BeforeEach void configure() {
        when(manager.getTransaction(any())).thenAnswer(call->new SimpleTransactionStatus());
        ReflectionTestUtils.setField(job,"enabled",true);
        ReflectionTestUtils.setField(job,"dryRun",false);
        ReflectionTestUtils.setField(job,"suppressionReady",true);
        ReflectionTestUtils.setField(job,"allowlistText","12");
        ReflectionTestUtils.setField(job,"startAtText",NOW.minus(Duration.ofDays(30)).toString());
        store=store(NOW.minus(Duration.ofDays(3)));
        when(stores.findActiveByStoreIdForUpdate(12L)).thenAnswer(call->Optional.of(store));
        when(outbox.storeIdFor(1L)).thenReturn(Optional.of(12L));
    }
    Store store(Instant installed) {
        var s=new Store();s.setStoreId(12L);s.setStoreCountryCode("BR");
        ReflectionTestUtils.setField(s,"installedAt",installed);return s;
    }
    ActivationEmailOutbox event(String step) {
        var e=new ActivationEmailOutbox(12L,store.getInstalledAt(),step,NOW,"nc39-v1");
        when(outbox.findForUpdate(1L)).thenReturn(Optional.of(e));return e;
    }
    @Test void schedulesOnlyD3D7AndD11AndNeverD15() {
        store=store(NOW.minus(Duration.ofDays(15)));
        when(stores.findAllActive()).thenReturn(List.of(store));job.scheduleAt(NOW);
        var captured=org.mockito.ArgumentCaptor.forClass(ActivationEmailOutbox.class);
        verify(outbox,times(3)).save(captured.capture());
        assertThat(captured.getAllValues()).extracting(ActivationEmailOutbox::getStep).containsExactly("D3","D7","D11");
        verifyNoInteractions(mail);
    }
    @Test void anExistingD3DoesNotPreventSchedulingD7() {
        store=store(NOW.minus(Duration.ofDays(7)));
        when(stores.findAllActive()).thenReturn(List.of(store));
        when(outbox.existsByStoreIdAndStep(12L,"D3")).thenReturn(true);
        when(outbox.existsByStoreIdAndInstalledAtAndStep(12L,store.getInstalledAt(),"D3")).thenReturn(true);
        job.scheduleAt(NOW);
        var captured=org.mockito.ArgumentCaptor.forClass(ActivationEmailOutbox.class);verify(outbox).save(captured.capture());
        assertThat(captured.getValue().getStep()).isEqualTo("D7");
    }
    @Test void suppressesQueuedD15BeforeDeliveryEvenWithLiveFlagsEnabled() {
        var e=event("D15");job.dispatch(1L,NOW);
        assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);
        assertThat(e.getSuppressionReason()).isEqualTo("d15_temporarily_disabled");verifyNoInteractions(mail);
    }
    @ParameterizedTest @ValueSource(strings={"D3","D7","D11"})
    void sendsAnEligibleStageOnceAndCommitsClaimBeforeSmtp(String step) {
        int day=step.equals("D3")?3:step.equals("D7")?7:11;
        store=store(NOW.minus(Duration.ofDays(day)));var e=event(step);
        job.dispatch(1L,NOW);job.dispatch(1L,NOW);
        assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.SENT);assertThat(e.getAttempts()).isEqualTo(1);
        var order=inOrder(outbox,manager,mail);
        order.verify(outbox).saveAndFlush(e);order.verify(manager).commit(any());
        order.verify(mail).send(eq(12L),anyString(),anyString());
        verify(mail,times(1)).send(any(),any(),any());
    }
    @Test void smtpTimeoutRequiresReviewWithoutAutomaticRetry() {
        var e=event("D3");doThrow(new IllegalStateException("SMTP uncertain")).when(mail).send(any(),any(),any());
        job.dispatch(1L,NOW);job.dispatch(1L,NOW.plusSeconds(300));
        assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.REVIEW);
        assertThat(e.getAttempts()).isEqualTo(1);verify(mail,times(1)).send(any(),any(),any());
    }
    @Test void retriesPreSmtpValidationOnlyThreeTimes() {
        var e=event("D3");doThrow(new IllegalArgumentException("configuration missing")).when(mail).send(any(),any(),any());
        for(int i=0;i<4;i++)job.dispatch(1L,NOW.plusSeconds(i*600));
        assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.FAILED);
        assertThat(e.getAttempts()).isEqualTo(3);verify(mail,times(3)).send(any(),any(),any());
    }
    @Test void futureRetryDoesNotSendEarly() {
        var e=event("D3");e.claim();e.failed(NOW);
        job.dispatch(1L,NOW.plusSeconds(30));assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.PENDING);
        verifyNoInteractions(mail);
    }
    @Test void earlyStageCannotSendEvenWithIncorrectDueTime() {
        var e=event("D11");job.dispatch(1L,NOW);
        assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.PENDING);verifyNoInteractions(mail);
    }
    @Test void allEntryPointsRespectGlobalDisabledFlag() {
        ReflectionTestUtils.setField(job,"enabled",false);
        job.scheduleAt(NOW);job.dispatchDue(NOW);job.dispatch(1L,NOW);
        verifyNoInteractions(stores,outbox,mail);
    }
    @Test void malformedStartDateCannotEnableAnyEntryPoint() {
        ReflectionTestUtils.setField(job,"startAtText","invalid");job.scheduleAt(NOW);job.dispatch(1L,NOW);
        verifyNoInteractions(stores,outbox,mail);
    }
    @Test void simulationConsumesNoSmtpAttempt() {
        ReflectionTestUtils.setField(job,"dryRun",true);var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.SIMULATED);assertThat(e.getAttempts()).isZero();verifyNoInteractions(mail);
    }
    @Test void suppressionIntegrationGateKeepsQueuePending() {
        ReflectionTestUtils.setField(job,"suppressionReady",false);var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getState()).isEqualTo(ActivationEmailOutbox.State.PENDING);assertThat(e.getAttempts()).isZero();verifyNoInteractions(mail);
    }
    @Test void storeOutsideAllowlistIsSuppressed() {
        ReflectionTestUtils.setField(job,"allowlistText","99");var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getSuppressionReason()).isEqualTo("not_allowlisted");verifyNoInteractions(mail);
    }
    @Test void reinstalledStoreDoesNotReceiveOldInstallationMail() {
        var e=event("D3");ReflectionTestUtils.setField(store,"installedAt",NOW);job.dispatch(1L,NOW);
        assertThat(e.getSuppressionReason()).isEqualTo("store_inactive_or_reinstalled");verifyNoInteractions(mail);
    }
    @Test void uninstallOrErasureStopsDelivery() {
        when(stores.findActiveByStoreIdForUpdate(12L)).thenReturn(Optional.empty());var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getSuppressionReason()).isEqualTo("store_inactive_or_reinstalled");verifyNoInteractions(mail);
    }
    @Test void olderCohortIsNotContacted() {
        ReflectionTestUtils.setField(job,"startAtText",NOW.minus(Duration.ofDays(2)).toString());var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getSuppressionReason()).isEqualTo("outside_cohort");verifyNoInteractions(mail);
    }
    @Test void deletedConfigurationHistoryStillStopsOnboarding() {
        when(history.existsByStoreId(12L)).thenReturn(true);var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getSuppressionReason()).isEqualTo("first_activation");verifyNoInteractions(mail);
    }
    @Test void presentConfigurationAlsoStopsOnboarding() {
        when(rules.hasConfiguredFields(12L)).thenReturn(true);var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getSuppressionReason()).isEqualTo("first_activation");verifyNoInteractions(mail);
    }
    @Test void expiredStageCannotSend() {
        store=store(NOW.minus(Duration.ofDays(5)));var e=event("D3");job.dispatch(1L,NOW);
        assertThat(e.getSuppressionReason()).isEqualTo("stage_expired");verifyNoInteractions(mail);
    }
    @Test void unknownStageCannotUseAnOldD15Template() {
        var e=event("UNKNOWN");job.dispatch(1L,NOW);assertThat(e.getSuppressionReason()).isEqualTo("unsupported_stage");verifyNoInteractions(mail);
    }
    @Test void d11IsHelpCopyWithoutDiscountOrTrialExpiryPromise() {
        store=store(NOW.minus(Duration.ofDays(11)));store.setStoreCountryCode("AR");event("D11");job.dispatch(1L,NOW);
        var body=org.mockito.ArgumentCaptor.forClass(String.class);verify(mail).send(eq(12L),contains("primer producto"),body.capture());
        assertThat(body.getValue()).contains("último correo", "soporte").doesNotContain("30%","15 días","venció","cobro");
    }
}
