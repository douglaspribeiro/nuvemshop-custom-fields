package br.com.nuvemcustomfields.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WinbackFeedbackMetricsTest {
    @Test
    void countsOnlyRecentFeedbackMessagesAndDeduplicatesSesEvents() {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        var registry = new SimpleMeterRegistry();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE winback_emails (id VARCHAR(30), step VARCHAR(20), status VARCHAR(20), sent_at TIMESTAMP)");
            jdbc.execute("CREATE TABLE winback_email_events (email_id VARCHAR(30), type VARCHAR(30))");
            var now = Instant.parse("2026-11-19T19:00:00Z");
            var recent = Timestamp.from(now.minusSeconds(60));
            var old = Timestamp.from(now.minus(Duration.ofDays(15)));
            jdbc.update("""
                    INSERT INTO winback_emails VALUES
                    ('delivered', 'FEEDBACK', 'SENT', ?), ('awaiting', 'FEEDBACK', 'SENT', ?),
                    ('complaint', 'FEEDBACK', 'SENT', ?), ('failed', 'FEEDBACK', 'SENT', ?),
                    ('old', 'FEEDBACK', 'SENT', ?), ('offer', 'FOLLOWUP', 'SENT', ?),
                    ('sending', 'FEEDBACK', 'SENDING', NULL)
                    """, recent, recent, recent, recent, old, recent);
            jdbc.update("""
                    INSERT INTO winback_email_events VALUES
                    ('delivered', 'Delivery'), ('delivered', 'Delivery'),
                    ('awaiting', 'Send'), ('complaint', 'Delivery'), ('complaint', 'Complaint'),
                    ('failed', 'Bounce'), ('offer', 'Delivery'), ('old', 'Delivery')
                    """);

            new WinbackFeedbackMetrics(jdbc, registry).refresh(now);

            for (String outcome : new String[]{"delivered", "awaiting", "complaint", "failed"}) {
                assertThat(registry.get("ncf.winback.feedback.last14d").tag("outcome", outcome).gauge().value()).isEqualTo(1);
            }
            assertThat(registry.get("ncf.winback.feedback.last14d").tag("outcome", "sent").gauge().value()).isEqualTo(4);
        } finally {
            registry.close();
            database.shutdown();
        }
    }
}
