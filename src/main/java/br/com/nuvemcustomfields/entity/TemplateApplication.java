package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="template_applications",uniqueConstraints=@UniqueConstraint(columnNames={"store_id","request_id"}))
public class TemplateApplication {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="store_id",nullable=false) private Long storeId;
    @Column(name="request_id",nullable=false,length=36) private String requestId;
    @Column(name="source_key",nullable=false,length=100) private String sourceKey;
    @Column(name="product_id",nullable=false) private Long productId;
    @Column(name="apply_mode",nullable=false,length=12) private String applyMode;
    @Column(name="review_token",nullable=false,length=64) private String reviewToken;
    @Column(name="field_count",nullable=false) private int fieldCount;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}
