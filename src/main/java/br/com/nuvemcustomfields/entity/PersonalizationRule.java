package br.com.nuvemcustomfields.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PersonalizationRule {

    private Long id;
    private CommercePlatform platform = CommercePlatform.NUVEMSHOP;
    private Long storeId;
    private Long productId;
    private String productName;
    private boolean enabled = true;
    private Instant createdAt = Instant.now();
    private List<PersonalizationField> fields = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CommercePlatform getPlatform() {
        return platform;
    }

    public void setPlatform(CommercePlatform platform) {
        this.platform = platform;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<PersonalizationField> getFields() {
        return fields;
    }
}
