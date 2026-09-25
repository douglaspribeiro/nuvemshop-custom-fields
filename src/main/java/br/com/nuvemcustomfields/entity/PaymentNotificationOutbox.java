package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_notification_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uk_payment_notification_provider_payment", columnNames = {"provider", "payment_id"}))
public class PaymentNotificationOutbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProviderType provider;
    @Column(name = "payment_id", nullable = false, length = 120)
    private String paymentId;
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlanType plan;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "amount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountValue;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public PaymentProviderType getProvider() { return provider; }
    public void setProvider(PaymentProviderType value) { provider = value; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String value) { paymentId = value; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long value) { storeId = value; }
    public PlanType getPlan() { return plan; }
    public void setPlan(PlanType value) { plan = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; }
    public BigDecimal getAmountValue() { return amountValue; }
    public void setAmountValue(BigDecimal value) { amountValue = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant value) { nextAttemptAt = value; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant value) { deliveredAt = value; }
    public Instant getCreatedAt() { return createdAt; }
}
