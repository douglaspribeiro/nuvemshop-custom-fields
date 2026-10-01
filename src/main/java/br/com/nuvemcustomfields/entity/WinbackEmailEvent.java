package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "winback_email_events")
public class WinbackEmailEvent {
    @Id @Column(length = 64) private String id;
    @Column(name = "email_id", nullable = false, length = 36) private String emailId;
    @Column(nullable = false, length = 40) private String type;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    // Store only the route classification, never URL query tokens, IPs or user agents.
    @Column(length = 40) private String target;
    protected WinbackEmailEvent() { }
    public WinbackEmailEvent(String id, String emailId, String type, Instant occurredAt, String target) {
        this.id = id; this.emailId = emailId; this.type = type; this.occurredAt = occurredAt; this.target = target;
    }
    public String getId() { return id; }
    public String getEmailId() { return emailId; }
    public String getType() { return type; }
    public String getLabel() {
        return switch (type) {
            case "Send" -> "Aceito pelo provedor";
            case "Delivery" -> "Entregue";
            case "Open" -> "Abertura detectada";
            case "Click" -> "Clique detectado";
            case "Bounce" -> "Falha de entrega";
            case "Complaint" -> "Reclamação";
            case "Reject" -> "Envio recusado";
            case "Rendering Failure" -> "Falha na preparação do e-mail";
            case "Subscription" -> "Preferência de comunicação alterada";
            default -> "Entrega atrasada";
        };
    }
    public Instant getOccurredAt() { return occurredAt; }
    public String getTarget() { return target; }
}
