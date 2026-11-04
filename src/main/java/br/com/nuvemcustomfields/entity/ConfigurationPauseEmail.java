package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Durable notification of a committed NC-04 pause transition. */
@Entity
@Table(name = "configuration_pause_emails")
public class ConfigurationPauseEmail {
    public enum State { PENDING, CLAIMED, SENT, SKIPPED, REVIEW, FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long storeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private State state = State.PENDING;
    @Column(nullable = false, columnDefinition = "TEXT") private String pausedProducts;
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    private Instant completedAt;
    public ConfigurationPauseEmail() { }
    public ConfigurationPauseEmail(Long storeId, String products) { this.storeId = storeId; pausedProducts = products; }
    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public State getState() { return state; }
    public String getPausedProducts() { return pausedProducts; }
    public Instant getCreatedAt() { return createdAt; }
    public void claim() { state = State.CLAIMED; }
    public void finish(State value) { state = value; completedAt = Instant.now(); }
}
