package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Store-scoped NC-39 suppression; never disables transactional billing/support messages. */
@Entity
@Table(name = "nc39_activation_email_suppressions")
public class ActivationEmailSuppression {
    @Id @Column(name = "store_id") private Long storeId;
    @Column(nullable = false, length = 32) private String reason;
    @Column(name = "suppressed_at", nullable = false) private Instant suppressedAt;
    protected ActivationEmailSuppression() { }
    public ActivationEmailSuppression(Long storeId, String reason, Instant at) {
        this.storeId = storeId; this.reason = reason; this.suppressedAt = at;
    }
    public String getReason() { return reason; }
}
