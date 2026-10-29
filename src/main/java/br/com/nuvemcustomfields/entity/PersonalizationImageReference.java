package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;

@Entity @Table(name="personalization_image_references",uniqueConstraints=@UniqueConstraint(columnNames={"image_id","owner_type","owner_id"}))
public class PersonalizationImageReference {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="image_id",nullable=false,length=36) private String imageId;
    @Column(name="store_id",nullable=false) private Long storeId;
    @Column(name="owner_type",nullable=false,length=12) private String ownerType;
    @Column(name="owner_id",nullable=false) private Long ownerId;
    @Column(name="product_id",nullable=false) private Long productId;
}
