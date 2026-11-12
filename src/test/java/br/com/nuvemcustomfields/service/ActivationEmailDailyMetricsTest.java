package br.com.nuvemcustomfields.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.Instant;
import java.sql.Timestamp;
import static org.assertj.core.api.Assertions.*;

class ActivationEmailDailyMetricsTest {
    @Test void calendarDaysUseSaoPauloIncludePartialTodayAndExcludeUnknownHistory() {
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        db.execute("create table nc39_activation_email_outbox(sent_at timestamp,step varchar(12))");
        var registry=new SimpleMeterRegistry();var metrics=new ActivationEmailDailyMetrics(db,registry);
        var now=Instant.parse("2026-11-08T14:00:00Z");metrics.refresh(now);
        assertThat(registry.find("ncf.activation.email.daily.sent").gauges()).isEmpty();
        db.update("insert into nc39_activation_email_outbox values (?, 'D11'), (?, 'D7')",Timestamp.from(Instant.parse("2026-11-08T02:59:00Z")),Timestamp.from(Instant.parse("2026-11-08T03:01:00Z")));
        metrics.refresh(now);
        assertThat(registry.get("ncf.activation.email.daily.sent").tags("date","2026-11-07","partial","false").gauge().value()).isEqualTo(1);
        assertThat(registry.get("ncf.activation.email.daily.sent").tags("date","2026-11-08","partial","true").gauge().value()).isEqualTo(1);
        assertThat(registry.find("ncf.activation.email.daily.sent").gauges()).hasSize(2);
        db.update("insert into nc39_activation_email_outbox values (?, 'D11')",Timestamp.from(now.minusSeconds(20*86400)));
        metrics.refresh(now);
        assertThat(registry.find("ncf.activation.email.daily.sent").gauges()).hasSize(14);
        metrics.refresh(now.plusSeconds(86400));
        assertThat(registry.find("ncf.activation.email.daily.sent").gauges()).hasSize(14);
        assertThat(registry.find("ncf.activation.email.daily.sent").tags("date","2026-11-08","partial","true").gauge()).isNull();
    }
}
