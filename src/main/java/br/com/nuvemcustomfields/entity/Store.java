package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false, unique = true)
    private Long storeId;

    @Column(name = "store_name")
    private String storeName;

    @Column(name = "store_country_code", length = 2)
    private String storeCountryCode;

    @Column(name = "store_currency", length = 3)
    private String storeCurrency;

    @Column(name = "store_email")
    private String storeEmail;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan = PlanType.FREE;

    @Column(name = "subscription_id")
    private String subscriptionId;

    @Column(name = "courtesy_premium", nullable = false)
    private boolean courtesyPremium;

    @Column(name = "courtesy_premium_reason")
    private String courtesyPremiumReason;

    @Column(name = "premium_bonus_started_at")
    private Instant premiumBonusStartedAt;

    @Column(name = "premium_bonus_expires_at")
    private Instant premiumBonusExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_bonus_plan", length = 30)
    private PlanType premiumBonusPlan;

    public Instant getPremiumBonusStartedAt() {
        return premiumBonusStartedAt;
    }

    public void setPremiumBonusStartedAt(Instant value) {
        premiumBonusStartedAt = value;
    }

    public Instant getPremiumBonusExpiresAt() {
        return premiumBonusExpiresAt;
    }

    public void setPremiumBonusExpiresAt(Instant value) {
        premiumBonusExpiresAt = value;
    }

    public PlanType getPremiumBonusPlan() {
        // Bonuses created before V16 were always Essencial.
        return premiumBonusPlan == null ? PlanType.PREMIUM : premiumBonusPlan;
    }

    public void setPremiumBonusPlan(PlanType premiumBonusPlan) {
        this.premiumBonusPlan = premiumBonusPlan;
    }

    public boolean isPremiumBonusActive() {
        return premiumBonusExpiresAt != null && Instant.now().isBefore(premiumBonusExpiresAt);
    }

    public long getPremiumBonusDurationDays() {
        if (premiumBonusStartedAt == null || premiumBonusExpiresAt == null) return 30;
        return java.time.temporal.ChronoUnit.DAYS.between(premiumBonusStartedAt, premiumBonusExpiresAt);
    }

    public long getPremiumBonusDaysRemaining() {
        if (!isPremiumBonusActive()) {
            return 0;
        }
        long secondsRemaining = Duration.between(Instant.now(), premiumBonusExpiresAt).getSeconds();
        return Math.max(1, (secondsRemaining + 86_399) / 86_400);
    }

    @Column(name = "billing_plan_external_id", length = 80)
    private String billingPlanExternalId;

    @Column(name = "billing_amount_currency", length = 3)
    private String billingAmountCurrency;

    @Column(name = "billing_amount_value", precision = 10, scale = 2)
    private BigDecimal billingAmountValue;

    @Column(name = "billing_next_execution")
    private LocalDate billingNextExecution;

    @Column(name = "billing_last_execution")
    private LocalDate billingLastExecution;

    @Column(name = "billing_suspended", nullable = false)
    private boolean billingSuspended;

    @Column(name = "billing_last_synced_at")
    private Instant billingLastSyncedAt;

    @Column(name = "billing_last_error", length = 500)
    private String billingLastError;

    @Column(name = "product_text_color", length = 7)
    private String productTextColor;

    @Column(name = "checkout_text_color", length = 7)
    private String checkoutTextColor;

    @Column(name = "cart_text_color", length = 7)
    private String cartTextColor;

    @Column(name = "installed_at", nullable = false, updatable = false)
    private Instant installedAt = Instant.now();

    @Column(name = "uninstalled_at")
    private Instant uninstalledAt;

    @Column(name = "erasure_requested_at")
    private Instant erasureRequestedAt;

    public Instant getErasureRequestedAt() { return erasureRequestedAt; }
    public void setErasureRequestedAt(Instant at) {
        erasureRequestedAt = at == null ? null : at.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public boolean isErasurePending() { return erasureRequestedAt != null; }

    @Column(name = "departure_reason", length = 160)
    private String departureReason;
    @Column(name = "departure_justification", length = 2000)
    private String departureJustification;
    public String getDepartureReason() { return departureReason; }
    public void setDepartureReason(String reason) { departureReason = reason; }
    public String getDepartureJustification() { return departureJustification; }
    public void setDepartureJustification(String justification) { departureJustification = justification; }

    @Column(name = "departure_counted", nullable = false)
    @org.hibernate.annotations.ColumnDefault("false")
    private boolean departureCounted;

    public boolean isDepartureCounted() {
        return departureCounted;
    }

    public void setDepartureCounted(boolean departureCounted) {
        this.departureCounted = departureCounted;
    }

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getStoreCountryCode() {
        return storeCountryCode;
    }

    public void setStoreCountryCode(String storeCountryCode) {
        this.storeCountryCode = normalizeUpper(storeCountryCode);
    }

    public String getStoreCurrency() {
        return storeCurrency;
    }

    public void setStoreCurrency(String storeCurrency) {
        this.storeCurrency = normalizeUpper(storeCurrency);
    }

    public String getStoreEmail() {
        return storeEmail;
    }

    public void setStoreEmail(String storeEmail) {
        this.storeEmail = storeEmail == null || storeEmail.isBlank() ? null : storeEmail.strip().toLowerCase();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public PlanType getPlan() {
        return plan;
    }

    public void setPlan(PlanType plan) {
        this.plan = plan;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public boolean isCourtesyPremium() {
        return courtesyPremium || isPremiumBonusActive();
    }

    public void setCourtesyPremium(boolean courtesyPremium) {
        this.courtesyPremium = courtesyPremium;
    }

    public String getCourtesyPremiumReason() {
        return courtesyPremiumReason;
    }

    public void setCourtesyPremiumReason(String courtesyPremiumReason) {
        this.courtesyPremiumReason = courtesyPremiumReason;
    }

    public String getBillingPlanExternalId() {
        return billingPlanExternalId;
    }

    public void setBillingPlanExternalId(String billingPlanExternalId) {
        this.billingPlanExternalId = billingPlanExternalId;
    }

    public String getBillingAmountCurrency() {
        return billingAmountCurrency;
    }

    public void setBillingAmountCurrency(String billingAmountCurrency) {
        this.billingAmountCurrency = billingAmountCurrency;
    }

    public BigDecimal getBillingAmountValue() {
        return billingAmountValue;
    }

    public void setBillingAmountValue(BigDecimal billingAmountValue) {
        this.billingAmountValue = billingAmountValue;
    }

    public LocalDate getBillingNextExecution() {
        return billingNextExecution;
    }

    public void setBillingNextExecution(LocalDate billingNextExecution) {
        this.billingNextExecution = billingNextExecution;
    }

    public LocalDate getBillingLastExecution() {
        return billingLastExecution;
    }

    public void setBillingLastExecution(LocalDate billingLastExecution) {
        this.billingLastExecution = billingLastExecution;
    }

    public boolean isBillingSuspended() {
        return billingSuspended;
    }

    public void setBillingSuspended(boolean billingSuspended) {
        this.billingSuspended = billingSuspended;
    }

    public Instant getBillingLastSyncedAt() {
        return billingLastSyncedAt;
    }

    public void setBillingLastSyncedAt(Instant billingLastSyncedAt) {
        this.billingLastSyncedAt = billingLastSyncedAt;
    }

    public String getBillingLastError() {
        return billingLastError;
    }

    public void setBillingLastError(String billingLastError) {
        this.billingLastError = billingLastError;
    }

    public PlanType getEffectivePlan() {
        if (!plan.isBillable() && isPremiumBonusActive()) {
            return getPremiumBonusPlan();
        }
        return billingSuspended && plan.isBillable() ? PlanType.FREE : plan;
    }

    public String getProductTextColor() {
        return productTextColor;
    }

    public void setProductTextColor(String productTextColor) {
        this.productTextColor = productTextColor;
    }

    public String getCheckoutTextColor() {
        return checkoutTextColor;
    }

    public void setCheckoutTextColor(String checkoutTextColor) {
        this.checkoutTextColor = checkoutTextColor;
    }

    public String getCartTextColor() {
        return cartTextColor;
    }

    public void setCartTextColor(String cartTextColor) {
        this.cartTextColor = cartTextColor;
    }

    public Instant getInstalledAt() {
        return installedAt;
    }

    public Instant getUninstalledAt() {
        return uninstalledAt;
    }

    public void setUninstalledAt(Instant uninstalledAt) {
        // Match TIMESTAMP(6) before queries and deduplication, including webhook retries.
        this.uninstalledAt = uninstalledAt == null ? null
                : uninstalledAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public boolean isActive() {
        return uninstalledAt == null && !isErasurePending();
    }

    private String normalizeUpper(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase();
    }
}
