package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_subscriptions")
public class PaymentSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "store_id", nullable = false, unique = true)
    private Long storeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentProviderType provider;
    @Column(name = "provider_subscription_id", unique = true, length = 120)
    private String providerSubscriptionId;
    @Column(name = "provider_checkout_id", unique = true, length = 120)
    private String providerCheckoutId;
    @Column(name = "provider_customer_id", length = 120)
    private String providerCustomerId;
    @Column(name = "provider_price_id", length = 120)
    private String providerPriceId;
    @Column(name = "country_code", length = 2)
    private String countryCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_environment", nullable = false, length = 20)
    private PaymentEnvironment providerEnvironment = PaymentEnvironment.PRODUCTION;
    @Column(name = "payer_email", length = 254)
    private String payerEmail;
    @Column(name = "external_reference", nullable = false, unique = true, length = 64)
    private String externalReference;
    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PlanType plan;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "amount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountValue;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentSubscriptionStatus status = PaymentSubscriptionStatus.PENDING;
    @Column(name = "provider_status", length = 50)
    private String providerStatus;
    @Column(name = "access_active", nullable = false)
    private boolean accessActive;
    @Column(name = "next_payment_at")
    private Instant nextPaymentAt;
    @Column(name = "current_period_start")
    private Instant currentPeriodStart;
    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;
    @Column(name = "last_payment_id", length = 120)
    private String lastPaymentId;
    @Column(name = "last_payment_status", length = 50)
    private String lastPaymentStatus;
    @Column(name = "grace_until")
    private Instant graceUntil;
    @Column(name = "cancellation_pending", nullable = false)
    private boolean cancellationPending;
    @Column(name = "cancellation_requested_at")
    private Instant cancellationRequestedAt;
    @Column(name = "cancellation_effective_at")
    private Instant cancellationEffectiveAt;
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
    @Column(name = "pending_started_at")
    private Instant pendingStartedAt;
    @Column(name = "last_error", length = 500)
    private String lastError;
    @Version
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public void setStoreId(Long value) { storeId = value; }
    public PaymentProviderType getProvider() { return provider; }
    public void setProvider(PaymentProviderType value) { provider = value; }
    public String getProviderSubscriptionId() { return providerSubscriptionId; }
    public void setProviderSubscriptionId(String value) { providerSubscriptionId = value; touch(); }
    public String getProviderCheckoutId() { return providerCheckoutId; }
    public void setProviderCheckoutId(String value) { providerCheckoutId = value; touch(); }
    public String getProviderCustomerId() { return providerCustomerId; }
    public void setProviderCustomerId(String value) { providerCustomerId = value; touch(); }
    public String getProviderPriceId() { return providerPriceId; }
    public void setProviderPriceId(String value) { providerPriceId = value; touch(); }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String value) { countryCode = value; touch(); }
    public PaymentEnvironment getProviderEnvironment() { return providerEnvironment; }
    public void setProviderEnvironment(PaymentEnvironment value) { providerEnvironment = value; touch(); }
    public String getPayerEmail() { return payerEmail; }
    public void setPayerEmail(String value) { payerEmail = value; touch(); }
    public String getExternalReference() { return externalReference; }
    public void setExternalReference(String value) { externalReference = value; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String value) { checkoutUrl = value; touch(); }
    public PlanType getPlan() { return plan; }
    public void setPlan(PlanType value) { plan = value; touch(); }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { currency = value; touch(); }
    public BigDecimal getAmountValue() { return amountValue; }
    public void setAmountValue(BigDecimal value) { amountValue = value; touch(); }
    public PaymentSubscriptionStatus getStatus() { return status; }
    public void setStatus(PaymentSubscriptionStatus value) { status = value; touch(); }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String value) { providerStatus = value; touch(); }
    public boolean isAccessActive() { return accessActive; }
    public void setAccessActive(boolean value) { accessActive = value; touch(); }
    public Instant getNextPaymentAt() { return nextPaymentAt; }
    public void setNextPaymentAt(Instant value) { nextPaymentAt = value; touch(); }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public void setCurrentPeriodStart(Instant value) { currentPeriodStart = value; touch(); }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public void setCurrentPeriodEnd(Instant value) { currentPeriodEnd = value; touch(); }
    public String getLastPaymentId() { return lastPaymentId; }
    public void setLastPaymentId(String value) { lastPaymentId = value; touch(); }
    public String getLastPaymentStatus() { return lastPaymentStatus; }
    public void setLastPaymentStatus(String value) { lastPaymentStatus = value; touch(); }
    public Instant getGraceUntil() { return graceUntil; }
    public void setGraceUntil(Instant value) { graceUntil = value; touch(); }
    public boolean isCancellationPending() { return cancellationPending; }
    public void setCancellationPending(boolean value) { cancellationPending = value; touch(); }
    public Instant getCancellationRequestedAt() { return cancellationRequestedAt; }
    public void setCancellationRequestedAt(Instant value) { cancellationRequestedAt = value; touch(); }
    public Instant getCancellationEffectiveAt() { return cancellationEffectiveAt; }
    public void setCancellationEffectiveAt(Instant value) { cancellationEffectiveAt = value; touch(); }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant value) { lastSyncedAt = value; touch(); }
    public Instant getPendingStartedAt() { return pendingStartedAt; }
    public void setPendingStartedAt(Instant value) { pendingStartedAt = value; touch(); }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; touch(); }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private void touch() { updatedAt = Instant.now(); }
}
