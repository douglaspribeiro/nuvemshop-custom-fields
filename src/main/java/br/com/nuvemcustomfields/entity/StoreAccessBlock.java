package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Retains only the denied identity, independently of the store's installation data. */
@Entity
@Table(name = "store_access_blocks")
public class StoreAccessBlock {
    @Id @Column(name = "store_id")
    private Long storeId;
    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;
}
