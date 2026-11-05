package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.junit.jupiter.api.*;
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
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StoreAdminEmailService mail;
    Instant installed;
    @BeforeEach void setup() {
        installed=Instant.now().minus(Duration.ofDays(7)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var s=new Store();s.setStoreId(STORE);s.setStoreCountryCode("BR");s.setStoreName("private-owner-name");
        s.setStoreEmail("owner-sensitive@example.com");ReflectionTestUtils.setField(s,"installedAt",installed);stores.saveAndFlush(s);
        ReflectionTestUtils.setField(job,"enabled",true);ReflectionTestUtils.setField(job,"dryRun",false);
        ReflectionTestUtils.setField(job,"suppressionReady",true);ReflectionTestUtils.setField(job,"allowlistText",String.valueOf(STORE));
        ReflectionTestUtils.setField(job,"startAtText",installed.minusSeconds(86400).toString());
    }
    @AfterEach void cleanup() {
        ReflectionTestUtils.setField(job,"enabled",false);
        jdbc.update("delete from nc39_activation_email_outbox where store_id=?",STORE);
        jdbc.update("delete from personalization_fields where rule_id in (select id from personalization_rules where store_id=?)",STORE);
        jdbc.update("delete from personalization_rules where store_id=?",STORE);
        jdbc.update("delete from personalization_field_history where store_id=?",STORE);
        jdbc.update("delete from stores where store_id=?",STORE);
    }
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
        verify(mail,times(3)).send(eq(STORE),anyString(),anyString());
        assertThat(output).contains("nc39.email.queued","step=D11","nc39.email.claimed","nc39.email.sent")
                .doesNotContain("owner-sensitive@example.com","private-owner-name");
    }
    @Test void firstSavedConfigurationStopsFutureStepsEvenIfLaterDisabled() {
        job.scheduleAt(day(3));configureProduct(false);job.scheduleAt(day(7));
        assertThat(row("ACTIVATED").getState()).isEqualTo(ActivationEmailOutbox.State.ACTIVATED);
        var r=rules.findByStoreIdAndProductId(STORE,10L).orElseThrow();r.setEnabled(false);rules.saveAndFlush(r);
        job.scheduleAt(day(11));verify(mail,times(1)).send(eq(STORE),anyString(),anyString());
        assertThat(rows()).extracting(ActivationEmailOutbox::getStep).containsExactlyInAnyOrder("D3","ACTIVATED");
    }
    @Test void pausedConfigurationDoesNotGetMistakenForNeverConfigured() {
        configureProduct(true);job.scheduleAt(day(3));assertThat(row("ACTIVATED").getState()).isEqualTo(ActivationEmailOutbox.State.ACTIVATED);
        verifyNoInteractions(mail);
    }
    @Test void deletedConfigurationHistoryStopsTheSequence() {
        history.saveAndFlush(new PersonalizationFieldHistory(STORE,10L,"Nome antigo"));
        job.scheduleAt(day(3));assertThat(row("ACTIVATED").getState()).isEqualTo(ActivationEmailOutbox.State.ACTIVATED);verifyNoInteractions(mail);
    }
    @Test void configurationBeforeDeliverySuppressesAlreadyPendingMessages() {
        ReflectionTestUtils.setField(job,"suppressionReady",false);job.scheduleAt(day(3));pending("D7",7);
        configureProduct(false);job.scheduleAt(day(7));
        assertThat(row("D3").getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);
        assertThat(row("D7").getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);verifyNoInteractions(mail);
    }
    @Test void queuedD15IsSuppressedAndNeverConvertedIntoALateD11Email() {
        var e=pending("D15",15);job.dispatch(e.getId(),day(15));
        assertThat(queue.findById(e.getId()).orElseThrow().getSuppressionReason()).isEqualTo("d15_temporarily_disabled");verifyNoInteractions(mail);
    }
    @Test void mismatchedInstallationAndUninstallSuppressPendingMessages() {
        var e=queue.saveAndFlush(new ActivationEmailOutbox(STORE,installed.minusSeconds(1),"D3",day(3),"nc39-v1"));
        var s=stores.findByStoreId(STORE).orElseThrow();
        job.dispatch(e.getId(),day(3));assertThat(queue.findById(e.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);
        var e7=new ActivationEmailOutbox(STORE,s.getInstalledAt(),"D7",day(7),"nc39-v1");e7=queue.saveAndFlush(e7);
        s.setUninstalledAt(day(4));stores.saveAndFlush(s);job.dispatch(e7.getId(),day(7));
        assertThat(queue.findById(e7.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.SUPPRESSED);verifyNoInteractions(mail);
    }
    @Test void smtpTimeoutLeavesReviewAndNeverRetriesAutomatically() {
        doThrow(new IllegalStateException("SMTP uncertain")).when(mail).send(any(),any(),any());
        job.scheduleAt(day(3));job.dispatchDue(day(3).plusSeconds(120));
        assertThat(row("D3").getState()).isEqualTo(ActivationEmailOutbox.State.REVIEW);assertThat(row("D3").getAttempts()).isEqualTo(1);
        verify(mail,times(1)).send(eq(STORE),anyString(),anyString());
    }
    @Test void concurrentWorkersSeeCommittedClaimAndDoNotHoldTransactionDuringSmtp() throws Exception {
        var e=pending("D3",3);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(queue.findById(e.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.CLAIMED);
            entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return null;
        }).when(mail).send(eq(STORE),anyString(),anyString());
        try(var workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->job.dispatch(e.getId(),day(3)));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                workers.submit(()->job.dispatch(e.getId(),day(3))).get(5,TimeUnit.SECONDS);
            } finally { release.countDown(); }
            first.get(5,TimeUnit.SECONDS);
        }
        assertThat(queue.findById(e.getId()).orElseThrow().getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        verify(mail,times(1)).send(eq(STORE),anyString(),anyString());
    }
    @Test void concurrentSchedulersCreateOnlyOneInstallationStep() throws Exception {
        try(var workers=Executors.newFixedThreadPool(2)) {
            var a=workers.submit(()->job.scheduleAt(day(3)));var b=workers.submit(()->job.scheduleAt(day(3)));
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(rows()).hasSize(1);assertThat(row("D3").getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        verify(mail,times(1)).send(eq(STORE),anyString(),anyString());
    }
    @Test void lgpdErasureDeletesTheActivationQueue() {
        pending("D3",3);
        var s=stores.findByStoreId(STORE).orElseThrow();s.setDepartureCounted(true);stores.saveAndFlush(s);
        erasure.erase(STORE);assertThat(rows()).isEmpty();
        assertThat(stores.findByStoreId(STORE)).isEmpty();verifyNoInteractions(mail);
    }
    @Test void reinstallWithOriginalInstallDateDoesNotRestartAlreadySentSteps() {
        job.scheduleAt(day(3));
        var s=stores.findByStoreId(STORE).orElseThrow();s.setUninstalledAt(day(4));stores.saveAndFlush(s);
        s.setUninstalledAt(null);stores.saveAndFlush(s);job.scheduleAt(day(3));
        assertThat(stores.findByStoreId(STORE).orElseThrow().getInstalledAt()).isEqualTo(installed);
        assertThat(rows()).hasSize(1);verify(mail,times(1)).send(eq(STORE),anyString(),anyString());
    }
}
