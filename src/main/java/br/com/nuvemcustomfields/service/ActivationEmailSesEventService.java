package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.properties.SesEventsProperties;
import com.fasterxml.jackson.databind.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class ActivationEmailSesEventService {
    private static final Logger log = LoggerFactory.getLogger(ActivationEmailSesEventService.class);
    private final ObjectMapper json;
    private final ActivationEmailOutboxRepository outbox;
    private final ActivationEmailFeedbackRepository feedback;
    private final StoreRepository stores;
    private final ActivationEmailProtectionService protection;
    private final SesEventsProperties properties;
    public ActivationEmailSesEventService(ObjectMapper json, ActivationEmailOutboxRepository outbox,
            ActivationEmailFeedbackRepository feedback, StoreRepository stores,
            ActivationEmailProtectionService protection, SesEventsProperties properties) {
        this.json = json; this.outbox = outbox; this.feedback = feedback; this.stores = stores;
        this.protection = protection; this.properties = properties;
    }
    /** Trusted SQS consumer only. No anonymous/public SES endpoint. */
    @Transactional
    public void ingest(String body) throws Exception {
        JsonNode root = json.readTree(body);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid SES event");
        if ("Notification".equals(root.path("Type").asText())) root = json.readTree(root.path("Message").asText());
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid SES notification");
        String type = root.path("eventType").asText(root.path("notificationType").asText());
        if (!Set.of("Send", "Delivery", "Bounce", "Complaint", "Subscription").contains(type)) return;
        var mail = root.path("mail"); var tags = mail.path("tags");
        if (properties.configurationSet() == null || properties.configurationSet().isBlank()
                || !properties.configurationSet().equals(tags.path("ses:configuration-set").path(0).asText())) return;
        String eventTag = tags.path("nc39_event").path(0).asText();
        String storeTag = tags.path("nc39_store").path(0).asText();
        if (!eventTag.matches("[1-9][0-9]{0,17}") || !storeTag.matches("[1-9][0-9]{0,17}")) return;
        Long id = Long.valueOf(eventTag), storeId = Long.valueOf(storeTag);
        if (!outbox.storeIdFor(id).filter(storeId::equals).isPresent()) return;
        if (stores.findByStoreIdForUpdate(storeId).isEmpty()) return;
        var event = outbox.findForUpdate(id).orElse(null);
        if (event == null || event.getRecipientHash() == null || !Set.of(ActivationEmailOutbox.State.CLAIMED,
                ActivationEmailOutbox.State.SENT, ActivationEmailOutbox.State.REVIEW,
                ActivationEmailOutbox.State.SUPPRESSED).contains(event.getState())) return;
        // Only a single-recipient message matching the snapshot taken at claim is accepted.
        var destinations = mail.path("destination");
        if (!destinations.isArray() || destinations.size() != 1 || !matches(event, destinations.path(0).asText())) return;
        String messageId = mail.path("messageId").asText();
        if (messageId.isBlank() || messageId.length() > 200) throw new IllegalArgumentException("Invalid SES message id");
        if (event.getSesMessageId() != null && !event.getSesMessageId().equals(messageId)) return;
        String section = type.toLowerCase(Locale.ROOT);
        var detail = root.path(section);
        if ("Bounce".equals(type) && !includes(event, detail.path("bouncedRecipients"))) return;
        // Some complaint feedback loops omit individual recipients; the unique destination still binds the message.
        if ("Complaint".equals(type) && detail.path("complainedRecipients").size() > 0
                && !includes(event, detail.path("complainedRecipients"))) return;
        Instant at = Instant.parse(detail.path("timestamp").asText(mail.path("timestamp").asText()));
        String fingerprint = ActivationEmailProtectionService.hash(id + "\n" + messageId + "\n" + type + "\n" + at);
        if (feedback.existsById(fingerprint)) return;
        feedback.save(new ActivationEmailFeedback(fingerprint, storeId, id, type, at));
        event.acceptedBySes(messageId);
        String reason = switch (type) {
            case "Bounce" -> "ses_bounce";
            case "Complaint" -> "ses_complaint";
            case "Subscription" -> optedOut(detail) ? "ses_unsubscribe" : null;
            default -> null;
        };
        if (reason != null) protection.suppress(storeId, reason, at);
        log.info("nc39.ses.feedback store_id={} event_id={} type={}", storeId, id, type);
    }
    private boolean matches(ActivationEmailOutbox event, String address) {
        return event.getRecipientHash().equals(ActivationEmailProtectionService.recipientHash(address));
    }
    private boolean includes(ActivationEmailOutbox event, JsonNode recipients) {
        for (var recipient : recipients) if (matches(event, recipient.path("emailAddress").asText())) return true;
        return false;
    }
    private boolean optedOut(JsonNode detail) {
        var preferences = detail.path("newTopicPreferences");
        if (preferences.path("unsubscribeAll").asBoolean(false)) return true;
        for (var topic : preferences.path("topicSubscriptionStatus"))
            if ("OptOut".equals(topic.path("subscriptionStatus").asText())) return true;
        return false;
    }
}
