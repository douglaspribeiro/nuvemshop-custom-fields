package br.com.nuvemcustomfields.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="template_product_sources",uniqueConstraints=@UniqueConstraint(columnNames={"store_id","product_id"}))
public class TemplateProductSource {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="store_id",nullable=false) private Long storeId;
 @Column(name="product_id",nullable=false) private Long productId;
 @Column(name="source_key",nullable=false,length=100) private String sourceKey;
 @Column(name="source_revision",nullable=false) private long sourceRevision;
 @Column(name="template_json",nullable=false,columnDefinition="LONGTEXT") private String templateJson;
 @Column(name="configuration_json",nullable=false,columnDefinition="LONGTEXT") private String configurationJson;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
}
