package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="payment_attempts")
public class PaymentAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="store_id",nullable=false) private Long storeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PaymentProviderType provider;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentEnvironment environment;
    @Column(name="country_code",nullable=false,length=2) private String countryCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PlanType plan;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="amount_value",nullable=false,precision=12,scale=2) private BigDecimal amountValue;
    @Column(name="external_reference",nullable=false,unique=true,length=64) private String externalReference;
    @Column(name="provider_transaction_id",length=120) private String providerTransactionId;
    @Column(name="checkout_token_hash",nullable=false,unique=true,length=64) private String checkoutTokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PaymentAttemptStatus status;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="last_error",length=500) private String lastError;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
    @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    public Long getId(){return id;} public Long getStoreId(){return storeId;} public void setStoreId(Long v){storeId=v;}
    public PaymentProviderType getProvider(){return provider;} public void setProvider(PaymentProviderType v){provider=v;}
    public PaymentEnvironment getEnvironment(){return environment;} public void setEnvironment(PaymentEnvironment v){environment=v;}
    public String getCountryCode(){return countryCode;} public void setCountryCode(String v){countryCode=v;}
    public PlanType getPlan(){return plan;} public void setPlan(PlanType v){plan=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public BigDecimal getAmountValue(){return amountValue;} public void setAmountValue(BigDecimal v){amountValue=v;}
    public String getExternalReference(){return externalReference;} public void setExternalReference(String v){externalReference=v;}
    public String getProviderTransactionId(){return providerTransactionId;} public void setProviderTransactionId(String v){providerTransactionId=v;touch();}
    public String getCheckoutTokenHash(){return checkoutTokenHash;} public void setCheckoutTokenHash(String v){checkoutTokenHash=v;}
    public PaymentAttemptStatus getStatus(){return status;} public void setStatus(PaymentAttemptStatus v){status=v;touch();}
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
    public String getLastError(){return lastError;} public void setLastError(String v){lastError=v;touch();}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} private void touch(){updatedAt=Instant.now();}
}
