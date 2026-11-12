package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="buyer_upload_sessions")
public class BuyerUploadSession {
    @Id @Column(name="session_hash",length=64) private String sessionHash;
    @Column(name="store_id",nullable=false) private Long storeId;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
}
