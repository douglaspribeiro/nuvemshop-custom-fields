package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.WinbackEmailRepository;
import br.com.nuvemcustomfields.repository.WinbackOutboxRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.util.Properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.background.enabled=false", "winback.enabled=true", "winback.mail-enabled=true",
        "winback.queue-url=https://sqs.us-east-2.amazonaws.com/123/test", "winback.region=us-east-2",
        "homolog.allowed-store-ids=0", "notifications.ses.host=smtp.example.test",
        "notifications.ses.port=587", "notifications.ses.username=test", "notifications.ses.password=test",
        "notifications.ses.from=support@example.test", "notifications.ses-events.configuration-set=tracking-test"
})
@Transactional
class AutomaticWinbackIntegrationTest {
    @Autowired StoreRepository stores;
    @Autowired WinbackOutboxRepository outbox;
    @Autowired WinbackEmailRepository emails;
    @Autowired WebhookLifecycleService webhooks;
    @Autowired WinbackQueuePublisher publisher;
    @Autowired WinbackCampaignConsumer consumer;
    @MockBean(name = "winbackSqsClient") SqsClient sqs;
    @MockBean JavaMailSender smtp;

    @Test void uninstallWebhookQueuesAndSendsOneFeedbackWithoutHomologationAccess() throws Exception {
        var store = new Store(); store.setStoreId(990077660L); store.setStoreName("Loja");
        store.setStoreCountryCode("BR"); store.setStoreEmail("merchant@example.test"); store.setAccessToken("test");
        stores.saveAndFlush(store);
        var mail = new MimeMessage(Session.getInstance(new Properties()));
        when(smtp.createMimeMessage()).thenReturn(mail);
        webhooks.handle(new WebhookPayload(store.getStoreId(), "app/uninstalled", null));
        webhooks.handle(new WebhookPayload(store.getStoreId(), "app/uninstalled", null));
        assertThat(outbox.findAll()).hasSize(1);
        verifyNoInteractions(sqs);
        verify(smtp, never()).send(any(MimeMessage.class));
        publisher.publishDue();
        var request = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs, times(1)).sendMessage(request.capture());
        assertThat(request.getValue().delaySeconds()).isEqualTo(900);
        var event = outbox.findAll().getFirst();
        assertThat(event.getPublishedAt()).isNotNull();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(ReceiveMessageResponse.builder()
                .messages(Message.builder().body(request.getValue().messageBody()).receiptHandle("receipt").build()).build());
        consumer.poll(); consumer.poll();
        verify(smtp, times(1)).send(mail);
        assertThat(emails.findAll()).hasSize(1);
        assertThat(emails.findAll().getFirst().getStatus()).isEqualTo("SENT");
        assertThat(mail.getSubject()).contains("melhorar");
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }
}
