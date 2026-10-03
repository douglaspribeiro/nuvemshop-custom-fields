package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="upgrade_coupons")
public class UpgradeCoupon {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=36) private String code;
    @Column(nullable=false, precision=5, scale=2) private BigDecimal discountPercent;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentEnvironment environment;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PlanType targetPlan;
    @Column(nullable=false) private boolean enabled;
    private Instant startsAt;
    private Instant endsAt;
    private Integer maxUses;
    @Column(nullable=false) private int maxUsesPerStore=1;
    @Column(nullable=false) private int usedCount;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant updatedAt=Instant.now();
    public Long getId(){return id;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public BigDecimal getDiscountPercent(){return discountPercent;} public void setDiscountPercent(BigDecimal v){discountPercent=v;}
    public PaymentEnvironment getEnvironment(){return environment;} public void setEnvironment(PaymentEnvironment v){environment=v;}
    public PlanType getTargetPlan(){return targetPlan;} public void setTargetPlan(PlanType v){targetPlan=v;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
    public Instant getStartsAt(){return startsAt;} public void setStartsAt(Instant v){startsAt=v;}
    public Instant getEndsAt(){return endsAt;} public void setEndsAt(Instant v){endsAt=v;}
    public Integer getMaxUses(){return maxUses;} public void setMaxUses(Integer v){maxUses=v;}
    public int getMaxUsesPerStore(){return maxUsesPerStore;} public void setMaxUsesPerStore(int v){maxUsesPerStore=v;}
    public int getUsedCount(){return usedCount;} public void setUsedCount(int v){usedCount=v;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
