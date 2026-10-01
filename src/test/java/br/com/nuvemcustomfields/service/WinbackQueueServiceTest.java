package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.WinbackProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.WinbackOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class WinbackQueueServiceTest {
    private static final long ID = 998877664L;
    private static final String URL = "https://sqs.us-east-2.amazonaws.com/265105089924/eventosDesistalacao";
    @Autowired StoreRepository stores;
    @Autowired WinbackOutboxRepository outbox;
    @Autowired StoreDataErasureService erasure;
    @Autowired ObjectMapper json;
    @Autowired WinbackTrackingService tracking;
    @Autowired jakarta.persistence.EntityManager entityManager;
    private SqsClient sqs;
    private WinbackQueueService queue;
    private Store store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sqs = mock(SqsClient.class);
        ObjectProvider<SqsClient> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(sqs);
        queue = new WinbackQueueService(outbox, stores, new WinbackProperties(true, URL, "us-east-2"), provider, json, tracking);
        store = new Store();
        store.setStoreId(ID);
        store.setStoreName("Nome privado");
        store.setStoreEmail("privado@example.com");
        store.setAccessToken("token-privado");
        store.setUninstalledAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        stores.saveAndFlush(store);
    }

    @Test
    void queuesOnceAndPublishesOnlyIdentifiersWithFifteenMinuteDelay() throws Exception {
        queue.enqueue(store);
        queue.enqueue(store);
        assertThat(outbox.findAll()).hasSize(1);
        var event = outbox.findAll().getFirst();
        queue.publish(event.getId(), ID);
        queue.publish(event.getId(), ID);
        var request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs, times(1)).sendMessage(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo(URL);
        assertThat(request.getValue().delaySeconds()).isEqualTo(900);
        var payload = json.readTree(request.getValue().messageBody());
        assertThat(payload.path("eventId").asText()).isEqualTo(event.getId());
        assertThat(payload.size()).isEqualTo(2);
        assertThat(request.getValue().messageBody()).doesNotContain(
                "privado", "token", "Nome", "storeId", "uninstalledAt", String.valueOf(ID));
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void sqsFailureRetainsEventForRetryWithoutBreakingWebhookTransaction() {
        queue.enqueue(store);
        var event = outbox.findAll().getFirst();
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(new IllegalStateException("simulated"));
        queue.publish(event.getId(), ID);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void erasureRemovesTheMappingOfAnAlreadyPublishedMessage() {
        queue.enqueue(store);
        var event = outbox.findAll().getFirst();
        queue.publish(event.getId(), ID);
        assertThat(event.getPublishedAt()).isNotNull();
        erasure.erase(ID);
        // Consulta como o futuro consumidor, sem objetos do contexto anterior.
        entityManager.clear();
        assertThat(outbox.findById(event.getId())).isEmpty();
    }

    @Test
    void reinstallsAndErasureCancelUnpublishedMessages() {
        queue.enqueue(store);
        var event = outbox.findAll().getFirst();
        store.setUninstalledAt(null);
        stores.saveAndFlush(store);
        queue.publish(event.getId(), ID);
        assertThat(outbox.findAll()).isEmpty();
        verifyNoInteractions(sqs);

        store.setUninstalledAt(Instant.now());
        stores.saveAndFlush(store);
        queue.enqueue(store);
        erasure.erase(ID);
        assertThat(outbox.findAll()).isEmpty();
        verifyNoInteractions(sqs);
    }
}
