package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "winback_emails", uniqueConstraints = @UniqueConstraint(
        name = "uk_campaign_email_step", columnNames = {"campaign_id", "step"}))
public class WinbackEmail {
    @Id @Column(length = 36) private String id = UUID.randomUUID().toString();
    @Column(name = "campaign_id", nullable = false, length = 36) private String campaignId;
    @Column(nullable = false, length = 40) private String step;
    @Column(nullable = false, length = 40) private String status = "PREPARED";
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "sent_at") private Instant sentAt;
    @Column(name = "ses_message_id", length = 200) private String sesMessageId;
    protected WinbackEmail() { }
    public WinbackEmail(String campaignId, String step) { this.campaignId = campaignId; this.step = step; }
    public String getId() { return id; }
    public String getCampaignId() { return campaignId; }
    public String getStep() { return step; }
    public String getStepLabel() {
        return switch (step) {
            case "FEEDBACK" -> "Motivo da saída";
            case "FOLLOWUP" -> "Resposta e oferta de retorno";
            case "FEATURE_DELIVERED" -> "Funcionalidade entregue";
            default -> step;
        };
    }
    public String getStatus() { return status; }
    public String getStatusLabel() {
        return switch (status) {
            case "SENT" -> "Enviado";
            case "SENDING" -> "Aguardando confirmação do envio";
            case "CANCELLED" -> "Cancelado";
            default -> "Preparado";
        };
    }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public String getSesMessageId() { return sesMessageId; }
    public void status(String status) { this.status = status; }
    public void sent() { status = "SENT"; sentAt = Instant.now(); }
    public void acceptedBySes(String messageId, Instant at) {
        if (sesMessageId == null) sesMessageId = messageId;
        if (sentAt == null || at.isBefore(sentAt)) sentAt = at;
        status = "SENT";
    }
}
