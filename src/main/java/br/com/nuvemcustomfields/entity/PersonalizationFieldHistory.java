package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;

/** Durable field names used to recognize order properties after configuration changes. */
@Entity
@Table(name = "personalization_field_history", uniqueConstraints = @UniqueConstraint(
        name = "uk_field_history", columnNames = {"store_id", "product_id", "property_name"}))
public class PersonalizationFieldHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "store_id", nullable = false) private Long storeId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "property_name", nullable = false, length = 100) private String propertyName;
    protected PersonalizationFieldHistory() { }
    public PersonalizationFieldHistory(Long storeId, Long productId, String propertyName) {
        this.storeId = storeId; this.productId = productId; this.propertyName = propertyName;
    }
    public Long getStoreId() { return storeId; }
    public Long getProductId() { return productId; }
    public String getPropertyName() { return propertyName; }
}
