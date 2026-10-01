package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.entity.WinbackOutbox;
import br.com.nuvemcustomfields.properties.WinbackProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import br.com.nuvemcustomfields.repository.WinbackOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.time.Instant;

@Service
public class WinbackQueueService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WinbackQueueService.class);
    private final WinbackOutboxRepository outbox;
    private final StoreRepository stores;
    private final WinbackProperties properties;
    private final ObjectProvider<SqsClient> client;
    private final ObjectMapper json;
    private final WinbackTrackingService tracking;

    public WinbackQueueService(WinbackOutboxRepository outbox, StoreRepository stores,
            WinbackProperties properties, @org.springframework.beans.factory.annotation.Qualifier("winbackSqsClient")
            ObjectProvider<SqsClient> client, ObjectMapper json,
            WinbackTrackingService tracking) {
        this.outbox = outbox;
        this.stores = stores;
        this.properties = properties;
        this.client = client;
        this.json = json;
        this.tracking = tracking;
    }

    @Transactional
    public void enqueue(Store store) {
        if (store.getUninstalledAt() == null) return;
        tracking.record(store);
        if (!properties.enabled()) return;
        // Chamado sob o lock da loja, na mesma transação do webhook.
        if (!outbox.existsByStoreIdAndUninstalledAt(store.getStoreId(), store.getUninstalledAt()))
            outbox.save(new WinbackOutbox(store.getStoreId(), store.getUninstalledAt()));
    }

    @Transactional
    public void publish(String id, Long storeId) {
        if (!properties.enabled()) return;
        // Ordem de locks igual à exclusão: loja, depois outbox.
        Store store = stores.findByStoreIdForUpdate(storeId).orElse(null);
        WinbackOutbox event = outbox.findForUpdate(id).orElse(null);
        if (event == null || event.getPublishedAt() != null || event.getNextAttemptAt().isAfter(Instant.now())) return;
        if (store == null || store.isActive() || !store.getUninstalledAt().equals(event.getUninstalledAt())) {
            outbox.delete(event);
            return;
        }
        try {
            // A SQS guarda somente um ID aleatório. store/redact apaga o vínculo
            // local; mensagens já publicadas não retêm identificadores da loja.
            String body = json.writeValueAsString(new Event(1, event.getId()));
            client.getObject().sendMessage(SendMessageRequest.builder()
                    .queueUrl(properties.queueUrl()).delaySeconds(900).messageBody(body).build());
            event.published();
            LOGGER.info("winback.queue.published event_id={}", id);
        } catch (Exception ex) {
            event.retry();
            LOGGER.warn("winback.queue.publish_failed event_id={} attempt={} type={}",
                    id, event.getAttempts(), ex.getClass().getSimpleName());
        }
    }

    public record Event(int version, String eventId) { }
}
