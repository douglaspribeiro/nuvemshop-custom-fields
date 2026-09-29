package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "store_order_sales")
public class StoreOrderSales {

    @EmbeddedId
    private StoreOrderSalesId id;

    @Column(nullable = false)
    private long totalItems;

    @Column(nullable = false)
    private long personalizedItems;

    private Instant createdAt;

    @Column(precision = 20, scale = 2)
    private BigDecimal personalizedProductValue;

    @Column(length = 3)
    private String currency;

    protected StoreOrderSales() {
    }

    public StoreOrderSales(Long storeId, Long orderId, long totalItems, long personalizedItems) {
        this(storeId, orderId, totalItems, personalizedItems, null, null, null);
    }

    public StoreOrderSales(Long storeId, Long orderId, long totalItems, long personalizedItems,
                           Instant createdAt, BigDecimal personalizedProductValue) {
        this(storeId, orderId, totalItems, personalizedItems, createdAt, personalizedProductValue, null);
    }

    public StoreOrderSales(Long storeId, Long orderId, long totalItems, long personalizedItems,
                           Instant createdAt, BigDecimal personalizedProductValue, String currency) {
        this.id = new StoreOrderSalesId(storeId, orderId);
        this.totalItems = totalItems;
        this.personalizedItems = personalizedItems;
        this.createdAt = createdAt;
        this.personalizedProductValue = personalizedProductValue;
        this.currency = currency;
    }

    public StoreOrderSalesId getId() {
        return id;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public long getPersonalizedItems() {
        return personalizedItems;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getPersonalizedProductValue() {
        return personalizedProductValue;
    }

    public String getCurrency() {
        return currency;
    }
}
