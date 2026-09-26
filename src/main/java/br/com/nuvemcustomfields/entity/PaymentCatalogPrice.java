package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="payment_catalog_prices")
public class PaymentCatalogPrice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PaymentProviderType provider;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentEnvironment environment;
    @Column(name="country_code",nullable=false,length=2) private String countryCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PlanType plan;
    @Column(nullable=false,length=3) private String currency;
    @Column(name="amount_value",nullable=false,precision=12,scale=2) private BigDecimal amountValue;
    @Column(name="provider_price_id",length=120) private String providerPriceId;
    @Column(name="tax_mode",nullable=false,length=20) private String taxMode="internal";
    @Column(nullable=false) private boolean recurring=true;
    @Column(nullable=false) private boolean enabled=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
    @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    public Long getId(){return id;} public PaymentProviderType getProvider(){return provider;} public void setProvider(PaymentProviderType v){provider=v;}
    public PaymentEnvironment getEnvironment(){return environment;} public void setEnvironment(PaymentEnvironment v){environment=v;}
    public String getCountryCode(){return countryCode;} public void setCountryCode(String v){countryCode=v;}
    public PlanType getPlan(){return plan;} public void setPlan(PlanType v){plan=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;touch();}
    public BigDecimal getAmountValue(){return amountValue;} public void setAmountValue(BigDecimal v){amountValue=v;touch();}
    public String getProviderPriceId(){return providerPriceId;} public void setProviderPriceId(String v){providerPriceId=v;touch();}
    public String getTaxMode(){return taxMode;} public void setTaxMode(String v){taxMode=v;touch();}
    public boolean isRecurring(){return recurring;} public void setRecurring(boolean v){recurring=v;touch();}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;touch();}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} private void touch(){updatedAt=Instant.now();}
}
