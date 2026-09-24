package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id")
    private Long storeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProviderType provider;
    @Column(name = "event_key", nullable = false, unique = true, length = 190)
    private String eventKey;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "provider_resource_id", length = 120)
    private String providerResourceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentWebhookStatus status = PaymentWebhookStatus.RECEIVED;
    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long value) { storeId = value; }
    public PaymentProviderType getProvider() { return provider; }
    public void setProvider(PaymentProviderType value) { provider = value; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String value) { eventKey = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getProviderResourceId() { return providerResourceId; }
    public void setProviderResourceId(String value) { providerResourceId = value; }
    public PaymentWebhookStatus getStatus() { return status; }
    public void setStatus(PaymentWebhookStatus value) { status = value; }
    public int getProcessingAttempts() { return processingAttempts; }
    public void setProcessingAttempts(int value) { processingAttempts = value; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant value) { processedAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
    public Instant getReceivedAt() { return receivedAt; }
}
