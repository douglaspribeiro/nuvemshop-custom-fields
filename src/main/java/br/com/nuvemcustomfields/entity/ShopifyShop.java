package br.com.nuvemcustomfields.entity;

import java.time.Instant;

public class ShopifyShop {

    private Long id;
    private String shopDomain;
    private Long shopifyShopId;
    private String shopName;
    private String accessToken;
    private String scope;
    private PlanType plan = PlanType.FREE;
    private Instant installedAt = Instant.now();
    private Instant uninstalledAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShopDomain() {
        return shopDomain;
    }

    public void setShopDomain(String shopDomain) {
        this.shopDomain = shopDomain;
    }

    public Long getShopifyShopId() {
        return shopifyShopId;
    }

    public void setShopifyShopId(Long shopifyShopId) {
        this.shopifyShopId = shopifyShopId;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
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
