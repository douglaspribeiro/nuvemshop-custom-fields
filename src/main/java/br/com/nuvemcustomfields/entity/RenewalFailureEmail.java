package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "renewal_failure_emails", uniqueConstraints = @UniqueConstraint(name = "uk_renewal_failure_email", columnNames = {"provider", "subscription_id", "payment_id"}))
public class RenewalFailureEmail {
    public enum State { PENDING, CLAIMED, SENT, SKIPPED, REVIEW, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long storeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentProviderType provider;
    @Column(nullable = false, length = 120) private String subscriptionId;
    @Column(nullable = false, length = 120) private String paymentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state = State.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt = Instant.now();
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant completedAt;
    public Long getId() { return id; }
    public Long getStoreId() { return storeId; } public void setStoreId(Long v) { storeId=v; }
    public PaymentProviderType getProvider() { return provider; } public void setProvider(PaymentProviderType v) { provider=v; }
    public String getSubscriptionId() { return subscriptionId; } public void setSubscriptionId(String v) { subscriptionId=v; }
    public String getPaymentId() { return paymentId; } public void setPaymentId(String v) { paymentId=v; }
    public State getState() { return state; } public void setState(State v) { state=v; }
    public int getAttempts() { return attempts; } public void claim() { attempts++; state=State.CLAIMED; }
    public Instant getNextAttemptAt() { return nextAttemptAt; } public void setNextAttemptAt(Instant v) { nextAttemptAt=v; }
    public void finish(State v) { state=v; completedAt=Instant.now(); }
}
