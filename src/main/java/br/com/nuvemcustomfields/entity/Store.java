package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

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

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan = PlanType.FREE;

    @Column(name = "subscription_id")
    private String subscriptionId;

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
}
