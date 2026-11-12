package br.com.nuvemcustomfields.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="template_batches")
public class TemplateBatch {
 @Id @Column(length=36) public String id;
 @Column(name="store_id",nullable=false) public Long storeId;
 @Column(name="source_key",nullable=false,length=100) private String sourceKey;
 @Column(name="apply_mode",nullable=false,length=10) private String applyMode;
 @Column(name="total_products",nullable=false) private int totalProducts;
 @Column(name="succeeded_products",nullable=false) private int succeededProducts;
 @Column(name="document_json",nullable=false,columnDefinition="LONGTEXT") public String documentJson;
 @Column(name="created_at",nullable=false) public Instant createdAt;
}
