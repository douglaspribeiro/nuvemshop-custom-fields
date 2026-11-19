package br.com.nuvemcustomfields.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Counts retained feedback messages once, using their persisted SES outcomes. */
@Component
public class WinbackFeedbackMetrics {
    private static final Logger LOG = LoggerFactory.getLogger(WinbackFeedbackMetrics.class);
    private static final List<String> OUTCOMES = List.of("sent", "delivered", "failed", "complaint", "awaiting");
    private static final String COUNTS = """
            SELECT outcome, COUNT(*) AS total
            FROM (
                SELECT CASE
                    WHEN EXISTS (SELECT 1 FROM winback_email_events ev
                                 WHERE ev.email_id = m.id AND ev.type = 'Complaint') THEN 'complaint'
                    WHEN EXISTS (SELECT 1 FROM winback_email_events ev WHERE ev.email_id = m.id
                                 AND ev.type IN ('Bounce', 'Reject', 'Rendering Failure')) THEN 'failed'
                    WHEN EXISTS (SELECT 1 FROM winback_email_events ev
                                 WHERE ev.email_id = m.id AND ev.type = 'Delivery') THEN 'delivered'
                    ELSE 'awaiting'
                END AS outcome
                FROM winback_emails m
                WHERE m.step = 'FEEDBACK' AND m.status = 'SENT' AND m.sent_at BETWEEN ? AND ?
            ) feedback
            GROUP BY outcome
            """;

    private final JdbcTemplate jdbc;
    private final Counter failures;
    private volatile Snapshot snapshot;

    public WinbackFeedbackMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        failures = registry.counter("ncf.winback.feedback.metrics.failures");
        for (String outcome : OUTCOMES) {
            Gauge.builder("ncf.winback.feedback.last14d", this, metrics -> metrics.count(outcome))
                    .tag("outcome", outcome).register(registry);
        }
        Gauge.builder("ncf.winback.feedback.snapshot.timestamp.seconds", this, metrics -> metrics.timestamp())
                .register(registry);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void refresh() {
        refresh(Instant.now());
    }

    synchronized void refresh(Instant now) {
        try {
            Map<String, Long> counts = jdbc.query(COUNTS, rs -> {
                Map<String, Long> outcomes = new HashMap<>();
                while (rs.next()) outcomes.put(rs.getString("outcome"), rs.getLong("total"));
                return outcomes;
            }, Timestamp.from(now.minus(Duration.ofDays(14))), Timestamp.from(now));
            counts.put("sent", counts.values().stream().mapToLong(Long::longValue).sum());
            snapshot = new Snapshot(now, Map.copyOf(counts));
        } catch (RuntimeException ex) {
            failures.increment();
            LOG.warn("winback.feedback.metrics.failed type={}", ex.getClass().getSimpleName());
        }
    }

    private double count(String outcome) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.counts().getOrDefault(outcome, 0L);
    }

    private double timestamp() {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : current.at().getEpochSecond();
    }

    private record Snapshot(Instant at, Map<String, Long> counts) { }
}
