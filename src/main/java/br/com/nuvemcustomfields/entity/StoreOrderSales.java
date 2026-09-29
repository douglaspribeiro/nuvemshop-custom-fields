package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_order_sales")
public class StoreOrderSales {

    @EmbeddedId
    private StoreOrderSalesId id;

    @Column(nullable = false)
    private long totalItems;

    @Column(nullable = false)
    private long personalizedItems;

    protected StoreOrderSales() {
    }

    public StoreOrderSales(Long storeId, Long orderId, long totalItems, long personalizedItems) {
        this.id = new StoreOrderSalesId(storeId, orderId);
        this.totalItems = totalItems;
        this.personalizedItems = personalizedItems;
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
}
