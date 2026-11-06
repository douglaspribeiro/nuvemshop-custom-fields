package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.ActivationEmailOutbox;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Read-only audience estimate; never schedules, claims, suppresses, or sends an email. */
@Service
public class ActivationEmailMetricsSnapshotService {
    public static final List<String> STEPS = List.of("D3", "D7", "D11");
    public static final List<String> STORE_STATES = List.of("eligible", "waiting", "blocked", "handled", "review");
    public static final List<String> REASONS = List.of("outside_cohort", "first_activation", "stage_expired", "not_allowlisted",
            "unsubscribe", "ses_bounce", "ses_complaint", "ses_unsubscribe", "previous_email_opt_out",
            "store_inactive_or_reinstalled", "invalid_start_at", "invalid_recipient", "already_contacted", "other");
    public static final List<String> DELIVERY_OUTCOMES = List.of("delivered", "bounced", "complaint", "awaiting");
    public static final List<String> OUTBOX_STEPS = List.of("D3", "D7", "D11", "D15", "OTHER");
    private final StoreRepository stores;
    private final ActivationEmailOutboxRepository outbox;
    private final ActivationEmailJob job;
    public ActivationEmailMetricsSnapshotService(StoreRepository stores, ActivationEmailOutboxRepository outbox, ActivationEmailJob job) {
        this.stores = stores; this.outbox = outbox; this.job = job;
    }
    public record Snapshot(Instant at, Map<String, Long> stores, Map<String, Long> eligible,
            Map<String, Long> ready, Map<String, Long> blocked, Map<String, Long> outbox, Map<String, Long> delivery, Map<String, Boolean> controls) { }
    @Transactional(readOnly = true)
    public Snapshot collect(Instant now) {
        Map<String, Long> totals = zeros(STORE_STATES), eligible = zeros(STEPS), ready = zeros(STEPS), blocked = zeros(REASONS);
        Map<String, Long> persisted = new HashMap<>();
        for (var step : OUTBOX_STEPS) for (var state : ActivationEmailOutbox.State.values())
            persisted.put(step + ":" + state.name(), 0L);
        for (var row : outbox.countsByStepAndState()) {
            String step = Objects.toString(row[0]);
            if (!OUTBOX_STEPS.contains(step)) step = "OTHER";
            // ACTIVATED is a lifecycle marker, not an email queued for delivery.
            if ("ACTIVATED".equals(Objects.toString(row[0]))) continue;
            persisted.merge(step + ":" + row[1], ((Number) row[2]).longValue(), Long::sum);
        }
        Map<String, Long> delivery = zeros(DELIVERY_OUTCOMES);
        for (var row : outbox.countsByDeliveryOutcome()) {
            String outcome = Objects.toString(row[0]);
            if (DELIVERY_OUTCOMES.contains(outcome)) delivery.put(outcome, ((Number) row[1]).longValue());
        }
        var controls = job.controls();
        for (var store : stores.findAllActive()) {
            var audience = new LinkedHashMap<String, ActivationEmailJob.Audience>();
            for (String step : STEPS) {
                var decision = job.audience(store, step, now); audience.put(step, decision);
                if (decision.state() == ActivationEmailJob.AudienceState.ELIGIBLE) eligible.merge(step, 1L, Long::sum);
            }
            String overall;
            if (has(audience, ActivationEmailJob.AudienceState.ELIGIBLE)) overall = "eligible";
            else if (has(audience, ActivationEmailJob.AudienceState.REVIEW)) overall = "review";
            else if (audience.get("D11").state() == ActivationEmailJob.AudienceState.HANDLED) overall = "handled";
            else if (has(audience, ActivationEmailJob.AudienceState.WAITING)) overall = "waiting";
            else if (has(audience, ActivationEmailJob.AudienceState.BLOCKED)) overall = "blocked";
            else overall = "handled";
            totals.merge(overall, 1L, Long::sum);
            if (overall.equals("blocked")) {
                var d11 = audience.get("D11");
                String reason = d11.state() == ActivationEmailJob.AudienceState.BLOCKED ? d11.reason()
                        : audience.values().stream().filter(v -> v.state() == ActivationEmailJob.AudienceState.BLOCKED)
                                .findFirst().orElseThrow().reason();
                blocked.merge(reason != null && REASONS.contains(reason) ? reason : "other", 1L, Long::sum);
            }
        }
        if (Boolean.TRUE.equals(controls.get("live_enabled"))) for (String step : STEPS)
            ready.put(step, eligible.get(step));
        return new Snapshot(now, Map.copyOf(totals), Map.copyOf(eligible), Map.copyOf(ready),
                Map.copyOf(blocked), Map.copyOf(persisted), Map.copyOf(delivery), Map.copyOf(controls));
    }
    private boolean has(Map<String, ActivationEmailJob.Audience> audience, ActivationEmailJob.AudienceState state) {
        return audience.values().stream().anyMatch(v -> v.state() == state);
    }
    private Map<String, Long> zeros(List<String> labels) {
        var result = new HashMap<String, Long>();labels.forEach(label -> result.put(label, 0L));return result;
    }
}
