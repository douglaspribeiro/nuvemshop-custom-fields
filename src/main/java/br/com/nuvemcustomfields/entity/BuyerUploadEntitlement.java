package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Explicit new-offer grant, separate from legacy plan names. No automatic grants. */
@Entity
@Table(name = "buyer_upload_entitlements")
public class BuyerUploadEntitlement {
    @Id @Column(name = "store_id") private Long storeId;
    @Column(name = "offer_code", nullable = false, length = 40) private String offerCode;
    @Column(name = "quota_bytes", nullable = false) private long quotaBytes;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
