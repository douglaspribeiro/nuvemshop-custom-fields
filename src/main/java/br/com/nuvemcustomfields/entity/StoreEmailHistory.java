package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Schema mapping; writes are independent of the business transaction around SMTP. */
@Entity
@Table(name="store_email_history", indexes=@Index(name="ix_store_email_history", columnList="store_id,attempted_at,id"))
public class StoreEmailHistory {
    @Id @Column(length=36) private String id;
    @Column(name="store_id",nullable=false) private Long storeId;
    @Column(nullable=false,length=40) private String category;
    @Column(name="source_key",unique=true,length=120) private String sourceKey;
    @Column(length=320) private String recipient;
    @Column(length=500) private String subject;
    @Column(columnDefinition="TEXT") private String body;
    @Column(nullable=false,length=30) private String state;
    @Column(name="attempted_at",nullable=false) private Instant attemptedAt;
    @Column(name="accepted_at") private Instant acceptedAt;
}
