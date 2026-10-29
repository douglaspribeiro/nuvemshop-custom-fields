package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="store_templates")
public class StoreTemplate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="store_id",nullable=false) public Long storeId;
    @Column(nullable=false,length=100) public String name;
    @Column(length=500) public String description;
    @Column(nullable=false,length=100) public String origin;
    @Column(name="fields_json",nullable=false,columnDefinition="LONGTEXT") public String fieldsJson;
    @Version @Column(nullable=false) public long revision;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="updated_at",nullable=false) public Instant updatedAt;
}
