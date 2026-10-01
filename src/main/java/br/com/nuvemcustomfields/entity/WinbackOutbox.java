package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "winback_outbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_winback_store_uninstall", columnNames = {"store_id", "uninstalled_at"}))
public class WinbackOutbox {
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();
    @Column(name = "store_id", nullable = false)
    private Long storeId;
    @Column(name = "uninstalled_at", nullable = false)
    private Instant uninstalledAt;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();
    @Column(name = "published_at")
    private Instant publishedAt;

    protected WinbackOutbox() { }
    public WinbackOutbox(Long storeId, Instant uninstalledAt) {
        this.storeId = storeId;
        this.uninstalledAt = uninstalledAt;
    }
    public String getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Instant getUninstalledAt() { return uninstalledAt; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void published() { publishedAt = Instant.now(); }
    public void retry() {
        attempts++;
        nextAttemptAt = Instant.now().plusSeconds(Math.min(3600, 30L << Math.min(attempts - 1, 7)));
    }
}
