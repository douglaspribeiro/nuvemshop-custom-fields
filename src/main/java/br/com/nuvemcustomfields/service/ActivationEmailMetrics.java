package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.ActivationEmailOutbox;
import io.micrometer.core.instrument.*;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

/** Fixed-label gauges; no store IDs, email addresses or message IDs. Unknown values are NaN. */
@Component
public class ActivationEmailMetrics {
    private static final Logger log = LoggerFactory.getLogger(ActivationEmailMetrics.class);
    private final ActivationEmailMetricsSnapshotService snapshots;
    private final Counter failures;
    private volatile ActivationEmailMetricsSnapshotService.Snapshot snapshot;
    public ActivationEmailMetrics(ActivationEmailMetricsSnapshotService snapshots, MeterRegistry registry) {
        this.snapshots = snapshots;
        failures = registry.counter("ncf.activation.email.metrics.failures");
        for (String state : ActivationEmailMetricsSnapshotService.STORE_STATES)
            Gauge.builder("ncf.activation.email.stores", this, m -> m.value("stores", state)).tag("status", state).register(registry);
        for (String step : ActivationEmailMetricsSnapshotService.STEPS) {
            Gauge.builder("ncf.activation.email.eligible", this, m -> m.value("eligible", step)).tag("step", step).register(registry);
            Gauge.builder("ncf.activation.email.ready", this, m -> m.value("ready", step)).tag("step", step).register(registry);
        }
        for (String outcome : ActivationEmailMetricsSnapshotService.DELIVERY_OUTCOMES)
            Gauge.builder("ncf.activation.email.delivery", this, m -> m.value("delivery", outcome))
                    .tag("outcome", outcome).register(registry);
        for (String reason : ActivationEmailMetricsSnapshotService.REASONS)
            Gauge.builder("ncf.activation.email.blocked", this, m -> m.value("blocked", reason)).tag("reason", reason).register(registry);
        for (String step : ActivationEmailMetricsSnapshotService.OUTBOX_STEPS) for (var state : ActivationEmailOutbox.State.values()) {
            String key = step + ":" + state.name();
            Gauge.builder("ncf.activation.email.outbox", this, m -> m.value("outbox", key))
                    .tag("step", step).tag("state", state.name()).register(registry);
        }
        for (String control : List.of("enabled", "dry_run", "suppression_ready", "cohort_valid", "allowlist_nonempty",
                "feedback_configured", "smtp_configured", "live_enabled"))
            Gauge.builder("ncf.activation.email.control", this, m -> {
                var s = m.snapshot;return s == null ? Double.NaN : Boolean.TRUE.equals(s.controls().get(control)) ? 1 : 0;
            }).tag("control", control).register(registry);
        Gauge.builder("ncf.activation.email.snapshot.timestamp.seconds", this,
                m -> m.snapshot == null ? Double.NaN : m.snapshot.at().getEpochSecond()).register(registry);
    }
    @EventListener({ApplicationReadyEvent.class, br.com.nuvemcustomfields.event.ActivationEmailActivityUpdated.class})
    @Scheduled(fixedDelayString = "${activation.email.metrics-delay-ms:3600000}",
            initialDelayString = "${activation.email.metrics-delay-ms:3600000}")
    public synchronized void refresh() {
        try { snapshot = snapshots.collect(Instant.now()); }
        catch (RuntimeException ex) {
            failures.increment();log.warn("nc39.metrics.failed type={}", ex.getClass().getSimpleName());
            // Retain the timestamp of the last successful snapshot so dashboards can reject stale data.
        }
    }
    private double value(String family, String key) {
        var s = snapshot;if (s == null) return Double.NaN;
        Map<String, Long> values = switch (family) {
            case "stores" -> s.stores();case "eligible" -> s.eligible();case "ready" -> s.ready();
            case "blocked" -> s.blocked();case "outbox" -> s.outbox();case "delivery" -> s.delivery();
            default -> throw new IllegalArgumentException();
        };
        return values.getOrDefault(key, 0L);
    }
}
