package br.com.nuvemcustomfields.service;

import io.micrometer.core.instrument.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Calendar-day SMTP acceptances from persisted sent_at; no zero history before first known send. */
@Component
public class ActivationEmailDailyMetrics {
    private static final ZoneId ZONE=ZoneId.of("America/Sao_Paulo");
    private final JdbcTemplate jdbc;
    private final MultiGauge daily;
    private final MeterRegistry registry;
    private volatile Instant at;
    public ActivationEmailDailyMetrics(JdbcTemplate jdbc,MeterRegistry registry) {
        this.jdbc=jdbc;this.registry=registry;
        daily=MultiGauge.builder("ncf.activation.email.daily.sent").register(registry);
        Gauge.builder("ncf.activation.email.daily.snapshot.timestamp.seconds",this,m->m.at==null?Double.NaN:m.at.getEpochSecond()).register(registry);
        registry.counter("ncf.activation.email.daily.metrics.failures");
    }
    @EventListener({ApplicationReadyEvent.class,br.com.nuvemcustomfields.event.ActivationEmailActivityUpdated.class})
    @Scheduled(fixedDelayString="${activation.email.daily-metrics-delay-ms:60000}",initialDelayString="${activation.email.daily-metrics-delay-ms:60000}")
    public synchronized void refresh(){refresh(Instant.now());}
    synchronized void refresh(Instant now) {
        try {
            var today=now.atZone(ZONE).toLocalDate();
            var first=jdbc.queryForObject("select min(sent_at) from nc39_activation_email_outbox where sent_at is not null and step in ('D3','D7','D11')",Timestamp.class);
            var totals=new TreeMap<LocalDate,Long>();
            if(first!=null) {
                var start=today.minusDays(13);var firstDay=first.toInstant().atZone(ZONE).toLocalDate();if(firstDay.isAfter(start))start=firstDay;
                for(var day=start;!day.isAfter(today);day=day.plusDays(1))totals.put(day,0L);
                jdbc.query("select sent_at from nc39_activation_email_outbox where sent_at>=? and sent_at<=? and step in ('D3','D7','D11')",(org.springframework.jdbc.core.RowCallbackHandler)rs -> {
                    var day=rs.getTimestamp(1).toInstant().atZone(ZONE).toLocalDate();totals.computeIfPresent(day,(d,n)->n+1);
                },Timestamp.from(start.atStartOfDay(ZONE).toInstant()),Timestamp.from(now));
            }
            var rows=new ArrayList<MultiGauge.Row<?>>();
            totals.forEach((day,n)->rows.add(MultiGauge.Row.of(Tags.of("date",day.toString(),"partial",Boolean.toString(day.equals(today))),n)));
            daily.register(rows,true);at=now;
        } catch(RuntimeException e){registry.counter("ncf.activation.email.daily.metrics.failures").increment();}
    }
}
