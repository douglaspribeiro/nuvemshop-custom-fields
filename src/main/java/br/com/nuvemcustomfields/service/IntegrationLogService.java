package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.IntegrationLog;
import br.com.nuvemcustomfields.repository.IntegrationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationLogService.class);

    private final IntegrationLogRepository repository;

    public IntegrationLogService(IntegrationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void info(Long storeId, String eventType, String message) {
        write(storeId, "INFO", eventType, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void warn(Long storeId, String eventType, String message) {
        write(storeId, "WARN", eventType, message);
    }

    @Transactional(readOnly = true)
    public java.util.List<IntegrationLog> recent(Long storeId) {
        return repository.findTop20ByStoreIdOrderByCreatedAtDesc(storeId);
    }

    private void write(Long storeId, String level, String eventType, String message) {
        LOGGER.info("integration_log store_id={} level={} event_type={} message={}", storeId, level, eventType, message);
        IntegrationLog log = new IntegrationLog();
        log.setStoreId(storeId);
        log.setLevel(level);
        log.setEventType(eventType);
        log.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        repository.save(log);
    }
}
