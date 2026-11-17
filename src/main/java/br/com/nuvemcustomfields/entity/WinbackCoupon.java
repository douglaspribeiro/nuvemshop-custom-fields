package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "winback_coupons")
public class WinbackCoupon implements EfiFirstPaymentDiscountUse {
    @Id @Column(length = 36)
    private String code = "VOLTE50-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    @Column(name = "store_id", nullable = false, unique = true) private Long storeId;
    @Column(name = "campaign_id", nullable = false, unique = true, length = 36) private String campaignId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    @Column(name = "expires_at", nullable = false) private Instant expiresAt = createdAt.plus(30, ChronoUnit.DAYS);
    @Column(name = "checkout_at") private Instant checkoutAt;
    @Column(name = "external_reference", length = 64) private String externalReference;
    @Column(name = "subscription_id", length = 120) private String subscriptionId;
    @Column(name = "first_payment_id", length = 120) private String firstPaymentId;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "regular_amount", precision = 12, scale = 2) private BigDecimal regularAmount;
    @Column(name = "first_amount", precision = 12, scale = 2) private BigDecimal firstAmount;
    @Column(name = "price_restored_at") private Instant priceRestoredAt;
    @Column(name = "recurrence_stopped_at") private Instant recurrenceStoppedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30)
    private PaymentProviderType provider = PaymentProviderType.EFI;
    @Enumerated(EnumType.STRING) @Column(length=20) private PaymentEnvironment providerEnvironment;
    @Column(length=120) private String nativeDiscountId;
    @Column(nullable=false) private int nativeSyncAttempts;
    private Instant nativeSyncAfter;
    private Instant nativeConfirmedAt;
    @Column(length=255) private String nativeSyncError;
    public PaymentProviderType getProvider() { return provider; }
    public PaymentEnvironment getProviderEnvironment() { return providerEnvironment; }
    public String getNativeDiscountId() { return nativeDiscountId; }
    public int getNativeSyncAttempts() { return nativeSyncAttempts; }
    public Instant getNativeSyncAfter() { return nativeSyncAfter; }
    public Instant getNativeConfirmedAt() { return nativeConfirmedAt; }
    public String getNativeSyncError() { return nativeSyncError; }
    public void useDodo(PaymentProviderType value, PaymentEnvironment environment) {
        if (value == null || !value.isDodo()) throw new IllegalArgumentException("Gateway Dodo inválido.");
        provider = value; providerEnvironment = environment;
    }
    public void nativeConfirmed(String id) { nativeDiscountId=id; nativeSyncAfter=null; nativeSyncError=null; nativeConfirmedAt=Instant.now().truncatedTo(ChronoUnit.MICROS); }
    public void nativeFailed() {
        nativeSyncAttempts++; nativeSyncAfter=Instant.now().plusSeconds(Math.min(3600,30L << Math.min(7,nativeSyncAttempts-1)));
        nativeSyncError="Não foi possível confirmar o cupom no Dodo; nova tentativa agendada.";
    }
    protected WinbackCoupon() { }
    public WinbackCoupon(Long storeId, String campaignId) { this.storeId = storeId; this.campaignId = campaignId; }
    public String getCode() { return code; }
    public Long getStoreId() { return storeId; }
    public String getCampaignId() { return campaignId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCheckoutAt() { return checkoutAt; }
    public String getExternalReference() { return externalReference; }
    public String getSubscriptionId() { return subscriptionId; }
    public String getFirstPaymentId() { return firstPaymentId; }
    public Instant getUsedAt() { return usedAt; }
    public BigDecimal getRegularAmount() { return regularAmount; }
    public BigDecimal getFirstAmount() { return firstAmount; }
    public Instant getPriceRestoredAt() { return priceRestoredAt; }
    public Instant getRecurrenceStoppedAt() { return recurrenceStoppedAt; }
    public void reserve(String reference, BigDecimal regular, BigDecimal first) {
        externalReference = reference; regularAmount = regular; firstAmount = first;
        subscriptionId = null; firstPaymentId = null; priceRestoredAt = null; recurrenceStoppedAt = null; checkoutAt = Instant.now();
    }
    public void subscription(String id) { subscriptionId = id; }
    public void firstPayment(String id) { firstPaymentId = id; }
    public void used() { if (usedAt == null) usedAt = Instant.now(); }
    public void restored() { if (priceRestoredAt == null) priceRestoredAt = Instant.now(); }
    public void stopped() { if (recurrenceStoppedAt == null) recurrenceStoppedAt = Instant.now(); }
    public String getStatusLabel() {
        if (usedAt != null) return "Usado em pagamento confirmado";
        if (recurrenceStoppedAt != null) return "Primeira cobrança não aprovada; recorrência encerrada";
        if (checkoutAt != null) return "Aplicado no checkout, aguardando pagamento";
        if (expiresAt.isBefore(Instant.now())) return "Expirado";
        if (provider.isDodo() && nativeDiscountId == null) return "Aguardando confirmação do cupom no Dodo";
        return "Disponível";
    }
}
