package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="upgrade_coupon_uses")
public class UpgradeCouponUse implements EfiFirstPaymentDiscountUse {
    public enum Status { RESERVED, USED, RELEASED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long couponId;
    @Column(nullable=false) private Long storeId;
    @Column(unique=true,length=36) private String adjustmentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.RESERVED;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(unique=true, length=64) private String paymentReference;
    @Column(length=36) private String couponCode;
    @Column(length=120) private String nativeDiscountId;
    public String getNativeDiscountId() { return nativeDiscountId; }
    public void setNativeDiscountId(String id) { nativeDiscountId = id; }
    @Column(length=120) private String subscriptionId;
    @Column(length=120) private String firstPaymentId;
    @Column(precision=12, scale=2) private java.math.BigDecimal regularAmount;
    @Column(precision=12, scale=2) private java.math.BigDecimal firstAmount;
    private Instant usedAt;
    private Instant priceRestoredAt;
    private Instant recurrenceStoppedAt;
    public String getPaymentReference() { return paymentReference; }
    public String getExternalReference() { return paymentReference; }
    public String getCouponCode() { return couponCode; }
    public String getSubscriptionId() { return subscriptionId; }
    public String getFirstPaymentId() { return firstPaymentId; }
    public java.math.BigDecimal getRegularAmount() { return regularAmount; }
    public java.math.BigDecimal getFirstAmount() { return firstAmount; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getPriceRestoredAt() { return priceRestoredAt; }
    public Instant getRecurrenceStoppedAt() { return recurrenceStoppedAt; }
    public void payment(String reference, String code, java.math.BigDecimal regular, java.math.BigDecimal first) {
        paymentReference = reference; couponCode = code; regularAmount = regular; firstAmount = first;
    }
    public void subscription(String id) { subscriptionId = id; }
    public void firstPayment(String id) { firstPaymentId = id; }
    public void used() { if (usedAt == null) usedAt = Instant.now(); status = Status.USED; }
    public void restored() { if (priceRestoredAt == null) priceRestoredAt = Instant.now(); }
    public void stopped() { if (recurrenceStoppedAt == null) recurrenceStoppedAt = Instant.now(); }
    public Long getId(){return id;}
    public Long getCouponId(){return couponId;} public void setCouponId(Long v){couponId=v;}
    public Long getStoreId(){return storeId;} public void setStoreId(Long v){storeId=v;}
    public String getAdjustmentId(){return adjustmentId;} public void setAdjustmentId(String v){adjustmentId=v;}
    public Status getStatus(){return status;} public void setStatus(Status v){status=v;}
    public Instant getCreatedAt(){return createdAt;}
}
