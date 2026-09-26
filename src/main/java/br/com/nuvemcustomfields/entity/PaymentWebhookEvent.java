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
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_environment", nullable = false, length = 20)
    private PaymentEnvironment providerEnvironment = PaymentEnvironment.PRODUCTION;
    @Column(name = "event_key", nullable = false, unique = true, length = 190)
    private String eventKey;
    @Column(name = "notification_id", length = 120)
    private String notificationId;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "occurred_at")
    private Instant occurredAt;
    @Column(name = "provider_resource_id", length = 120)
    private String providerResourceId;
    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentWebhookStatus status = PaymentWebhookStatus.RECEIVED;
    @Column(name = "processing_attempts", nullable = false)
    private int processingAttempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();
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
    public PaymentEnvironment getProviderEnvironment() { return providerEnvironment; }
    public void setProviderEnvironment(PaymentEnvironment value) { providerEnvironment = value; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String value) { eventKey = value; }
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String value) { notificationId = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant value) { occurredAt = value; }
    public String getProviderResourceId() { return providerResourceId; }
    public void setProviderResourceId(String value) { providerResourceId = value; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String value) { payloadJson = value; }
    public PaymentWebhookStatus getStatus() { return status; }
    public void setStatus(PaymentWebhookStatus value) { status = value; }
    public int getProcessingAttempts() { return processingAttempts; }
    public void setProcessingAttempts(int value) { processingAttempts = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant value) { processedAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
    public Instant getReceivedAt() { return receivedAt; }
}
