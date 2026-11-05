package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "nc39_activation_email_outbox", uniqueConstraints = @UniqueConstraint(
        name = "uk_nc39_store_install_step", columnNames = {"store_id", "installed_at", "step"}))
public class ActivationEmailOutbox {
    public enum State { PENDING, CLAIMED, SENT, SIMULATED, SUPPRESSED, FAILED, REVIEW, ACTIVATED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "installed_at", nullable = false) private Instant installedAt;
    @Column(nullable = false, length = 12) private String step;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private State state = State.PENDING;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "suppression_reason", length = 80) private String suppressionReason;
    @Column(name = "template_version", nullable = false, length = 32) private String templateVersion;

    protected ActivationEmailOutbox() { }
    public ActivationEmailOutbox(Long storeId, Instant installedAt, String step, Instant due, String templateVersion) {
        this.storeId = storeId; this.installedAt = installedAt; this.step = step;
        this.nextAttemptAt = due; this.templateVersion = templateVersion;
    }
    public Long getId() { return id; }
    public Long getStoreId() { return storeId; }
    public Instant getInstalledAt() { return installedAt; }
    public String getStep() { return step; }
    public State getState() { return state; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getTemplateVersion() { return templateVersion; }
    public String getSuppressionReason() { return suppressionReason; }
    public void claim() { state = State.CLAIMED; attempts++; }
    public void review() { state = State.REVIEW; suppressionReason = "smtp_result_unknown"; }
    public void sent(Instant at) { state = State.SENT; sentAt = at; }
    public void simulated() { state = State.SIMULATED; }
    public void activated() { state = State.ACTIVATED; }
    public void suppress(String reason) { state = State.SUPPRESSED; suppressionReason = reason; }
    public void failed(Instant now) {
        if (attempts >= 3) { state = State.FAILED; return; }
        state = State.PENDING;
        nextAttemptAt = now.plusSeconds(60L * (1L << (attempts - 1)));
    }
}
