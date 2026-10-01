package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.WinbackEmailEvent;
import br.com.nuvemcustomfields.properties.SesEventsProperties;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

@Service
public class WinbackSesEventService {
    private static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("Send", "send"), Map.entry("Delivery", "delivery"), Map.entry("Open", "open"),
            Map.entry("Click", "click"), Map.entry("Bounce", "bounce"), Map.entry("Complaint", "complaint"),
            Map.entry("Reject", "reject"), Map.entry("Rendering Failure", "failure"),
            Map.entry("DeliveryDelay", "deliveryDelay"), Map.entry("Subscription", "subscription"));
    private final ObjectMapper json;
    private final WinbackCampaignRepository campaigns;
    private final WinbackEmailRepository emails;
    private final WinbackEmailEventRepository events;
    private final StoreRepository stores;
    private final SesEventsProperties properties;
    public WinbackSesEventService(ObjectMapper json, WinbackCampaignRepository campaigns,
            WinbackEmailRepository emails, WinbackEmailEventRepository events,
            StoreRepository stores, SesEventsProperties properties) {
        this.json = json; this.campaigns = campaigns; this.emails = emails; this.events = events;
        this.stores = stores; this.properties = properties;
    }

    /** Only used by the authenticated SQS consumer, never exposed as a public webhook. */
    @Transactional
    public void ingest(String body) throws Exception {
        JsonNode root = json.readTree(body);
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Evento SES inválido.");
        if ("Notification".equals(root.path("Type").asText())) root = json.readTree(root.path("Message").asText());
        String type = root.path("eventType").asText(root.path("notificationType").asText());
        String section = SECTIONS.get(type);
        if (section == null) return;
        var mail = root.path("mail");
        var tags = mail.path("tags");
        String campaignId = tags.path("winback_campaign").path(0).asText();
        String emailId = tags.path("winback_email").path(0).asText();
        if (campaignId.isEmpty() || emailId.isEmpty()) return; // Shared queue may contain support emails.
        if (!properties.configurationSet().equals(tags.path("ses:configuration-set").path(0).asText())) return;
        var storeId = campaigns.storeIdFor(campaignId).orElse(null);
        if (storeId == null) return;
        // Store lock serializes events, duplicate deliveries, sends and store/redact.
        if (stores.findByStoreIdForUpdate(storeId).isEmpty()) return;
        var campaign = campaigns.findById(campaignId).orElse(null);
        var email = emails.findById(emailId).orElse(null);
        if (campaign == null || email == null || !campaignId.equals(email.getCampaignId())) return;
        String messageId = mail.path("messageId").asText();
        if (messageId.isBlank() || messageId.length() > 200) throw new IllegalArgumentException("ID SES inválido.");
        if (email.getSesMessageId() != null && !email.getSesMessageId().equals(messageId)) return;
        // SES Send/Reject records may omit a section timestamp: use the mail timestamp.
        Instant at = Instant.parse(root.path(section).path("timestamp").asText(mail.path("timestamp").asText()));
        String link = "Click".equals(type) ? root.path("click").path("link").asText() : "";
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (emailId + "\n" + messageId + "\n" + type + "\n" + at + "\n" + link)
                        .getBytes(StandardCharsets.UTF_8)));
        if (events.existsById(fingerprint)) return;
        events.save(new WinbackEmailEvent(fingerprint, emailId, type, at, target(link)));
        email.acceptedBySes(messageId, Instant.parse(mail.path("timestamp").asText()));
        if ("Complaint".equals(type) || "Bounce".equals(type) || "Subscription".equals(type)) campaign.optOut();
    }

    private String target(String link) {
        if (link.isEmpty()) return null;
        try {
            String path = java.net.URI.create(link).getPath();
            if (path != null && path.startsWith("/winback/")) return "FEEDBACK";
        } catch (IllegalArgumentException ignored) { }
        return "OTHER";
    }
}
