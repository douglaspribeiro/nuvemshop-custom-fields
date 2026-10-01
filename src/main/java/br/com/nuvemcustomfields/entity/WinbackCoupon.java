package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "winback_coupons")
public class WinbackCoupon {
    @Id @Column(length = 36)
    private String code = "VOLTE50-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    @Column(name = "store_id", nullable = false, unique = true) private Long storeId;
    @Column(name = "campaign_id", nullable = false, unique = true, length = 36) private String campaignId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
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
        return "Disponível";
    }
}
