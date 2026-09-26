package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_routing_history")
public class PaymentRoutingHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="country_code", nullable=false, length=2) private String countryCode;
    @Enumerated(EnumType.STRING) @Column(name="old_provider", length=30) private PaymentProviderType oldProvider;
    @Enumerated(EnumType.STRING) @Column(name="new_provider", nullable=false, length=30) private PaymentProviderType newProvider;
    @Enumerated(EnumType.STRING) @Column(name="old_environment", length=20) private PaymentEnvironment oldEnvironment;
    @Enumerated(EnumType.STRING) @Column(name="new_environment", nullable=false, length=20) private PaymentEnvironment newEnvironment;
    @Column(name="old_enabled") private Boolean oldEnabled;
    @Column(name="new_enabled", nullable=false) private boolean newEnabled;
    @Column(name="operator_name", nullable=false, length=120) private String operatorName;
    @Column(name="changed_at", nullable=false, updatable=false) private Instant changedAt = Instant.now();
    public Long getId(){return id;} public String getCountryCode(){return countryCode;} public void setCountryCode(String v){countryCode=v;}
    public PaymentProviderType getOldProvider(){return oldProvider;} public void setOldProvider(PaymentProviderType v){oldProvider=v;}
    public PaymentProviderType getNewProvider(){return newProvider;} public void setNewProvider(PaymentProviderType v){newProvider=v;}
    public PaymentEnvironment getOldEnvironment(){return oldEnvironment;} public void setOldEnvironment(PaymentEnvironment v){oldEnvironment=v;}
    public PaymentEnvironment getNewEnvironment(){return newEnvironment;} public void setNewEnvironment(PaymentEnvironment v){newEnvironment=v;}
    public Boolean getOldEnabled(){return oldEnabled;} public void setOldEnabled(Boolean v){oldEnabled=v;}
    public boolean isNewEnabled(){return newEnabled;} public void setNewEnabled(boolean v){newEnabled=v;}
    public String getOperatorName(){return operatorName;} public void setOperatorName(String v){operatorName=v;}
    public Instant getChangedAt(){return changedAt;}
}
