package br.com.nuvemcustomfields.service;

import io.micrometer.prometheusmetrics.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivationEmailMetricsTest {
    @Test void fixedLabelGaugesExposeZeroOnlyAfterSuccessfulSnapshotAndPreserveFailureTimestamp() {
        var source=mock(ActivationEmailMetricsSnapshotService.class);
        var registry=new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            var metrics=new ActivationEmailMetrics(source,registry);
            assertThat(registry.scrape()).contains("ncf_activation_email_eligible{step=\"D11\"} NaN");
            var at=Instant.parse("2026-11-05T12:00:00Z");
            when(source.collect(any())).thenReturn(new ActivationEmailMetricsSnapshotService.Snapshot(at,
                    Map.of("eligible",2L),Map.of("D11",2L),Map.of("D11",0L),Map.of(),Map.of(),Map.of("enabled",false)));
            metrics.refresh();String text=registry.scrape();
            assertThat(text).contains("ncf_activation_email_eligible{step=\"D11\"} 2.0",
                    "ncf_activation_email_ready{step=\"D11\"} 0.0", "ncf_activation_email_snapshot_timestamp_seconds")
                    .doesNotContain("store_id","store_name","@","token=");
            assertThat(registry.get("ncf.activation.email.snapshot.timestamp.seconds").gauge().value()).isEqualTo(at.getEpochSecond());
            when(source.collect(any())).thenThrow(new IllegalStateException("private diagnostic"));metrics.refresh();
            assertThat(registry.scrape()).contains("ncf_activation_email_metrics_failures_total 1.0",
                    "ncf_activation_email_eligible{step=\"D11\"} 2.0", "ncf_activation_email_snapshot_timestamp_seconds");
        } finally {registry.close();}
    }
    @Test void confirmedSendCounterIsAvailableAtZeroAndSeparatedByStage() {
        var registry=new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try {
            var metrics=new ActivationEmailDeliveryMetrics(registry);
            assertThat(registry.scrape()).contains("ncf_activation_email_sent_total{step=\"D11\"} 0.0");
            metrics.sent("D11");
            assertThat(registry.scrape()).contains("ncf_activation_email_sent_total{step=\"D11\"} 1.0",
                    "ncf_activation_email_sent_total{step=\"D3\"} 0.0", "ncf_activation_email_sent_total{step=\"D7\"} 0.0");
        } finally {registry.close();}
    }
}
