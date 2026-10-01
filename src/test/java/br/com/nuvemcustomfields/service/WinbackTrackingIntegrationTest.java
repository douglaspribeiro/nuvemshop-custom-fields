package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"notifications.ses.host=smtp.example.test", "notifications.ses.port=587",
        "notifications.ses.username=test", "notifications.ses.password=test", "notifications.ses.from=support@example.test",
        "notifications.ses-events.configuration-set=tracking-test"})
@AutoConfigureMockMvc
@Transactional
class WinbackTrackingIntegrationTest {
    private static final long STORE_ID = 991122330L;
    @Autowired StoreRepository stores;
    @Autowired WinbackOutboxRepository outbox;
    @Autowired WinbackCampaignRepository campaigns;
    @Autowired WinbackEmailRepository emails;
    @Autowired WinbackEmailEventRepository events;
    @Autowired WinbackTrackingService tracking;
    @Autowired WinbackQueueService queue;
    @Autowired WinbackCampaignService service;
    @Autowired WinbackSesEventService ses;
    @Autowired StoreDataErasureService erasure;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager em;
    @MockBean JavaMailSender sender;
    Store store;
    WinbackCampaign campaign;
    WinbackOutbox event;
    MimeMessage message;

    @BeforeEach
    void setup() {
        store = new Store(); store.setStoreId(STORE_ID); store.setStoreName("Loja de reconquista");
        store.setStoreCountryCode("BR"); store.setStoreEmail("private@example.test");
        store.setUninstalledAt(Instant.now().minusSeconds(1000).truncatedTo(ChronoUnit.MICROS));
        stores.saveAndFlush(store);
        campaign = tracking.record(store);
        event = new WinbackOutbox(STORE_ID, store.getUninstalledAt()); event.published(); outbox.saveAndFlush(event);
        message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
    }

    @Test
    void disabledDispatchStillRecordsOneCampaignAndDoesNotEnqueue() {
        queue.enqueue(store); queue.enqueue(store);
        assertThat(campaigns.findByStoreIdAndUninstalledAt(STORE_ID, store.getUninstalledAt()))
                .get().extracting(WinbackCampaign::getId).isEqualTo(campaign.getId());
        assertThat(campaigns.count()).isEqualTo(1);
        verifyNoInteractions(sender);
    }

    @Test
    void sendsFeedbackOnceWithTrackingTagsAndCollectsReason() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        assertThat(service.prepare(event.getId())).isNull();
        verify(sender, times(1)).send(message);
        assertThat(message.getHeader("X-SES-CONFIGURATION-SET", null)).isEqualTo("tracking-test");
        assertThat(message.getHeader("X-SES-MESSAGE-TAGS", null))
                .contains("winback_campaign=" + campaign.getId(), "winback_email=" + id)
                .doesNotContain("private", String.valueOf(STORE_ID));
        assertThat(emails.findById(id).orElseThrow().getSentAt()).isNotNull();
        mvc.perform(get("/winback/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Como podemos melhorar?")));
        mvc.perform(post("/winback/" + id).param("reason", "MISSING_FEATURE").param("response", "Preciso de upload"))
                .andExpect(status().isOk());
        assertThat(campaign.getReason()).isEqualTo("MISSING_FEATURE");
        assertThat(campaign.getResponse()).isEqualTo("Preciso de upload");
    }

    @Test
    void smtpFailureKeepsClaimAndDuplicateDoesNotResend() {
        String id = service.prepare(event.getId());
        doThrow(new MailSendException("simulated")).when(sender).send(any(MimeMessage.class));
        assertThatThrownBy(() -> service.send(id)).isInstanceOf(MailSendException.class);
        assertThat(emails.findById(id).orElseThrow().getStatus()).isEqualTo("SENDING");
        assertThat(service.prepare(event.getId())).isNull();
        verify(sender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void reinstallBetweenClaimAndSendCancelsEmail() throws Exception {
        String id = service.prepare(event.getId());
        store.setUninstalledAt(null); stores.saveAndFlush(store); tracking.reinstalled(STORE_ID);
        service.send(id);
        assertThat(emails.findById(id).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(campaign.getReinstalledAt()).isNotNull();
        verify(sender, never()).send(any(MimeMessage.class));
    }

    @Test
    void consumesRawAndSnsEventsDeduplicatesAndKeepsOutOfOrderTimeline() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        String click = sesBody(id, "Click", "2026-09-30T12:03:00Z");
        ses.ingest(json.writeValueAsString(Map.of("Type", "Notification", "Message", click)));
        ses.ingest(click);
        ses.ingest(sesBody(id, "Open", "2026-09-30T12:02:00Z"));
        ses.ingest(sesBody(id, "Delivery", "2026-09-30T12:01:00Z"));
        var timeline = tracking.detail(campaign.getId()).events();
        assertThat(timeline).extracting(WinbackEmailEvent::getType).containsExactly("Delivery", "Open", "Click");
        assertThat(timeline.getLast().getTarget()).isEqualTo("FEEDBACK");
        assertThat(json.writeValueAsString(timeline)).doesNotContain("secret-query", "private@example", "127.0.0.1");
        assertThat(emails.findById(id).orElseThrow().getSesMessageId()).isEqualTo("ses-provider-id");
    }

    @Test
    void complaintSuppressesFutureCampaignsForTheStore() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        ses.ingest(sesBody(id, "Complaint", "2026-09-30T12:04:00Z"));
        assertThat(campaign.getOptedOutAt()).isNotNull();
        store.setUninstalledAt(Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MICROS)); stores.saveAndFlush(store);
        tracking.record(store);
        var next = new WinbackOutbox(STORE_ID, store.getUninstalledAt()); next.published(); outbox.saveAndFlush(next);
        assertThat(service.prepare(next.getId())).isNull();
    }

    @Test
    void ignoresUnrelatedEventsAndRetriesMalformedKnownEvents() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        ses.ingest(sesBody(id, "Open", "2026-09-30T12:02:00Z").replace("tracking-test", "other-set"));
        ses.ingest(sesBody(id, "Click", "2026-09-30T12:03:00Z").replace(id, "unknown-email"));
        assertThat(events.count()).isZero();
        assertThatThrownBy(() -> ses.ingest("{not-json")).isInstanceOf(Exception.class);
    }

    @Test
    void erasureDeletesCampaignEmailAndEventsAndLateSesDoesNotRestoreThem() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        String body = sesBody(id, "Open", "2026-09-30T12:02:00Z"); ses.ingest(body);
        erasure.erase(STORE_ID); em.clear(); ses.ingest(body);
        assertThat(campaigns.findById(campaign.getId())).isEmpty();
        assertThat(emails.findById(id)).isEmpty(); assertThat(events.count()).isZero();
        mvc.perform(get("/winback/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void listAndDetailsRequireBackofficeAndShowDetectedEvents() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        ses.ingest(sesBody(id, "Open", "2026-09-30T12:02:00Z"));
        mvc.perform(get("/backoffice/winback")).andExpect(redirectedUrl("/backoffice/login"));
        var session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        mvc.perform(get("/backoffice/winback").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Loja de reconquista")))
                .andExpect(content().string(containsString("Abertura detectada")));
        mvc.perform(get("/backoffice/winback/" + campaign.getId()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Linha do tempo")))
                .andExpect(content().string(containsString("Abertura detectada")));
    }

    @Test
    void invalidFeedbackCannotChangeCampaignAndOptOutWorks() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        mvc.perform(post("/winback/" + id).param("reason", "invalid")).andExpect(status().isBadRequest());
        assertThat(campaign.getRespondedAt()).isNull();
        mvc.perform(post("/winback/" + id).param("optOut", "true")).andExpect(status().isOk());
        assertThat(campaign.getOptedOutAt()).isNotNull();
    }

    @Test
    void filtersCampaignsByDetectedActivityStoreAndDateRange() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        ses.ingest(sesBody(id, "Click", "2026-09-30T12:03:00Z"));
        em.flush();
        assertThat(tracking.list(0, "reconquista", "click", null, null).getTotalElements()).isEqualTo(1);
        assertThat(tracking.list(0, "reconquista", "open", null, null).getTotalElements()).isZero();
        assertThat(tracking.list(0, "other-store", "click", null, null).getTotalElements()).isZero();
        var day = store.getUninstalledAt().atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate();
        assertThat(tracking.list(0, "", "", day, day).getTotalElements()).isEqualTo(1);
        assertThat(tracking.list(0, "", "", day.minusDays(1), day.minusDays(1)).getTotalElements()).isZero();
        var session = new MockHttpSession(); session.setAttribute(BackofficeSessionInterceptor.SESSION_KEY, true);
        mvc.perform(get("/backoffice/winback").param("stage", "click").param("q", "reconquista")
                .param("from", day.toString()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Loja de reconquista")));
    }

    @Test
    void expiredFeedbackStillAllowsOptOut() throws Exception {
        String id = service.prepare(event.getId()); service.send(id);
        var mail = emails.findById(id).orElseThrow();
        org.springframework.test.util.ReflectionTestUtils.setField(mail, "createdAt", Instant.now().minus(31, ChronoUnit.DAYS));
        em.flush();
        mvc.perform(get("/winback/" + id)).andExpect(status().isOk())
                .andExpect(content().string(containsString("O prazo para responder terminou")));
        mvc.perform(post("/winback/" + id).param("reason", "PRICE")).andExpect(status().isGone());
        mvc.perform(post("/winback/" + id).param("optOut", "true")).andExpect(status().isOk());
        assertThat(campaign.getOptedOutAt()).isNotNull();
    }

    private String sesBody(String id, String type, String at) throws Exception {
        String section = type.substring(0, 1).toLowerCase() + type.substring(1);
        return json.writeValueAsString(Map.of("eventType", type,
                "mail", Map.of("messageId", "ses-provider-id", "timestamp", "2026-09-30T12:00:00Z",
                    "destination", new String[]{"private@example.test"}, "tags", Map.of(
                        "winback_campaign", new String[]{campaign.getId()}, "winback_email", new String[]{id},
                        "ses:configuration-set", new String[]{"tracking-test"})),
                section, Map.of("timestamp", at, "link", "http://localhost:8080/winback/" + id + "?secret-query=true",
                        "ipAddress", "127.0.0.1")));
    }
}
