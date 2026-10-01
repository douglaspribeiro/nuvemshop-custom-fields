package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.WinbackProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

@Service
public class WinbackCampaignConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WinbackCampaignConsumer.class);
    private final WinbackProperties properties;
    private final boolean mailEnabled;
    private final ObjectProvider<SqsClient> client;
    private final WinbackCampaignService campaigns;
    private final ObjectMapper json;
    public WinbackCampaignConsumer(WinbackProperties properties, @Value("${winback.mail-enabled:false}") boolean mailEnabled,
            @Qualifier("winbackSqsClient") ObjectProvider<SqsClient> client,
            WinbackCampaignService campaigns, ObjectMapper json) {
        this.properties = properties; this.mailEnabled = mailEnabled; this.client = client;
        this.campaigns = campaigns; this.json = json;
    }
    @Scheduled(fixedDelayString = "${winback.consume-delay-ms:10000}")
    public void poll() {
        if (!properties.enabled() || !mailEnabled) return;
        try {
            var sqs = client.getObject();
            var messages = sqs.receiveMessage(ReceiveMessageRequest.builder().queueUrl(properties.queueUrl())
                    .maxNumberOfMessages(5).visibilityTimeout(120).waitTimeSeconds(0).build()).messages();
            for (var message : messages) {
                try {
                    var event = json.readValue(message.body(), WinbackQueueService.Event.class);
                    if (event.version() != 1 || event.eventId() == null) throw new IllegalArgumentException("Evento inválido.");
                    String emailId = campaigns.prepare(event.eventId());
                    if (emailId != null) campaigns.send(emailId);
                    sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(properties.queueUrl())
                            .receiptHandle(message.receiptHandle()).build());
                } catch (Exception ex) {
                    // SMTP uncertainty leaves a durable SENDING claim for reconciliation from SES.
                    // Do not automatically resend: provider may have accepted the message.
                    LOGGER.warn("winback.campaign.failed type={}", ex.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("winback.campaign.poll_failed type={}", ex.getClass().getSimpleName());
        }
    }
}
