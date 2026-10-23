package br.com.nuvemcustomfields.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
@Table(name="dlocal_go_reversals")
public class DlocalGoReversal {
    @Id @Column(length=180) private String id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentEnvironment environment;
    @Column(nullable=false,length=120) private String orderId;
    @Column(nullable=false,length=20) private String kind;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal amount;
    public String getId(){return id;} public void setId(String v){id=v;}
    public PaymentEnvironment getEnvironment(){return environment;} public void setEnvironment(PaymentEnvironment v){environment=v;}
    public String getOrderId(){return orderId;} public void setOrderId(String v){orderId=v;}
    public String getKind(){return kind;} public void setKind(String v){kind=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
    public BigDecimal getAmount(){return amount;} public void setAmount(BigDecimal v){amount=v;}
}
