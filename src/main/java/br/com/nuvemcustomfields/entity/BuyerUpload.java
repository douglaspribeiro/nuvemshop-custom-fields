package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Schema mapping; lifecycle writes are serialized by BuyerUploadLedger. */
@Entity
@Table(name = "buyer_uploads", uniqueConstraints = @UniqueConstraint(name = "uk_buyer_upload_request",
        columnNames = {"store_id", "session_hash", "request_id"}))
public class BuyerUpload {
    @Id @Column(length = 36) private String id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "field_id", nullable = false) private Long fieldId;
    @Column(name = "session_hash", nullable = false, length = 64) private String sessionHash;
    @Column(name = "request_id", nullable = false, length = 36) private String requestId;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 20) private String state;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "order_item_id") private Long orderItemId;
    @Column(name = "order_created_at") private Instant orderCreatedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "delete_reason", length = 20) private String deleteReason;
    @Column(name = "original_name", length = 180) private String originalName;
    @Column(name = "content_type", length = 40) private String contentType;
    @Column(length = 120) private String bucket;
    @Column(name = "object_key", length = 255) private String objectKey;
    @Column(name = "retry_at") private Instant retryAt;
    @Column(name = "delete_attempts",nullable=false) @org.hibernate.annotations.ColumnDefault("0") private int deleteAttempts;
}
