package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class StoreOrderSalesId implements Serializable {

    private Long storeId;
    private Long orderId;

    protected StoreOrderSalesId() {
    }

    public StoreOrderSalesId(Long storeId, Long orderId) {
        this.storeId = storeId;
        this.orderId = orderId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public Long getOrderId() {
        return orderId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StoreOrderSalesId id
                && Objects.equals(storeId, id.storeId) && Objects.equals(orderId, id.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeId, orderId);
    }
}
