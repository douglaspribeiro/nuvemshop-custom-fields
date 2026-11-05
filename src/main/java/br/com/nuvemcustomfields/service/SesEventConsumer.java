package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@Service
public class SesEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SesEventConsumer.class);
    private final SesEventsProperties properties;
    private final ObjectProvider<SqsClient> client;
    private final WinbackSesEventService winback;
    private final ActivationEmailSesEventService activation;
    private final SesEventConsumerMetrics metrics;
    private final AtomicBoolean consuming = new AtomicBoolean();
    private volatile RunStatus lastRun = new RunStatus(false, "", null, null, 0, 0, 0, 0, false, null, null);
    public enum ManualStart { STARTED, RUNNING, DISABLED, MISCONFIGURED, UNAVAILABLE }
    public record RunStatus(boolean running, String source, Instant startedAt, Instant finishedAt,
            int received, int processed, int acknowledged, int failures, boolean limited,
            Boolean attributesOk, String error) { }
    public RunStatus status() { return lastRun; }
    public boolean enabled() { return properties.enabled(); }
    public String configurationProblem() {
        if (!properties.enabled()) return "O consumidor está desativado. Configure AWS_SES_EVENTS_ENABLED=true e publique a configuração.";
        try {
            var uri = java.net.URI.create(properties.queueUrl());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || uri.getPath() == null || uri.getPath().length() < 2)
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            return "Configure AWS_SES_EVENTS_QUEUE_URL com a URL HTTPS completa da fila, e não somente o nome.";
        }
        if (properties.region() == null || properties.region().isBlank()) return "Configure AWS_SES_EVENTS_REGION com a região da fila.";
        if (properties.configurationSet() == null || properties.configurationSet().isBlank()) return "Configure AWS_SES_CONFIGURATION_SET.";
        return null;
    }
    public ManualStart startManual(Executor executor) {
        if (!properties.enabled()) return ManualStart.DISABLED;
        if (configurationProblem() != null) return ManualStart.MISCONFIGURED;
        if (!reserve("Manual")) return ManualStart.RUNNING;
        try { executor.execute(() -> consumeReserved(true)); }
        catch (RejectedExecutionException ex) {
            var run = lastRun;
            lastRun = new RunStatus(false, run.source(), run.startedAt(), Instant.now(), 0, 0, 0, 0, false, null,
                    "A aplicação está encerrando. Tente novamente após a reinicialização.");
            consuming.set(false);
            LOGGER.warn("ses.events.manual_rejected type={}", ex.getClass().getSimpleName());
            return ManualStart.UNAVAILABLE;
        }
        LOGGER.info("ses.events.manual_requested");
        return ManualStart.STARTED;
    }
    private boolean reserve(String source) {
        if (!consuming.compareAndSet(false, true)) return false;
        lastRun = new RunStatus(true, source, Instant.now(), null, 0, 0, 0, 0, false, null, null);
        return true;
    }
    public SesEventConsumer(SesEventsProperties properties,
            @Qualifier("sesEventsSqsClient") ObjectProvider<SqsClient> client, WinbackSesEventService winback, ActivationEmailSesEventService activation, SesEventConsumerMetrics metrics) {
        this.properties = properties; this.client = client; this.winback = winback; this.activation = activation; this.metrics = metrics;
    }
    @Scheduled(fixedDelayString = "${notifications.ses-events.poll-delay-ms:3600000}",
            initialDelayString = "${notifications.ses-events.poll-delay-ms:3600000}")
    public void poll() {
        if (!properties.enabled() || !reserve("Automática")) return;
        consumeReserved(false);
    }
    private void consumeReserved(boolean manual) {
        int received = 0, processed = 0, acknowledged = 0, failures = 0;
        boolean limited = true;
        Boolean attributesOk = null;
        String error = null;
        Instant started = lastRun.startedAt();
        String source = lastRun.source();
        try {
            var sqs = client.getObject();
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(45).toNanos();
            for (int batch = 0; batch < 100 && System.nanoTime() < deadline && !Thread.currentThread().isInterrupted(); batch++) {
                var messages = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(properties.queueUrl())
                        .maxNumberOfMessages(10).visibilityTimeout(60).waitTimeSeconds(0).build()).messages();
                received += messages.size();
                metrics.received(messages.size());
                if (messages.isEmpty()) { limited = false; break; }
                for (var message : messages) {
                    boolean handled = false;
                    try {
                        winback.ingest(message.body());
                        activation.ingest(message.body());
                        handled = true; processed++;
                        metrics.processed();
                        // ingest has committed before acknowledgement; replay is idempotent.
                        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(properties.queueUrl())
                                .receiptHandle(message.receiptHandle()).build());
                        acknowledged++; metrics.acknowledged();
                    } catch (Exception ex) {
                        failures++;
                        metrics.failed(handled ? SesEventConsumerMetrics.Failure.ACKNOWLEDGE : SesEventConsumerMetrics.Failure.PROCESS);
                        LOGGER.warn("ses.events.event_failed type={}", ex.getClass().getSimpleName());
                    }
                }
                lastRun = new RunStatus(true, source, started, null, received, processed, acknowledged, failures, false, null, null);
            }
        } catch (RuntimeException ex) {
            failures++; limited = false;
            error = "Não foi possível concluir a leitura da fila. Verifique URL, região, credenciais e permissões AWS nos logs.";
            metrics.failed(SesEventConsumerMetrics.Failure.RECEIVE);
            LOGGER.warn("ses.events.poll_failed type={}", ex.getClass().getSimpleName());
        } finally {
            try { if (manual) attributesOk = sampleQueueOnce(); }
            finally {
                lastRun = new RunStatus(false, source, started, Instant.now(), received, processed, acknowledged,
                        failures, limited, attributesOk, error);
                consuming.set(false);
                LOGGER.info("ses.events.cycle_finished source={} received={} processed={} acknowledged={} failures={} limited={}",
                        source, received, processed, acknowledged, failures, limited);
            }
        }
    }

    /** Separate read-only sampling: a missing GetQueueAttributes permission never stops consumption. */
    @Scheduled(fixedDelayString = "${notifications.ses-events.attributes-delay-ms:3600000}",
            initialDelayString = "${notifications.ses-events.attributes-delay-ms:3600000}")
    public void sampleQueue() { sampleQueueOnce(); }
    private boolean sampleQueueOnce() {
        if (!properties.enabled()) return false;
        try {
            var response = client.getObject().getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(properties.queueUrl()).attributeNames(
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED).build());
            metrics.attributes(response.attributes());
            return true;
        } catch (RuntimeException ex) {
            metrics.attributesUnavailable();
            metrics.failed(SesEventConsumerMetrics.Failure.ATTRIBUTES);
            LOGGER.warn("ses.events.attributes_failed type={}", ex.getClass().getSimpleName());
            return false;
        }
    }

}
