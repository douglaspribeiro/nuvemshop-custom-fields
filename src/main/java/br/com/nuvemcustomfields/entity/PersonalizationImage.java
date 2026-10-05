package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Durable upload ledger and deletion outbox; deliberately has no owner foreign keys. */
@Entity @Table(name = "personalization_images")
public class PersonalizationImage {
    @Id @Column(length=36) private String id;
    @Column(name="store_id", nullable=false) private Long storeId;
    @Column(name="product_id", nullable=false) private Long productId;
    @Column(name="field_id") private Long fieldId;
    @Column(nullable=false) private String bucket;
    @Column(name="preview_key", nullable=false, length=512) private String previewKey;
    @Column(name="thumbnail_key", nullable=false, length=512) private String thumbnailKey;
    @Column(nullable=false, length=20) private String state;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Column(name="retry_at", nullable=false) private Instant retryAt;
    @Column(name="last_error", length=500) private String lastError;
}
