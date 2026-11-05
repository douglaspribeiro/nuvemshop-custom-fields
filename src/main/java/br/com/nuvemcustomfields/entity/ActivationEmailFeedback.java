package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "nc39_activation_email_feedback")
public class ActivationEmailFeedback {
    @Id @Column(length = 64) private String id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "event_id", nullable = false) private Long eventId;
    @Column(name = "event_type", nullable = false, length = 16) private String eventType;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    protected ActivationEmailFeedback() { }
    public ActivationEmailFeedback(String id, Long storeId, Long eventId, String type, Instant at) {
        this.id = id; this.storeId = storeId; this.eventId = eventId; this.eventType = type; this.occurredAt = at;
    }
}
