package br.com.nuvemcustomfields.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class Store {

    private Long id;
    private Long storeId;
    private String storeName;
    private String accessToken;
    private String scope;
    private PlanType plan = PlanType.FREE;
    private String subscriptionId;
    private boolean courtesyPremium;
    private String courtesyPremiumReason;
    private String billingPlanExternalId;
    private String billingAmountCurrency;
    private BigDecimal billingAmountValue;
    private LocalDate billingNextExecution;
    private LocalDate billingLastExecution;
    private boolean billingSuspended;
    private Instant billingLastSyncedAt;
    private String billingLastError;
    private String productTextColor;
    private String checkoutTextColor;
    private String cartTextColor;
    private Instant installedAt = Instant.now();
    private Instant uninstalledAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
        return billingSuspended ? PlanType.FREE : plan;
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

    public void setInstalledAt(Instant installedAt) {
        this.installedAt = installedAt;
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
}
