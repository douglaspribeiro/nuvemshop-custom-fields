package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.ActivationEmailOutbox;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.SesEventsProperties;
import br.com.nuvemcustomfields.repository.ActivationEmailOutboxRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import br.com.nuvemcustomfields.repository.PersonalizationFieldHistoryRepository;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** NC-39 D3/D7/D11 activation sequence. Legacy D15 is paused pending the free-trial decision.
 * Disabled and simulation-only by default. */
@Service
public class ActivationEmailJob {
    private static final Logger log = LoggerFactory.getLogger(ActivationEmailJob.class);
    private static final String VERSION = "nc39-v2";
    private final StoreRepository stores;
    private final PersonalizationRuleRepository rules;
    private final ActivationEmailOutboxRepository outbox;
    private final StoreAdminEmailService mail;
    private final TransactionTemplate tx;
    private final PersonalizationFieldHistoryRepository history;
    private final ActivationEmailProtectionService protection;
    private final SesEventsProperties sesEvents;
    private final ActivationEmailTemplates templates;
    private final ActivationEmailDeliveryMetrics deliveryMetrics;
    private final org.springframework.context.ApplicationEventPublisher events;
    @Value("${activation.email.enabled:false}") private boolean enabled;
    @Value("${activation.email.dry-run:true}") private boolean dryRun;
    @Value("${activation.email.allowlist:}") private String allowlistText;
    @Value("${activation.email.start-at:}") private String startAtText;
    @Value("${activation.email.suppression-ready:false}") private boolean suppressionReady;

    public ActivationEmailJob(StoreRepository stores, PersonalizationRuleRepository rules,
            ActivationEmailOutboxRepository outbox, StoreAdminEmailService mail, PlatformTransactionManager manager,
            PersonalizationFieldHistoryRepository history, ActivationEmailProtectionService protection,
            SesEventsProperties sesEvents, ActivationEmailTemplates templates, ActivationEmailDeliveryMetrics deliveryMetrics, org.springframework.context.ApplicationEventPublisher events) {
        this.stores = stores; this.rules = rules; this.outbox = outbox; this.mail = mail;
        this.tx = new TransactionTemplate(manager);
        this.history = history; this.protection = protection; this.sesEvents = sesEvents; this.templates = templates; this.deliveryMetrics = deliveryMetrics; this.events = events;
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${activation.email.schedule-delay-ms:3600000}",
            initialDelayString = "${activation.email.schedule-delay-ms:3600000}")
    public void schedule() {
        scheduleAt(Instant.now());
    }

    void scheduleAt(Instant now) {
        if (!enabled) { log.debug("nc39.schedule.skipped reason=disabled"); return; }
        Instant startAt = startAt();
        Set<Long> allowlist = parseAllowlist();
        log.debug("nc39.schedule.started dry_run={} allowlisted_stores={}", dryRun, allowlist.size());
        for (Store candidate : stores.findAllActive()) {
            try { tx.executeWithoutResult(status -> scheduleStore(candidate.getStoreId(), startAt, now)); }
            catch (RuntimeException ex) { log.warn("nc39.schedule.failed store_id={} type={}", candidate.getStoreId(), ex.getClass().getSimpleName()); }
        }
        dispatchDue(now);
    }

    private void scheduleStore(Long storeId, Instant startAt, Instant now) {
        // Serializes concurrent schedulers before checking the unique installation/step key.
        Store store = stores.findActiveByStoreIdForUpdate(storeId).orElse(null);
        if (store == null) return;
        Instant installed = store.getInstalledAt();
        if (installed == null || store.isErasurePending()) return;
        String blocked = protection.reason(store.getStoreId());
        if (blocked != null) { suppressPending(store, blocked); return; }
        boolean currentConfiguration = rules.hasConfiguredFields(store.getStoreId());
        boolean activated = currentConfiguration || history.existsByStoreId(store.getStoreId())
                || outbox.existsByStoreIdAndStep(store.getStoreId(), "ACTIVATED");
        if (activated && !outbox.existsByStoreIdAndStep(store.getStoreId(), "ACTIVATED")) {
            var event = new ActivationEmailOutbox(store.getStoreId(), installed, "ACTIVATED", now, VERSION);
            event.activated(); outbox.save(event);
            log.info("nc39.activation.detected store_id={} event_id={}", store.getStoreId(), event.getId());
        }
        if (currentConfiguration) { suppressPending(store, "first_activation"); return; }
        if (activated) outbox.findByStoreIdAndInstalledAtAndState(store.getStoreId(), installed, ActivationEmailOutbox.State.PENDING)
                .stream().filter(event -> !event.getStep().equals("D11")).forEach(event -> suppress(event, "first_activation"));
        if (!activated && startAt != null && !installed.isBefore(startAt) && parseAllowlist().contains(store.getStoreId())) {
            enqueueDue(store, installed, now, "D3", Duration.ofHours(72));
            enqueueDue(store, installed, now, "D7", Duration.ofHours(168));
        }
        enqueueDue(store, installed, now, "D11", Duration.ofHours(264));
    }

    private void suppressPending(Store store, String reason) {
        outbox.findByStoreIdAndInstalledAtAndState(store.getStoreId(), store.getInstalledAt(), ActivationEmailOutbox.State.PENDING)
                .forEach(event -> suppress(event, reason));
    }

    private void enqueueDue(Store store, Instant installed, Instant now, String step, Duration delay) {
        Instant due = installed.plus(delay);
        if (due.isAfter(now)) return;
        if (outbox.existsByStoreIdAndInstalledAtAndStep(store.getStoreId(), installed, step)) {
            if ("D11".equals(step)) outbox.findByStoreIdAndInstalledAtAndStep(store.getStoreId(), installed, step)
                    .filter(ActivationEmailOutbox::resumeD11).ifPresent(event ->
                            log.info("nc39.email.d11_resumed store_id={} event_id={}", store.getStoreId(), event.getId()));
            return;
        }
        var event = new ActivationEmailOutbox(store.getStoreId(), installed, step, due, VERSION);
        if (!"D11".equals(step) && now.isAfter(due.plus(Duration.ofHours(24)))) suppress(event, "stage_expired");
        outbox.save(event);
        log.info("nc39.email.queued store_id={} event_id={} step={} due_at={} state={}",
                store.getStoreId(), event.getId(), step, due, event.getState());
    }

    private boolean configured(Long storeId) {
        // D3/D7 stop after the first saved configuration, even if later deleted; D11 checks current fields.
        return rules.hasConfiguredFields(storeId) || history.existsByStoreId(storeId);
    }

    public void dispatchDue(Instant now) {
        if (!enabled) return;
        for (var candidate : outbox.findDue(now, PageRequest.of(0, 50))) {
            try { dispatch(candidate.getId(), now); }
            catch (RuntimeException ex) { log.warn("nc39.dispatch.failed event_id={} type={}", candidate.getId(), ex.getClass().getSimpleName()); }
        }
        events.publishEvent(new br.com.nuvemcustomfields.event.ActivationEmailActivityUpdated());
    }

    public void dispatch(Long id, Instant now) {
        Instant startAt = startAt();
        if (!enabled) return;
        Delivery delivery = tx.execute(status -> claim(id, now, startAt));
        if (delivery == null) return;
        log.info("nc39.email.claimed store_id={} event_id={} step={} attempt={}",
                delivery.storeId(), id, delivery.step(), delivery.attempt());
        try {
            // Feedback committed after the claim still cancels delivery before SMTP begins.
            String blocked = protection.reason(delivery.storeId());
            if (blocked != null) { finish(id, event -> suppress(event, blocked)); return; }
            mail.sendActivation(delivery.storeId(), id, delivery.recipient(), delivery.copy().subject(),
                    delivery.copy().body(), delivery.unsubscribeUrl(), sesEvents.configurationSet());
            deliveryMetrics.sent(delivery.step());
            finish(id, event -> event.sent(now));
            log.info("nc39.email.sent store_id={} event_id={}", delivery.storeId(), id);
        } catch (IllegalArgumentException ex) {
            // Recipient/configuration validation failed before SMTP. Bounded retry is safe.
            finish(id, event -> event.failed(now));
            log.warn("nc39.email.validation_failed store_id={} event_id={} type={}", delivery.storeId(), id, ex.getClass().getSimpleName());
        } catch (RuntimeException ex) {
            // SMTP may already have accepted the email. Never automatically send it again.
            finish(id, ActivationEmailOutbox::review);
            log.warn("nc39.email.review store_id={} event_id={} type={}", delivery.storeId(), id, ex.getClass().getSimpleName());
        }
    }

    private Delivery claim(Long id, Instant now, Instant startAt) {
        Long storeId = outbox.storeIdFor(id).orElse(null);
        if (storeId == null) return null;
        // All workers use store -> event lock ordering, including scheduling and suppression.
        Store store = stores.findActiveByStoreIdForUpdate(storeId).orElse(null);
        var event = outbox.findForUpdate(id).orElse(null);
        if (event == null || event.getState() != ActivationEmailOutbox.State.PENDING || event.getNextAttemptAt().isAfter(now)) return null;
        String reason = candidateReason(store, event, now, startAt);
        if (reason != null) {
            if (!Set.of("not_due", "invalid_start_at").contains(reason)) suppress(event, reason);
            return null;
        }
        if (!dryRun && !protection.ready()) { log.debug("nc39.email.deferred event_id={} reason=feedback_not_configured", id); return null; }
        if (!suppressionReady) { log.debug("nc39.email.deferred event_id={} reason=suppression_not_ready", id); return null; }
        if (dryRun) {
            event.simulated(); log.info("nc39.email.simulated store_id={} step={}", store.getStoreId(), event.getStep()); return null;
        }
        var copy = templates.copy(event.getStep(), store.getStoreCountryCode(), store.getStoreName());
        String unsubscribeUrl = protection.prepare(event, store.getStoreEmail());
        event.templateVersion(VERSION);
        event.claim();
        outbox.saveAndFlush(event); // Committed by REQUIRES_NEW before any SMTP call.
        return new Delivery(store.getStoreId(), copy, event.getStep(), event.getAttempts(), store.getStoreEmail(), unsubscribeUrl);
    }

    public enum AudienceState { ELIGIBLE, WAITING, BLOCKED, HANDLED, REVIEW }
    public record Audience(AudienceState state, String reason) { }
    public Audience audience(Store store, String step, Instant now) {
        var installed = store.getInstalledAt();
        if (installed == null) return new Audience(AudienceState.BLOCKED, "store_inactive_or_reinstalled");
        var existing = outbox.findByStoreIdAndInstalledAtAndStep(store.getStoreId(), installed, step).orElse(null);
        if (existing != null && existing.getState() != ActivationEmailOutbox.State.PENDING) {
            if (existing.getState() == ActivationEmailOutbox.State.REVIEW || existing.getState() == ActivationEmailOutbox.State.CLAIMED
                    || existing.getState() == ActivationEmailOutbox.State.FAILED)
                return new Audience(AudienceState.REVIEW, "review_required");
            boolean resumeD11 = existing.canResumeD11();
            if (!resumeD11) return existing.getState() == ActivationEmailOutbox.State.SUPPRESSED
                    ? new Audience(AudienceState.BLOCKED, existing.getSuppressionReason())
                    : new Audience(AudienceState.HANDLED, "already_handled");
        }
        var event = existing == null ? new ActivationEmailOutbox(store.getStoreId(), installed, step, installed.plus(stageAge(step)), VERSION) : existing;
        String reason = candidateReason(store, event, now, startAt());
        if (reason != null) return new Audience(reason.equals("not_due") ? AudienceState.WAITING : AudienceState.BLOCKED, reason);
        if (existing != null && existing.getState() == ActivationEmailOutbox.State.PENDING && existing.getNextAttemptAt().isAfter(now))
            return new Audience(AudienceState.WAITING, "retry_wait");
        if (!StoreAdminEmailService.validRecipient(store.getStoreEmail())) return new Audience(AudienceState.BLOCKED, "invalid_recipient");
        return new Audience(AudienceState.ELIGIBLE, "eligible");
    }
    public java.util.Map<String, Boolean> controls() {
        boolean feedback = protection.ready(), smtp = mail.configured();
        return java.util.Map.of("enabled", enabled, "dry_run", dryRun, "suppression_ready", suppressionReady,
                "cohort_valid", startAt() != null, "allowlist_nonempty", !parseAllowlist().isEmpty(),
                "feedback_configured", feedback, "smtp_configured", smtp,
                "live_enabled", enabled && !dryRun && suppressionReady && feedback && smtp);
    }

    private String candidateReason(Store store, ActivationEmailOutbox event, Instant now, Instant startAt) {
        if (event.getStep().equals("D15")) return "d15_temporarily_disabled";
        if (!Set.of("D3", "D7", "D11").contains(event.getStep())) return "unsupported_stage";
        boolean d11 = event.getStep().equals("D11");
        if (store == null || store.getInstalledAt() == null || store.isErasurePending()
                || !event.getInstalledAt().equals(store.getInstalledAt())) return "store_inactive_or_reinstalled";
        if (!d11 && startAt == null) return "invalid_start_at";
        if (!d11 && store.getInstalledAt().isBefore(startAt)) return "outside_cohort";
        if (d11 ? rules.hasConfiguredFields(store.getStoreId())
                : outbox.existsByStoreIdAndStep(store.getStoreId(), "ACTIVATED") || configured(store.getStoreId())) return "first_activation";
        if (d11 && outbox.existsByStoreIdAndStepAndStateIn(store.getStoreId(), "D11", Set.of(
                ActivationEmailOutbox.State.SENT, ActivationEmailOutbox.State.CLAIMED,
                ActivationEmailOutbox.State.REVIEW, ActivationEmailOutbox.State.SIMULATED,
                ActivationEmailOutbox.State.FAILED))) return "already_contacted";
        Duration stageAge = stageAge(event.getStep());
        if (!d11 && now.isAfter(event.getInstalledAt().plus(stageAge).plus(Duration.ofHours(24)))) return "stage_expired";
        if (now.isBefore(store.getInstalledAt().plus(stageAge))) return "not_due";
        if (!d11 && !parseAllowlist().contains(store.getStoreId())) return "not_allowlisted";
        return protection.reason(store.getStoreId());
    }
    private Duration stageAge(String step) {
        return switch (step) {
            case "D3" -> Duration.ofHours(72);
            case "D7" -> Duration.ofHours(168);
            case "D11" -> Duration.ofHours(264);
            default -> throw new IllegalArgumentException("Unsupported activation stage");
        };
    }

    private void finish(Long id, java.util.function.Consumer<ActivationEmailOutbox> update) {
        tx.executeWithoutResult(status -> outbox.findForUpdate(id).ifPresent(event -> {
            if (event.getState() == ActivationEmailOutbox.State.CLAIMED) update.accept(event);
        }));
    }

    private Instant startAt() {
        if (startAtText == null || startAtText.isBlank()) return null;
        try { return Instant.parse(startAtText); }
        catch (RuntimeException ex) { log.warn("nc39.schedule.invalid_start_at"); return null; }
    }

    private void suppress(ActivationEmailOutbox event, String reason) {
        event.suppress(reason);
        log.info("nc39.email.suppressed store_id={} event_id={} step={} reason={}",
                event.getStoreId(), event.getId(), event.getStep(), reason);
    }

    private Set<Long> parseAllowlist() {
        try { return Arrays.stream(allowlistText.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toUnmodifiableSet()); }
        catch (RuntimeException ex) { return Set.of(); }
    }

    private record Delivery(Long storeId, ActivationEmailTemplates.Copy copy, String step, int attempt, String recipient, String unsubscribeUrl) { }
}
