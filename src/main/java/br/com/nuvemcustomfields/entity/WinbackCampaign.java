package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "winback_campaigns", uniqueConstraints = @UniqueConstraint(
        name = "uk_campaign_departure", columnNames = {"store_id", "uninstalled_at"}))
public class WinbackCampaign {
    @Id @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "uninstalled_at", nullable = false) private Instant uninstalledAt;
    @Column(name = "reinstalled_at") private Instant reinstalledAt;
    @Column(name = "opted_out_at") private Instant optedOutAt;
    @Column(length = 40) private String reason;
    @Column(length = 2000) private String response;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "paid_before_departure", nullable = false)
    @org.hibernate.annotations.ColumnDefault("false")
    private boolean paidBeforeDeparture;
    @Column(name = "converted_at") private Instant convertedAt;
    @Column(name = "feature_status", length = 30) private String featureStatus;
    protected WinbackCampaign() { }
    public WinbackCampaign(Long storeId, Instant uninstalledAt) {
        this.storeId = storeId;
        this.uninstalledAt = uninstalledAt;
    }
    public String getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Instant getUninstalledAt() { return uninstalledAt; }
    public Instant getReinstalledAt() { return reinstalledAt; }
    public Instant getOptedOutAt() { return optedOutAt; }
    public String getReason() { return reason; }
    public String getReasonLabel() {
        if (reason == null) return "Ainda não informado";
        return switch (reason) {
            case "PRICE" -> "Preço";
            case "CONFIGURATION" -> "Dificuldade de configuração";
            case "NO_LONGER_NEEDED" -> "Não preciso mais";
            case "MISSING_FEATURE" -> "Faltou uma funcionalidade";
            default -> "Outro";
        };
    }
    public String getResponse() { return response; }
    public Instant getRespondedAt() { return respondedAt; }
    public boolean isPaidBeforeDeparture() { return paidBeforeDeparture; }
    public void setPaidBeforeDeparture(boolean paid) { paidBeforeDeparture = paid; }
    public Instant getConvertedAt() { return convertedAt; }
    public void converted() { if (convertedAt == null) convertedAt = Instant.now(); }
    public String getFeatureStatus() { return featureStatus; }
    public void setFeatureStatus(String status) { featureStatus = status; }
    public void reinstalled(Instant at) { if (reinstalledAt == null) reinstalledAt = at; }
    public void optOut() { if (optedOutAt == null) optedOutAt = Instant.now(); }
    public void respond(String reason, String response) {
        this.reason = reason;
        this.response = response;
        this.respondedAt = Instant.now();
        if ("MISSING_FEATURE".equals(reason) && featureStatus == null) featureStatus = "NOVA";
    }
}
