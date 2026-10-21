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
    private boolean personalizedValueBackfilled;

    private Instant historicalThrough;

    public Instant getHistoricalThrough() { return historicalThrough; }
    public void setHistoricalThrough(Instant value) { historicalThrough = value; }

    public String issueLabel() {
        if (lastError == null) return null;
        return switch (lastError) {
            case "AUTH_REQUIRED", "Unauthorized" -> "Reconectar loja: token inválido ou expirado";
            case "PLATFORM_BLOCKED" -> "Acesso ao app bloqueado pela Nuvemshop (402)";
            case "PERMISSION_REQUIRED", "Forbidden" -> "Permissão de leitura de pedidos recusada";
            case "RATE_LIMITED" -> "Limite de consultas da Nuvemshop; nova tentativa automática";
            default -> "Falha na consulta de pedidos; verificar integração";
        };
    }

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

    public boolean isPersonalizedValueBackfilled() {
        return personalizedValueBackfilled;
    }

    public void setPersonalizedValueBackfilled(boolean personalizedValueBackfilled) {
        this.personalizedValueBackfilled = personalizedValueBackfilled;
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
