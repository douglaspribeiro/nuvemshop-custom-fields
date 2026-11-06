package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import br.com.nuvemcustomfields.properties.SesEventsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class ActivationEmailProtectionService {
    private static final Logger log = LoggerFactory.getLogger(ActivationEmailProtectionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ActivationEmailSuppressionRepository suppressions;
    private final ActivationEmailOutboxRepository outbox;
    private final StoreRepository stores;
    private final WinbackCampaignRepository winback;
    private final SesEventsProperties events;
    private final String baseUrl;
    public ActivationEmailProtectionService(ActivationEmailSuppressionRepository suppressions,
            ActivationEmailOutboxRepository outbox, StoreRepository stores, WinbackCampaignRepository winback, SesEventsProperties events,
            @Value("${nuvemshop.app-base-url}") String baseUrl) {
        this.suppressions = suppressions; this.outbox = outbox; this.stores = stores;
        this.winback = winback; this.events = events; this.baseUrl = baseUrl.replaceAll("/+$", "");
    }
    public boolean ready() {
        try {
            var uri = java.net.URI.create(baseUrl);
            var queue = java.net.URI.create(events.queueUrl());
            return events.enabled() && "https".equals(queue.getScheme()) && queue.getHost() != null
                    && queue.getUserInfo() == null && queue.getQuery() == null && queue.getFragment() == null
                    && queue.getPath() != null && queue.getPath().length() > 1 && hasText(events.region())
                    && events.configurationSet() != null && events.configurationSet().matches("[A-Za-z0-9_-]{1,64}") && "https".equals(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
        } catch (RuntimeException ex) { return false; }
    }
    private boolean hasText(String s) { return s != null && !s.isBlank(); }
    public String reason(Long storeId) {
        return suppressions.findById(storeId).map(ActivationEmailSuppression::getReason)
                .orElseGet(() -> winback.existsByStoreIdAndOptedOutAtIsNotNull(storeId) ? "previous_email_opt_out" : null);
    }
    /** Caller holds the store lock. The token itself is never persisted or logged. */
    public String prepare(ActivationEmailOutbox event, String recipient) {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        event.prepareDelivery(hash(token), recipientHash(recipient));
        return baseUrl + "/activation-emails/unsubscribe?token=" + token;
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static String recipientHash(String value) {
        return value == null ? null : hash(value.strip().toLowerCase(Locale.ROOT));
    }
    @Transactional(readOnly = true)
    public boolean spanish(String token) {
        var event = tokenEvent(token);
        var store = stores.findByStoreId(event.getStoreId()).orElseThrow(ActivationEmailProtectionService::invalidLink);
        return "es".equals(br.com.nuvemcustomfields.i18n.StoreLocale.forCountry(store.getStoreCountryCode()).getLanguage());
    }
    @Transactional
    public void unsubscribe(String token) {
        var candidate = tokenEvent(token);
        if (stores.findByStoreIdForUpdate(candidate.getStoreId()).isEmpty()) throw invalidLink();
        // Erasure and SES feedback use the same store lock before touching the queue.
        if (outbox.findByUnsubscribeTokenHash(hash(token)).isEmpty()) throw invalidLink();
        suppress(candidate.getStoreId(), "unsubscribe", Instant.now());
    }
    private ActivationEmailOutbox tokenEvent(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidLink();
        return outbox.findByUnsubscribeTokenHash(hash(token)).orElseThrow(ActivationEmailProtectionService::invalidLink);
    }
    private static ResponseStatusException invalidLink() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Link inválido ou indisponível."); }
    /** Caller holds the store lock. First cause is retained; repeated events are idempotent. */
    public void suppress(Long storeId, String reason, Instant at) {
        if (!Set.of("unsubscribe", "ses_bounce", "ses_complaint", "ses_unsubscribe").contains(reason))
            throw new IllegalArgumentException("Invalid suppression reason");
        if (!suppressions.existsById(storeId)) {
            suppressions.save(new ActivationEmailSuppression(storeId, reason, at));
            log.info("nc39.suppression.recorded store_id={} reason={}", storeId, reason);
        }
        outbox.findByStoreIdAndState(storeId, ActivationEmailOutbox.State.PENDING).forEach(e -> e.suppress(reason));
    }
}
