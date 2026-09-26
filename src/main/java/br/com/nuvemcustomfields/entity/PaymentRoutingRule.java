package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_routing_rules")
public class PaymentRoutingRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "country_code", nullable = false, unique = true, length = 2) private String countryCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentProviderType provider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentEnvironment environment;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "updated_by", nullable = false, length = 120) private String updatedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    public Long getId() { return id; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { countryCode = v; }
    public PaymentProviderType getProvider() { return provider; }
    public void setProvider(PaymentProviderType v) { provider = v; touch(); }
    public PaymentEnvironment getEnvironment() { return environment; }
    public void setEnvironment(PaymentEnvironment v) { environment = v; touch(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; touch(); }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { updatedBy = v; touch(); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    private void touch() { updatedAt = Instant.now(); }
}
