package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Uma cobrança avulsa de ajuste; nunca representa uma segunda assinatura. */
@Entity
@Table(name = "upgrade_adjustments")
public class UpgradeAdjustment {
    public enum State { QUOTED, CREATING, PAYMENT_PENDING, PAID, CHANGE_PENDING, COMPLETED, FAILED, REVIEW }
    @Id private String id = UUID.randomUUID().toString();
    @Column(nullable=false) private Long storeId;
    @Column(nullable=false, length=120) private String subscriptionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentEnvironment environment;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PlanType sourcePlan;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private PlanType targetPlan;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal sourceAmount;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal regularAmount;
    @Column(nullable=false,length=120) private String targetPriceId;
    @Column(nullable=false,length=120) private String sourcePriceId;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal targetProrated;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal discountAmount;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal creditAmount;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal dueAmount;
    @Column(length=36) private String couponCode;
    private Long couponId;
    @Column(precision=5,scale=2) private BigDecimal couponPercent;
    @Column(nullable=false) private Instant periodStart;
    @Column(nullable=false) private Instant periodEnd;
    @Column(nullable=false) private Instant quotedAt;
    @Column(nullable=false) private Instant expiresAt;
    @Column(unique=true,length=120) private String chargeId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state = State.QUOTED;
    @Column(length=500) private String message;
    private Instant paidAt;
    private Instant completedAt;
    @Version private long version;
    public String getId(){return id;}
    public Long getStoreId(){return storeId;} public void setStoreId(Long v){storeId=v;}
    public String getSubscriptionId(){return subscriptionId;} public void setSubscriptionId(String v){subscriptionId=v;}
    public PaymentEnvironment getEnvironment(){return environment;} public void setEnvironment(PaymentEnvironment v){environment=v;}
    public PlanType getSourcePlan(){return sourcePlan;} public void setSourcePlan(PlanType v){sourcePlan=v;}
    public PlanType getTargetPlan(){return targetPlan;} public void setTargetPlan(PlanType v){targetPlan=v;}
    public BigDecimal getSourceAmount(){return sourceAmount;} public void setSourceAmount(BigDecimal v){sourceAmount=v;}
    public BigDecimal getRegularAmount(){return regularAmount;} public void setRegularAmount(BigDecimal v){regularAmount=v;}
    public String getTargetPriceId(){return targetPriceId;} public void setTargetPriceId(String v){targetPriceId=v;}
    public String getSourcePriceId(){return sourcePriceId;} public void setSourcePriceId(String v){sourcePriceId=v;}
    public BigDecimal getTargetProrated(){return targetProrated;} public void setTargetProrated(BigDecimal v){targetProrated=v;}
    public BigDecimal getDiscountAmount(){return discountAmount;} public void setDiscountAmount(BigDecimal v){discountAmount=v;}
    public BigDecimal getCreditAmount(){return creditAmount;} public void setCreditAmount(BigDecimal v){creditAmount=v;}
    public BigDecimal getDueAmount(){return dueAmount;} public void setDueAmount(BigDecimal v){dueAmount=v;}
    public String getCouponCode(){return couponCode;} public void setCouponCode(String v){couponCode=v;}
    public Long getCouponId(){return couponId;} public void setCouponId(Long v){couponId=v;}
    public BigDecimal getCouponPercent(){return couponPercent;} public void setCouponPercent(BigDecimal v){couponPercent=v;}
    public Instant getPeriodStart(){return periodStart;} public void setPeriodStart(Instant v){periodStart=v;}
    public Instant getPeriodEnd(){return periodEnd;} public void setPeriodEnd(Instant v){periodEnd=v;}
    public Instant getQuotedAt(){return quotedAt;} public void setQuotedAt(Instant v){quotedAt=v;}
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
    public String getChargeId(){return chargeId;} public void setChargeId(String v){chargeId=v;}
    public State getState(){return state;} public void setState(State v){state=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public Instant getPaidAt(){return paidAt;} public void setPaidAt(Instant v){paidAt=v;}
    public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
    public String reference(){return "upgrade-"+id;}
    public boolean isPending(){return state!=State.QUOTED && state!=State.COMPLETED && state!=State.FAILED;}
}
