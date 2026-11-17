package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.WebhookPayload;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.WinbackEmailRepository;
import br.com.nuvemcustomfields.repository.WinbackOutboxRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @Autowired WinbackCampaignService campaigns;
    @Autowired StoreErasureRequestService erasureRequests;
    @MockBean(name = "winbackSqsClient") SqsClient sqs;
    @MockBean JavaMailSender smtp;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void uninstallWebhookQueuesAndSendsOneFeedbackWithoutHomologationAccess(boolean pendingErasure) throws Exception {
        var store = new Store(); store.setStoreId(990077660L); store.setStoreName("Loja");
        store.setStoreCountryCode("BR"); store.setStoreEmail("merchant@example.test"); store.setAccessToken("test");
        stores.saveAndFlush(store);
        if (pendingErasure) erasureRequests.receive(store.getStoreId(), java.time.Instant.now());
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
        if (pendingErasure) assertThat(emails.findAll().getFirst().getErasureContactRequestAt()).isEqualTo(store.getErasureRequestedAt());
        assertThat(mail.getSubject()).contains("melhorar");
        verify(sqs, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test void initialManualFeedbackAlsoWorksOutsideHomologationWithAuthorizedPendingContact() throws Exception {
        var store = new Store(); store.setStoreId(990077661L); store.setStoreName("Loja");
        store.setStoreCountryCode("BR"); store.setStoreEmail("merchant@example.test");
        stores.saveAndFlush(store);
        erasureRequests.receive(store.getStoreId(), java.time.Instant.now());
        var mail = new MimeMessage(Session.getInstance(new Properties()));
        when(smtp.createMimeMessage()).thenReturn(mail);
        String id = campaigns.prepareManual(store.getStoreId(), true);
        assertThat(id).isNotNull();
        campaigns.send(id);
        assertThat(campaigns.prepareManual(store.getStoreId(), true)).isNull();
        assertThat(emails.findById(id).orElseThrow().getStatus()).isEqualTo("SENT");
        verify(smtp, times(1)).send(mail);
    }
}
