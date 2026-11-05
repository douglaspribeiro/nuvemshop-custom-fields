package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.properties.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"nuvemshop.app-base-url=https://app.example.com", "activation.email.schedule-delay-ms=86400000"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ActivationEmailProtectionIntegrationTest {
    static final long STORE = 990039068L;
    static final String ADDRESS = "nc39-private-owner@example.com";
    @Autowired ActivationEmailJob job;
    @Autowired ActivationEmailProtectionService protection;
    @Autowired ActivationEmailSesEventService feedback;
    @Autowired StoreRepository stores;
    @Autowired WinbackCampaignRepository winback;
    @Autowired ActivationEmailOutboxRepository outbox;
    @Autowired ActivationEmailSuppressionRepository suppressions;
    @Autowired ActivationEmailFeedbackRepository receipts;
    @Autowired StoreDataErasureService erasure;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender sender;
    @MockitoBean SesEventsProperties events;
    @MockitoBean SesProperties smtp;
    Instant installed;

    @BeforeEach void setup() {
        when(events.enabled()).thenReturn(true); when(events.configurationSet()).thenReturn("config");
        when(events.queueUrl()).thenReturn("https://sqs.example.com/queue");when(events.region()).thenReturn("us-east-1");
        when(smtp.configured()).thenReturn(true); when(smtp.from()).thenReturn("support@example.com");
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        installed = Instant.now().minus(Duration.ofDays(3)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var store = new Store();store.setStoreId(STORE);store.setStoreEmail(ADDRESS);store.setStoreCountryCode("BR");
        ReflectionTestUtils.setField(store,"installedAt",installed);stores.saveAndFlush(store);
        ReflectionTestUtils.setField(job,"enabled",true);ReflectionTestUtils.setField(job,"dryRun",false);
        ReflectionTestUtils.setField(job,"suppressionReady",true);ReflectionTestUtils.setField(job,"allowlistText",String.valueOf(STORE));
        ReflectionTestUtils.setField(job,"startAtText",installed.minusSeconds(86400).toString());
    }
    @AfterEach void cleanup() {
        ReflectionTestUtils.setField(job,"enabled",false);
        jdbc.update("delete from nc39_activation_email_feedback where store_id=?",STORE);
        jdbc.update("delete from nc39_activation_email_suppressions where store_id=?",STORE);
        jdbc.update("delete from nc39_activation_email_outbox where store_id=?",STORE);
        jdbc.update("delete from winback_campaigns where store_id=?",STORE);
        jdbc.update("delete from stores where store_id=?",STORE);
    }
    Instant day(int day) { return installed.plus(Duration.ofDays(day)); }
    ActivationEmailOutbox first() {
        return outbox.findAll().stream().filter(e -> e.getStoreId()==STORE && e.getStep().equals("D3")).findFirst().orElseThrow();
    }
    ActivationEmailOutbox sendFirst() { job.scheduleAt(day(3)); return first(); }
    ActivationEmailOutbox pending(String step,int day) {
        return outbox.saveAndFlush(new ActivationEmailOutbox(STORE,installed,step,day(day),"nc39-v1"));
    }
    MimeMessage message() {
        var capture = ArgumentCaptor.forClass(MimeMessage.class);verify(sender).send(capture.capture());return capture.getValue();
    }
    String token() throws Exception {
        String url = message().getHeader("List-Unsubscribe",null);
        return url.substring(url.indexOf("token=")+6,url.length()-1);
    }
    ObjectNode event(String type) {
        var root=json.createObjectNode().put("eventType",type);
        var mail=root.putObject("mail").put("messageId","ses-provider-1").put("timestamp",day(3).toString());
        mail.putArray("destination").add(ADDRESS);
        var tags=mail.putObject("tags");tags.putArray("ses:configuration-set").add("config");
        tags.putArray("nc39_event").add(first().getId().toString());tags.putArray("nc39_store").add(Long.toString(STORE));
        var detail=root.putObject(type.toLowerCase(Locale.ROOT)).put("timestamp",day(3).plusSeconds(2).toString());
        if (type.equals("Bounce")) {detail.put("bounceType","Permanent");detail.putArray("bouncedRecipients").addObject().put("emailAddress",ADDRESS);}
        if (type.equals("Complaint")) detail.putArray("complainedRecipients").addObject().put("emailAddress",ADDRESS);
        if (type.equals("Subscription")) detail.putObject("newTopicPreferences").put("unsubscribeAll",true);
        return root;
    }
    @Test void realMimeTransportIncludesCorrelatedSesTagsAndUnsubscribeWithoutLeakingToken(CapturedOutput logs) throws Exception {
        var row=sendFirst();var message=message();
        assertThat(row.getState()).isEqualTo(ActivationEmailOutbox.State.SENT);
        assertThat(message.getHeader("X-SES-CONFIGURATION-SET",null)).isEqualTo("config");
        assertThat(message.getHeader("X-SES-MESSAGE-TAGS",null)).isEqualTo("nc39_event="+row.getId()+", nc39_store="+STORE);
        assertThat(message.getHeader("List-Unsubscribe-Post",null)).isEqualTo("List-Unsubscribe=One-Click");
        String token=token(); assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(message.getContent().toString()).contains("Parar de receber", "https://app.example.com/activation-emails/unsubscribe?token="+token);
        assertThat(jdbc.queryForObject("select unsubscribe_token_hash from nc39_activation_email_outbox where id=?",String.class,row.getId()))
                .isEqualTo(ActivationEmailProtectionService.hash(token)).isNotEqualTo(token);
        assertThat(logs).doesNotContain(ADDRESS,token);
    }
    @Test void getOnlyConfirmsAndPostImmediatelyCancelsRemainingStagesIdempotently() throws Exception {
        sendFirst();pending("D7",7);pending("D11",11);String token=token();
        mvc.perform(get("/activation-emails/unsubscribe").param("token",token))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Referrer-Policy","no-referrer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Desativar estes e-mails")));
        assertThat(suppressions.findById(STORE)).isEmpty();
        for(int i=0;i<2;i++) mvc.perform(post("/activation-emails/unsubscribe").param("token",token).param("List-Unsubscribe","One-Click"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("E-mails desativados")));
        assertThat(protection.reason(STORE)).isEqualTo("unsubscribe");
        job.scheduleAt(day(7));job.scheduleAt(day(11));
        assertThat(outbox.findByStoreIdAndState(STORE,ActivationEmailOutbox.State.SUPPRESSED)).hasSize(2);
        verify(sender,times(1)).send(any(MimeMessage.class));
        assertThat(stores.findByStoreId(STORE)).isPresent();
    }
    @Test void forgedMissingAndErasedTokensDoNotSuppressAnyone() throws Exception {
        for(String token:List.of("invalid","a".repeat(43))) {
            mvc.perform(get("/activation-emails/unsubscribe").param("token",token)).andExpect(status().isNotFound());
            mvc.perform(post("/activation-emails/unsubscribe").param("token",token)).andExpect(status().isNotFound());
        }
        mvc.perform(post("/activation-emails/unsubscribe")).andExpect(status().isBadRequest());
        assertThat(suppressions.findById(STORE)).isEmpty();verifyNoInteractions(sender);
    }
    @Test void spanishConfirmationAndSuccessUseStoreLocale() throws Exception {
        var store=stores.findByStoreId(STORE).orElseThrow();store.setStoreCountryCode("MX");stores.saveAndFlush(store);
        sendFirst();String token=token();
        mvc.perform(get("/activation-emails/unsubscribe").param("token",token)).andExpect(content().string(org.hamcrest.Matchers.containsString("Desactivar estos correos")));
        mvc.perform(post("/activation-emails/unsubscribe").param("token",token)).andExpect(content().string(org.hamcrest.Matchers.containsString("Correos desactivados")));
    }
    @ParameterizedTest @ValueSource(strings={"Bounce","Complaint","Subscription"})
    void providerFeedbackStopsCurrentAndFutureStagesAndReplayIsIdempotent(String type) throws Exception {
        sendFirst();pending("D7",7);pending("D11",11);var event=event(type);
        feedback.ingest(event.toString());
        var envelope=json.createObjectNode().put("Type","Notification").put("Message",event.toString());
        feedback.ingest(envelope.toString());
        String expected=type.equals("Bounce")?"ses_bounce":type.equals("Complaint")?"ses_complaint":"ses_unsubscribe";
        assertThat(protection.reason(STORE)).isEqualTo(expected);
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(outbox.findByStoreIdAndState(STORE,ActivationEmailOutbox.State.SUPPRESSED)).hasSize(2);
        job.scheduleAt(day(7));job.scheduleAt(day(11));verify(sender,times(1)).send(any(MimeMessage.class));
        assertThat(first().getSesMessageId()).isEqualTo("ses-provider-1");
    }
    @Test void terminalTransientBounceAlsoStopsSequenceButDeliveryDelayDoesNot() throws Exception {
        sendFirst();feedback.ingest(event("DeliveryDelay").toString());assertThat(protection.reason(STORE)).isNull();
        var event=event("Bounce");((ObjectNode)event.path("bounce")).put("bounceType","Transient");
        feedback.ingest(event.toString());assertThat(protection.reason(STORE)).isEqualTo("ses_bounce");
    }
    @Test void complaintWithoutRecipientListStillMatchesUniqueOriginalDestination() throws Exception {
        sendFirst();var event=event("Complaint");((ObjectNode)event.path("complaint")).remove("complainedRecipients");
        feedback.ingest(event.toString());assertThat(protection.reason(STORE)).isEqualTo("ses_complaint");
    }
    @ParameterizedTest @ValueSource(strings={"config","store","event","destination","bounceRecipient","message"})
    void unrelatedOrMismatchedFeedbackCannotSuppressStore(String mismatch) throws Exception {
        sendFirst();feedback.ingest(event("Send").toString());var event=event("Bounce");
        var tags=(ObjectNode)event.path("mail").path("tags");
        switch(mismatch) {
            case "config" -> tags.putArray("ses:configuration-set").add("other");
            case "store" -> tags.putArray("nc39_store").add("123");
            case "event" -> tags.putArray("nc39_event").add("999999");
            case "destination" -> ((ObjectNode)event.path("mail")).putArray("destination").add("other@example.com");
            case "bounceRecipient" -> ((ObjectNode)event.path("bounce")).putArray("bouncedRecipients").addObject().put("emailAddress","other@example.com");
            case "message" -> ((ObjectNode)event.path("mail")).put("messageId","different-id");
        }
        feedback.ingest(event.toString());assertThat(protection.reason(STORE)).isNull();assertThat(receipts.count()).isEqualTo(1);
    }
    @Test void optInAndDelayedDeliveryNeverUndoAnEarlierOptOut() throws Exception {
        sendFirst();var event=event("Subscription");
        ((ObjectNode)event.path("subscription").path("newTopicPreferences")).put("unsubscribeAll",false);
        feedback.ingest(event.toString());assertThat(protection.reason(STORE)).isNull();
        protection.unsubscribe(token());feedback.ingest(event("Delivery").toString());
        feedback.ingest(event.toString());assertThat(protection.reason(STORE)).isEqualTo("unsubscribe");
    }
    @Test void topicOptOutStopsSequence() throws Exception {
        sendFirst();var event=event("Subscription");var preferences=(ObjectNode)event.path("subscription").path("newTopicPreferences");
        preferences.put("unsubscribeAll",false);preferences.putArray("topicSubscriptionStatus").addObject().put("topicName","activation").put("subscriptionStatus","OptOut");
        feedback.ingest(event.toString());assertThat(protection.reason(STORE)).isEqualTo("ses_unsubscribe");
    }
    @Test void unknownSmtpResultCanStillReceiveFeedbackWithoutResending() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("private diagnostics")).when(sender).send(any(MimeMessage.class));
        sendFirst();assertThat(first().getState()).isEqualTo(ActivationEmailOutbox.State.REVIEW);
        feedback.ingest(event("Bounce").toString());job.scheduleAt(day(7));
        assertThat(protection.reason(STORE)).isEqualTo("ses_bounce");verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void disabledFeedbackConsumerPreventsLiveSendEvenWithManualGateEnabled() {
        when(events.enabled()).thenReturn(false);job.scheduleAt(day(3));
        assertThat(first().getState()).isEqualTo(ActivationEmailOutbox.State.PENDING);verifyNoInteractions(sender);
    }
    @Test void suppressionSurvivesReinstallAndStopsNewScheduling() throws Exception {
        sendFirst();protection.unsubscribe(token());var store=stores.findByStoreId(STORE).orElseThrow();
        store.setUninstalledAt(day(4));stores.saveAndFlush(store);store.setUninstalledAt(null);stores.saveAndFlush(store);
        job.scheduleAt(day(7));assertThat(outbox.findByStoreIdAndState(STORE,ActivationEmailOutbox.State.PENDING)).isEmpty();
        verify(sender,times(1)).send(any(MimeMessage.class));
    }
    @Test void lgpdRemovesSuppressionTokensAndFeedbackAndLateEventsCannotRecreateThem() throws Exception {
        sendFirst();String token=token();var event=event("Complaint");feedback.ingest(event.toString());
        var store=stores.findByStoreId(STORE).orElseThrow();store.setDepartureCounted(true);stores.saveAndFlush(store);erasure.erase(STORE);
        assertThat(suppressions.findById(STORE)).isEmpty();assertThat(receipts.count()).isZero();
        feedback.ingest(event.toString());assertThat(receipts.count()).isZero();
        mvc.perform(post("/activation-emails/unsubscribe").param("token",token)).andExpect(status().isNotFound());
    }
    @Test void previousWinbackOptOutAlsoPreventsActivationMessages() {
        var campaign=new WinbackCampaign(STORE,installed.minusSeconds(3600));campaign.optOut();winback.saveAndFlush(campaign);
        job.scheduleAt(day(3));assertThat(protection.reason(STORE)).isEqualTo("previous_email_opt_out");
        assertThat(outbox.findByStoreIdAndState(STORE,ActivationEmailOutbox.State.PENDING)).isEmpty();verifyNoInteractions(sender);
    }
    @ParameterizedTest @ValueSource(strings={"queue","region","configurationSet"})
    void incompleteFeedbackConfigurationPreventsSending(String missing) {
        switch(missing) {
            case "queue" -> when(events.queueUrl()).thenReturn("");
            case "region" -> when(events.region()).thenReturn("");
            case "configurationSet" -> when(events.configurationSet()).thenReturn(null);
        }
        job.scheduleAt(day(3));assertThat(first().getState()).isEqualTo(ActivationEmailOutbox.State.PENDING);verifyNoInteractions(sender);
    }
    @Test void concurrentFeedbackCommitsOneReceiptAndOneSuppression() throws Exception {
        sendFirst();pending("D7",7);String body=event("Bounce").toString();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{feedback.ingest(body);return true;});var b=pool.submit(()->{feedback.ingest(body);return true;});
            assertThat(a.get(10,TimeUnit.SECONDS)).isTrue();assertThat(b.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(receipts.count()).isEqualTo(1);assertThat(protection.reason(STORE)).isEqualTo("ses_bounce");
        assertThat(outbox.findByStoreIdAndState(STORE,ActivationEmailOutbox.State.SUPPRESSED)).hasSize(1);
    }
}
