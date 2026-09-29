package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "store_sales_sync")
public class StoreSalesSync {

    @Id
    private Long storeId;

    @Column(nullable = false)
    private boolean complete;

    @Column(nullable = false)
    private boolean productValueBackfilled;

    private Instant lastAttemptAt;
    private Instant lastSyncedAt;

    @Column(length = 500)
    private String lastError;

    protected StoreSalesSync() {
    }

    public StoreSalesSync(Long storeId) {
        this.storeId = storeId;
    }

    public Long getStoreId() {
        return storeId;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isProductValueBackfilled() {
        return productValueBackfilled;
    }

    public void setProductValueBackfilled(boolean productValueBackfilled) {
        this.productValueBackfilled = productValueBackfilled;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
