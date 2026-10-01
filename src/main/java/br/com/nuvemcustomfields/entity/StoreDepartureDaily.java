package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "store_departure_daily")
public class StoreDepartureDaily {
    @Id
    @Column(name = "departure_day")
    private LocalDate day;

    @Column(name = "uninstall_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long uninstallCount;

    @Column(name = "erasure_departure_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long erasureDepartureCount;

    @Column(name = "recovered_uninstall_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long recoveredUninstallCount;

    @Column(name = "recovered_erasure_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long recoveredErasureCount;
}
