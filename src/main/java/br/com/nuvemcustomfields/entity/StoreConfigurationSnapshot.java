package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Local configuration at departure; removed with the rest of the store's data. */
@Entity
@Table(name = "store_configuration_snapshots", uniqueConstraints = @UniqueConstraint(
        name = "uk_configuration_departure", columnNames = {"store_id", "uninstalled_at"}))
public class StoreConfigurationSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "uninstalled_at", nullable = false) private Instant uninstalledAt;
    @Column(name = "configuration_json", nullable = false, columnDefinition = "LONGTEXT") private String configurationJson;
    @Column(name = "departure_reason", length = 160) private String departureReason;
    @Column(name = "departure_justification", length = 2000) private String departureJustification;
    protected StoreConfigurationSnapshot() { }
    public StoreConfigurationSnapshot(Long storeId, Instant uninstalledAt, String configurationJson) {
        this.storeId = storeId; this.uninstalledAt = uninstalledAt; this.configurationJson = configurationJson;
    }
    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Instant getUninstalledAt() { return uninstalledAt; }
    public String getConfigurationJson() { return configurationJson; }
    public String getDepartureReason() { return departureReason; }
    public String getDepartureJustification() { return departureJustification; }
    public void recordReason(String reason, String justification) {
        departureReason = reason; departureJustification = justification;
    }
}
