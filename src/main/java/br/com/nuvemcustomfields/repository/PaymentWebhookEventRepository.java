package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {
    Optional<PaymentWebhookEvent> findByEventKey(String eventKey);
    void deleteByStoreId(Long storeId);
}
