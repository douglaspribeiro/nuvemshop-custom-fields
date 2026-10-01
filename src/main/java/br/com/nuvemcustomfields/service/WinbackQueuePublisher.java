package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.WinbackProperties;
import br.com.nuvemcustomfields.repository.WinbackOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class WinbackQueuePublisher {
    private final WinbackOutboxRepository outbox;
    private final WinbackQueueService queue;
    private final WinbackProperties properties;
    public WinbackQueuePublisher(WinbackOutboxRepository outbox, WinbackQueueService queue, WinbackProperties properties) {
        this.outbox = outbox;
        this.queue = queue;
        this.properties = properties;
    }
    @Scheduled(fixedDelayString = "${winback.publish-delay-ms:30000}")
    public void publishDue() {
        if (!properties.enabled()) return;
        for (var event : outbox.findDue(Instant.now(), PageRequest.of(0, 20)))
            queue.publish(event.getId(), event.getStoreId());
    }
}
