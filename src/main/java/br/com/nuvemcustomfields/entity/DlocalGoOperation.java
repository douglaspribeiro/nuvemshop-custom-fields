package br.com.nuvemcustomfields.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="dlocal_go_operations")
public class DlocalGoOperation {
    public enum Kind { UPGRADE, REFUND }
    public enum State { QUOTED, CREATING, PENDING, PAID, APPLYING, COMPLETED, FAILED, REVERSED, REVIEW }
    @Id @Column(length=36) private String id=UUID.randomUUID().toString();
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state=State.QUOTED;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Version private long version;
    public String getId(){return id;}
    public Kind getKind(){return kind;} public void setKind(Kind v){kind=v;}
    public State getState(){return state;} public void setState(State v){state=v;}
    public Instant getCreatedAt(){return createdAt;}
    public String reference(){return "dlocal-upgrade-"+id;}
    @Column(nullable=false) private Long storeId;
    public Long getStoreId(){return storeId;} public void setStoreId(Long v){storeId=v;}
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentEnvironment environment;
    public PaymentEnvironment getEnvironment(){return environment;} public void setEnvironment(PaymentEnvironment v){environment=v;}
    @Column(length=120) private String subscriptionId;
    public String getSubscriptionId(){return subscriptionId;} public void setSubscriptionId(String v){subscriptionId=v;}
    @Enumerated(EnumType.STRING) @Column(length=30) private PlanType sourcePlan;
    public PlanType getSourcePlan(){return sourcePlan;} public void setSourcePlan(PlanType v){sourcePlan=v;}
    @Enumerated(EnumType.STRING) @Column(length=30) private PlanType targetPlan;
    public PlanType getTargetPlan(){return targetPlan;} public void setTargetPlan(PlanType v){targetPlan=v;}
    @Column(precision=12,scale=2) private BigDecimal sourceAmount;
    public BigDecimal getSourceAmount(){return sourceAmount;} public void setSourceAmount(BigDecimal v){sourceAmount=v;}
    @Column(precision=12,scale=2) private BigDecimal targetAmount;
    public BigDecimal getTargetAmount(){return targetAmount;} public void setTargetAmount(BigDecimal v){targetAmount=v;}
    @Column(nullable=false,precision=12,scale=2) private BigDecimal dueAmount;
    public BigDecimal getDueAmount(){return dueAmount;} public void setDueAmount(BigDecimal v){dueAmount=v;}
    @Column(length=120) private String sourcePriceId;
    public String getSourcePriceId(){return sourcePriceId;} public void setSourcePriceId(String v){sourcePriceId=v;}
    @Column(length=120) private String targetPriceId;
    public String getTargetPriceId(){return targetPriceId;} public void setTargetPriceId(String v){targetPriceId=v;}
    @Column(length=120) private String sourcePaymentId;
    public String getSourcePaymentId(){return sourcePaymentId;} public void setSourcePaymentId(String v){sourcePaymentId=v;}
    @Column(length=120) private String invoiceId;
    public String getInvoiceId(){return invoiceId;} public void setInvoiceId(String v){invoiceId=v;}
    @Column(length=120) private String paymentId;
    public String getPaymentId(){return paymentId;} public void setPaymentId(String v){paymentId=v;}
    @Column(length=120) private String remoteId;
    public String getRemoteId(){return remoteId;} public void setRemoteId(String v){remoteId=v;}
    @Column(length=2048) private String checkoutUrl;
    public String getCheckoutUrl(){return checkoutUrl;} public void setCheckoutUrl(String v){checkoutUrl=v;}
     private Instant periodStart;
    public Instant getPeriodStart(){return periodStart;} public void setPeriodStart(Instant v){periodStart=v;}
     private Instant periodEnd;
    public Instant getPeriodEnd(){return periodEnd;} public void setPeriodEnd(Instant v){periodEnd=v;}
     private Instant expiresAt;
    public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
    @Column(length=500) private String message;
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
}
