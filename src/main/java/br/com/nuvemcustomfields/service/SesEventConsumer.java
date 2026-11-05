package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesEventsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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
    public SesEventConsumer(SesEventsProperties properties,
            @Qualifier("sesEventsSqsClient") ObjectProvider<SqsClient> client, WinbackSesEventService winback, ActivationEmailSesEventService activation, SesEventConsumerMetrics metrics) {
        this.properties = properties; this.client = client; this.winback = winback; this.activation = activation; this.metrics = metrics;
    }
    @Scheduled(fixedDelayString = "${notifications.ses-events.poll-delay-ms:3600000}",
            initialDelayString = "${notifications.ses-events.poll-delay-ms:3600000}")
    public void poll() {
        if (!properties.enabled()) return;
        try {
            var sqs = client.getObject();
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(45).toNanos();
            // Drain bounded batches once per hour instead of leaving a backlog at ten messages/hour.
            for (int batch = 0; batch < 100 && System.nanoTime() < deadline && !Thread.currentThread().isInterrupted(); batch++) {
                var messages = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(properties.queueUrl())
                        .maxNumberOfMessages(10).visibilityTimeout(60).waitTimeSeconds(0).build()).messages();
                metrics.received(messages.size());
                if (messages.isEmpty()) break;
                for (var message : messages) {
                    boolean processed = false;
                    try {
                        winback.ingest(message.body());
                        activation.ingest(message.body());
                        processed = true;
                        metrics.processed();
                        // ingest has committed before acknowledgement; replay is idempotent.
                        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(properties.queueUrl())
                                .receiptHandle(message.receiptHandle()).build());
                        metrics.acknowledged();
                    } catch (Exception ex) {
                        metrics.failed(processed ? SesEventConsumerMetrics.Failure.ACKNOWLEDGE : SesEventConsumerMetrics.Failure.PROCESS);
                        LOGGER.warn("ses.events.event_failed type={}", ex.getClass().getSimpleName());
                    }
                }
            }
        } catch (RuntimeException ex) {
            metrics.failed(SesEventConsumerMetrics.Failure.RECEIVE);
            LOGGER.warn("ses.events.poll_failed type={}", ex.getClass().getSimpleName());
        }
    }

    /** Separate read-only sampling: a missing GetQueueAttributes permission never stops consumption. */
    @Scheduled(fixedDelayString = "${notifications.ses-events.attributes-delay-ms:3600000}",
            initialDelayString = "${notifications.ses-events.attributes-delay-ms:3600000}")
    public void sampleQueue() {
        if (!properties.enabled()) return;
        try {
            var response = client.getObject().getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(properties.queueUrl()).attributeNames(
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED).build());
            metrics.attributes(response.attributes());
        } catch (RuntimeException ex) {
            metrics.attributesUnavailable();
            metrics.failed(SesEventConsumerMetrics.Failure.ATTRIBUTES);
            LOGGER.warn("ses.events.attributes_failed type={}", ex.getClass().getSimpleName());
        }
    }

}
