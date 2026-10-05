package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="ga4_outbox")
public class Ga4Outbox {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=180) private String eventKey;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant nextAttemptAt=Instant.now();
    private Instant deliveredAt;
    private Instant expiredAt;
    @Column(nullable=false) private int attempts;
    protected Ga4Outbox() {}
    public Ga4Outbox(String key,String payload){this.eventKey=key;this.payload=payload;}
    public String getPayload(){return payload;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getDeliveredAt(){return deliveredAt;}
    public int getAttempts(){return attempts;}
    public void delivered(){deliveredAt=Instant.now();}
    public void expire(){expiredAt=Instant.now();}
    public void retry(){attempts++;nextAttemptAt=Instant.now().plusSeconds(Math.min(3600,30L << Math.min(attempts-1,7)));}
}
