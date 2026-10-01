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
public class WinbackSesEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WinbackSesEventConsumer.class);
    private final SesEventsProperties properties;
    private final ObjectProvider<SqsClient> client;
    private final WinbackSesEventService service;
    public WinbackSesEventConsumer(SesEventsProperties properties,
            @Qualifier("sesEventsSqsClient") ObjectProvider<SqsClient> client, WinbackSesEventService service) {
        this.properties = properties; this.client = client; this.service = service;
    }
    @Scheduled(fixedDelayString = "${notifications.ses-events.poll-delay-ms:10000}")
    public void poll() {
        if (!properties.enabled()) return;
        try {
            var sqs = client.getObject();
            var messages = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(properties.queueUrl())
                    .maxNumberOfMessages(10).visibilityTimeout(60).waitTimeSeconds(0).build()).messages();
            for (var message : messages) {
                try {
                    service.ingest(message.body());
                    // ingest has committed before acknowledgement; replay is idempotent.
                    sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(properties.queueUrl())
                            .receiptHandle(message.receiptHandle()).build());
                } catch (Exception ex) {
                    LOGGER.warn("winback.ses.event_failed type={}", ex.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("winback.ses.poll_failed type={}", ex.getClass().getSimpleName());
        }
    }
}
