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

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
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
        return courtesyPremium;
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
        this.uninstalledAt = uninstalledAt;
    }

    public boolean isActive() {
        return uninstalledAt == null;
    }

    private String normalizeUpper(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase();
    }
}
