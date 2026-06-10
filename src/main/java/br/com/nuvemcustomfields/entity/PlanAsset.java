package br.com.nuvemcustomfields.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class PlanAsset {

    private Long id;
    private PlanType planType;
    private String displayName;
    private String description;
    private String billingExternalId;
    private String currency;
    private BigDecimal amount;
    private long productLimit;
    private long fieldLimit;
    private LocalDate effectiveFrom;
    private LocalDate effectiveUntil;
    private boolean active = true;
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public void setPlanType(PlanType planType) {
        this.planType = planType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBillingExternalId() {
        return billingExternalId;
    }

    public void setBillingExternalId(String billingExternalId) {
        this.billingExternalId = billingExternalId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public long getProductLimit() {
        return productLimit;
    }

    public void setProductLimit(long productLimit) {
        this.productLimit = productLimit;
    }

    public long getFieldLimit() {
        return fieldLimit;
    }

    public void setFieldLimit(long fieldLimit) {
        this.fieldLimit = fieldLimit;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveUntil() {
        return effectiveUntil;
    }

    public void setEffectiveUntil(LocalDate effectiveUntil) {
        this.effectiveUntil = effectiveUntil;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEffectiveOn(LocalDate date) {
        return active
                && !effectiveFrom.isAfter(date)
                && (effectiveUntil == null || !effectiveUntil.isBefore(date));
    }
}
