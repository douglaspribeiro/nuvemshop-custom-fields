package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="activation.email.schedule-delay-ms=86400000")
@ExtendWith(OutputCaptureExtension.class)
class ActivationEmailFlowIntegrationTest {
    static final long STORE=990039001L;
    @Autowired ActivationEmailJob job;
    @Autowired StoreRepository stores;
    @Autowired PersonalizationRuleRepository rules;
    @Autowired PersonalizationFieldRepository fields;
    @Autowired PersonalizationFieldHistoryRepository history;
    @Autowired ActivationEmailOutboxRepository queue;
    @Autowired StoreDataErasureService erasure;
    @Autowired ActivationEmailMetricsSnapshotService metricsSnapshots;
    @Autowired io.micrometer.core.instrument.MeterRegistry registry;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StoreAdminEmailService mail;
    @MockitoBean ActivationEmailProtectionService protection;
    @MockitoBean br.com.nuvemcustomfields.properties.SesEventsProperties ses;
    Instant installed;
    @BeforeEach void setup() {
        when(ses.configurationSet()).thenReturn("config");
        when(protection.ready()).thenReturn(true);
        when(protection.prepare(any(),any())).thenReturn("https://app.test/activation-emails/unsubscribe?token=test");
        installed=Instant.now().minus(Duration.ofDays(7)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var s=new Store();s.setStoreId(STORE);s.setStoreCountryCode("BR");s.setStoreName("private-owner-name");
        s.setStoreEmail("owner-sensitive@example.com");ReflectionTestUtils.setField(s,"installedAt",installed);stores.saveAndFlush(s);
        ReflectionTestUtils.setField(job,"enabled",true);ReflectionTestUtils.setField(job,"dryRun",false);
        ReflectionTestUtils.setField(job,"suppressionReady",true);ReflectionTestUtils.setField(job,"allowlistText",String.valueOf(STORE));
        ReflectionTestUtils.setField(job,"startAtText",installed.minusSeconds(86400).toString());
        clearInvocations(mail);
    }
    @AfterEach void cleanup() {
        ReflectionTestUtils.setField(job,"enabled",false);
        jdbc.update("delete from nc39_activation_email_outbox where store_id=?",STORE);
        jdbc.update("delete from personalization_fields where rule_id in (select id from personalization_rules where store_id=?)",STORE);
        jdbc.update("delete from personalization_rules where store_id=?",STORE);
        jdbc.update("delete from personalization_field_history where store_id=?",STORE);
        jdbc.update("delete from stores where store_id=?",STORE);
    }
    void assertNoActivationSent() { verify(mail,never()).sendActivation(any(),any(),any(),any(),any(),any(),any()); }
    Instant day(int n) {return installed.plus(Duration.ofDays(n));}
    List<ActivationEmailOutbox> rows() {return queue.findAll().stream().filter(e->e.getStoreId()==STORE).toList();}
    ActivationEmailOutbox row(String step) {return rows().stream().filter(e->e.getStep().equals(step)).findFirst().orElseThrow();}
    ActivationEmailOutbox pending(String step,int day) {return queue.saveAndFlush(new ActivationEmailOutbox(STORE,installed,step,day(day),"nc39-v1"));}
    void configureProduct(boolean paused) {
        var r=new PersonalizationRule();r.setStoreId(STORE);r.setProductId(10L);r.setPlanPaused(paused);rules.saveAndFlush(r);
        var f=new PersonalizationField();f.setRule(r);f.setLabel("Nome");fields.saveAndFlush(f);
    }
    @Test void completeJourneySendsD3D7D11OnceWithoutD15AndLogsNoPersonalData(CapturedOutput output) {
        job.scheduleAt(day(3).minusSeconds(1));assertThat(rows()).isEmpty();
        for(int d:new int[]{3,3,7,7,11,11,15})job.scheduleAt(day(d));
        assertThat(rows()).extracting(ActivationEmailOutbox::getStep).containsExactlyInAnyOrder("D3","D7","D11");
        assertThat(rows()).allMatch(e->e.getState()==ActivationEmailOutbox.State.SENT && e.getAttempts()==1);
        verify(mail,times(3)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
        assertThat(output).contains("nc39.email.queued","step=D11","nc39.email.claimed","nc39.email.sent")
                .doesNotContain("owner-sensitive@example.com","private-owner-name");
    }
    @Test void firstSavedConfigurationStopsFutureStepsEvenIfLaterDisabled() {
        job.scheduleAt(day(3));configureProduct(false);job.scheduleAt(day(7));
        assertThat(row("ACTIVATED").getState()).isEqualTo(ActivationEmailOutbox.State.ACTIVATED);
        var r=rules.findByStoreIdAndProductId(STORE,10L).orElseThrow();r.setEnabled(false);rules.saveAndFlush(r);
        job.scheduleAt(day(11));verify(mail,times(1)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
        assertThat(rows()).extracting(ActivationEmailOutbox::getStep).containsExactlyInAnyOrder("D3","ACTIVATED");
    }
    @Test void pausedConfigurationDoesNotGetMistakenForNeverConfigured() {
        configureProduct(true);job.scheduleAt(day(3));assertThat(row("ACTIVATED").getState()).isEqualTo(ActivationEmailOutbox.State.ACTIVATED);
        assertNoActivationSent();
    }
    @Test void deletedConfigurationHistoryStopsTheSequence() {
        history.saveAndFlush(new PersonalizationFieldHistory(STORE,10L,"Nome antigo"));
        job.scheduleAt(day(3));assertThat(row("ACTIVATED").getState()).isEqualTo(ActivationEmailOutbox.State.ACTIVATED);assertNoActivationSent();
    }
    @Test void configurationBeforeDeliverySuppressesAlreadyPendingMessages() {
        ReflectionTestUtils.setField(job,"suppressionReady",false);job.scheduleAt(day(3));pending("D7",7);
        configureProduct(false);job.scheduleAt(day(7));
        assertThat(row("D3").getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);
        assertThat(row("D7").getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);assertNoActivationSent();
    }
    @Test void queuedD15IsSuppressedAndNeverConvertedIntoALateD11Email() {
        var e=pending("D15",15);job.dispatch(e.getId(),day(15));
        assertThat(queue.findById(e.getId()).orElseThrow().getSuppressionReason()).isEqualTo("d15_temporarily_disabled");assertNoActivationSent();
    }
    @Test void mismatchedInstallationAndUninstallSuppressPendingMessages() {
        var e=queue.saveAndFlush(new ActivationEmailOutbox(STORE,installed.minusSeconds(1),"D3",day(3),"nc39-v1"));
        var s=stores.findByStoreId(STORE).orElseThrow();
        job.dispatch(e.getId(),day(3));assertThat(queue.findById(e.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);
        var e7=new ActivationEmailOutbox(STORE,s.getInstalledAt(),"D7",day(7),"nc39-v1");e7=queue.saveAndFlush(e7);
        s.setUninstalledAt(day(4));stores.saveAndFlush(s);job.dispatch(e7.getId(),day(7));
        assertThat(queue.findById(e7.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);assertNoActivationSent();
    }
    @Test void smtpTimeoutLeavesReviewAndNeverRetriesAutomatically() {
        doThrow(new IllegalStateException("SMTP uncertain")).when(mail).sendActivation(any(),any(),any(),any(),any(),any(),any());
        job.scheduleAt(day(3));job.dispatchDue(day(3).plusSeconds(120));
        assertThat(row("D3").getState()).isEqualTo(ActivationEmailOutbox.State.REVIEW);assertThat(row("D3").getAttempts()).isEqualTo(1);
        verify(mail,times(1)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void concurrentWorkersSeeCommittedClaimAndDoNotHoldTransactionDuringSmtp() throws Exception {
        var e=pending("D3",3);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(queue.findById(e.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.CLAIMED);
            entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return null;
        }).when(mail).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
        try(var workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->job.dispatch(e.getId(),day(3)));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                workers.submit(()->job.dispatch(e.getId(),day(3))).get(5,TimeUnit.SECONDS);
            } finally { release.countDown(); }
            first.get(5,TimeUnit.SECONDS);
        }
        assertThat(queue.findById(e.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        verify(mail,times(1)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void concurrentSchedulersCreateOnlyOneInstallationStep() throws Exception {
        try(var workers=Executors.newFixedThreadPool(2)) {
            var a=workers.submit(()->job.scheduleAt(day(3)));var b=workers.submit(()->job.scheduleAt(day(3)));
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(rows()).hasSize(1);assertThat(row("D3").getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        verify(mail,times(1)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void lgpdErasureDeletesTheActivationQueue() {
        pending("D3",3);
        var s=stores.findByStoreId(STORE).orElseThrow();s.setDepartureCounted(true);stores.saveAndFlush(s);
        erasure.erase(STORE);assertThat(rows()).isEmpty();
        assertThat(stores.findByStoreId(STORE)).isEmpty();assertNoActivationSent();
    }
    @Test void reinstallWithOriginalInstallDateDoesNotRestartAlreadySentSteps() {
        job.scheduleAt(day(3));
        var s=stores.findByStoreId(STORE).orElseThrow();s.setUninstalledAt(day(4));stores.saveAndFlush(s);
        s.setUninstalledAt(null);stores.saveAndFlush(s);job.scheduleAt(day(3));
        assertThat(stores.findByStoreId(STORE).orElseThrow().getInstalledAt()).isEqualTo(installed);
        assertThat(rows()).hasSize(1);verify(mail,times(1)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    void installationAge(int days) {
        installed=Instant.now().minus(Duration.ofDays(days)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("update stores set installed_at=? where store_id=?",java.sql.Timestamp.from(installed),STORE);
    }
    @ParameterizedTest @ValueSource(ints={11,30,90})
    void d11ReachesAnyOldUnconfiguredStoreOnceWithoutRetroactiveD3D7(int days) {
        installationAge(days);
        ReflectionTestUtils.setField(job,"startAtText",Instant.now().minusSeconds(86400).toString());
        ReflectionTestUtils.setField(job,"allowlistText","");
        job.scheduleAt(day(days));job.scheduleAt(day(days+30));
        assertThat(rows()).extracting(ActivationEmailOutbox::getStep).containsExactly("D11");
        assertThat(row("D11").getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        verify(mail,times(1)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void d11StillWorksWithoutACohortButNeverBeforeElevenDays() {
        installationAge(30);ReflectionTestUtils.setField(job,"startAtText","");
        job.scheduleAt(day(11).minusSeconds(1));assertThat(rows()).noneMatch(e->e.getStep().equals("D11"));
        job.scheduleAt(day(11));assertThat(row("D11").getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
    }
    @ParameterizedTest @ValueSource(strings={"stage_expired","outside_cohort","not_allowlisted","first_activation"})
    void onlySupersededAudienceSuppressionsCanResumeD11(String reason) {
        installationAge(30);var event=pending("D11",11);event.suppress(reason);queue.saveAndFlush(event);
        job.scheduleAt(day(30));
        assertThat(row("D11").getId()).isEqualTo(event.getId());assertThat(row("D11").getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        verify(mail,times(1)).sendActivation(eq(STORE),any(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"unsubscribe","ses_bounce","ses_complaint","ses_unsubscribe","store_inactive_or_reinstalled"})
    void privacyAndOtherSuppressionReasonsRemainTerminal(String reason) {
        installationAge(30);var event=pending("D11",11);event.suppress(reason);queue.saveAndFlush(event);
        job.scheduleAt(day(30));assertThat(row("D11").getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);
        assertThat(row("D11").getSuppressionReason()).isEqualTo(reason);assertNoActivationSent();
    }
    @Test void currentlyConfiguredPausedProductStillBlocksOldStoreD11() {
        installationAge(30);configureProduct(true);job.scheduleAt(day(30));
        assertThat(rows()).noneMatch(e->e.getStep().equals("D11"));assertNoActivationSent();
    }
    @Test void deletedPastConfigurationDoesNotPreventD11WhenNoProductIsCurrentlyConfigured() {
        installationAge(30);history.saveAndFlush(new PersonalizationFieldHistory(STORE,10L,"Nome antigo"));
        job.scheduleAt(day(30));assertThat(row("D11").getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        assertThat(rows()).noneMatch(e->e.getStep().equals("D3")||e.getStep().equals("D7"));
    }
    @ParameterizedTest @ValueSource(strings={"SENT","CLAIMED","REVIEW","SIMULATED","FAILED"})
    void aPreviouslyHandledD11NeverRestartsOnAnotherInstallation(String state) {
        installationAge(30);var older=new ActivationEmailOutbox(STORE,installed.minusSeconds(86400),"D11",day(11),"nc39-v1");
        ReflectionTestUtils.setField(older,"state",ActivationEmailOutbox.State.valueOf(state));queue.saveAndFlush(older);
        job.scheduleAt(day(30));assertThat(queue.findByStoreIdAndInstalledAtAndStep(STORE,installed,"D11").orElseThrow().getSuppressionReason()).isEqualTo("already_contacted");
        assertNoActivationSent();
    }

    @Test void audienceMetricsAreReadOnlyEvenWhenCampaignIsDisabled() {
        installationAge(30);ReflectionTestUtils.setField(job,"enabled",false);
        ReflectionTestUtils.setField(job,"startAtText","");ReflectionTestUtils.setField(job,"allowlistText","");
        var snapshot=metricsSnapshots.collect(day(30));
        assertThat(snapshot.eligible().get("D11")).isEqualTo(1);
        assertThat(snapshot.ready().get("D11")).isZero();assertThat(snapshot.stores().get("eligible")).isEqualTo(1);
        assertThat(rows()).isEmpty();verify(mail,never()).sendActivation(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void audienceMetricsAndPersistedStatesTrackAConfirmedSendWithoutCountingItAgain() {
        installationAge(30);when(mail.configured()).thenReturn(true);
        var before=metricsSnapshots.collect(day(30));assertThat(before.ready().get("D11")).isEqualTo(1);
        double counter=registry.get("ncf.activation.email.sent").tag("step","D11").counter().count();
        job.scheduleAt(day(30));job.scheduleAt(day(31));
        var after=metricsSnapshots.collect(day(31));assertThat(after.eligible().get("D11")).isZero();
        assertThat(after.outbox().get("D11:SENT")).isGreaterThanOrEqualTo(1);
        assertThat(registry.get("ncf.activation.email.sent").tag("step","D11").counter().count()).isEqualTo(counter+1);
    }
    @Test void smtpUncertaintyAndSimulationAreNotCountedAsConfirmedSends() {
        installationAge(30);
        double counter=registry.get("ncf.activation.email.sent").tag("step","D11").counter().count();
        ReflectionTestUtils.setField(job,"dryRun",true);job.scheduleAt(day(30));
        assertThat(row("D11").getState()).isEqualTo(ActivationEmailOutbox.State.SIMULATED);
        assertThat(registry.get("ncf.activation.email.sent").tag("step","D11").counter().count()).isEqualTo(counter);
        queue.delete(row("D11"));ReflectionTestUtils.setField(job,"dryRun",false);
        doThrow(new IllegalStateException()).when(mail).sendActivation(any(),any(),any(),any(),any(),any(),any());
        job.scheduleAt(day(30));assertThat(row("D11").getState()).isEqualTo(ActivationEmailOutbox.State.REVIEW);
        assertThat(registry.get("ncf.activation.email.sent").tag("step","D11").counter().count()).isEqualTo(counter);
    }
    @Test void currentConfigurationAndInvalidAddressAreVisibleAsAudienceBlocks() {
        installationAge(30);configureProduct(false);
        var snapshot=metricsSnapshots.collect(day(30));assertThat(snapshot.blocked().get("first_activation")).isEqualTo(1);
        assertThat(snapshot.eligible().get("D11")).isZero();
        jdbc.update("delete from personalization_fields where rule_id in (select id from personalization_rules where store_id=?)",STORE);
        jdbc.update("update stores set store_email='invalid' where store_id=?",STORE);
        snapshot=metricsSnapshots.collect(day(30));assertThat(snapshot.blocked().get("invalid_recipient")).isEqualTo(1);
        assertThat(rows()).isEmpty();
    }

}
