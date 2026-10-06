package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="creem_catalog_publications")
public class CreemCatalogPublication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name="publication_key", nullable=false, unique=true, length=64)
    private String publicationKey;
    @Column(name="reservation_key", unique=true, length=64)
    private String reservationKey;
    public String getReservationKey() { return reservationKey; }
    public void setReservationKey(String value) { reservationKey = value; }
    @Column(name="payload_json", nullable=false, columnDefinition="TEXT")
    private String payloadJson;
    @Column(name="country_code", nullable=false, length=2)
    private String countryCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30)
    private PlanType plan;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private PaymentEnvironment environment;
    @Column(nullable=false,length=20)
    private String status = "CREATING";
    @Column(name="product_id",length=120)
    private String productId;
    @Column(name="last_error",length=500)
    private String lastError;
    @Column(name="operator_name",nullable=false,length=120)
    private String operatorName;
    @Column(name="created_at",nullable=false)
    private Instant createdAt = Instant.now();
    @Column(name="lease_until")
    private Instant leaseUntil;
    public Long getId() { return id; }
    public void setId(Long value) { id = value; }
    public String getPublicationKey() { return publicationKey; }
    public void setPublicationKey(String value) { publicationKey = value; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String value) { payloadJson = value; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String value) { countryCode = value; }
    public PlanType getPlan() { return plan; }
    public void setPlan(PlanType value) { plan = value; }
    public PaymentEnvironment getEnvironment() { return environment; }
    public void setEnvironment(PaymentEnvironment value) { environment = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getProductId() { return productId; }
    public void setProductId(String value) { productId = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String value) { operatorName = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant value) { leaseUntil = value; }
}
