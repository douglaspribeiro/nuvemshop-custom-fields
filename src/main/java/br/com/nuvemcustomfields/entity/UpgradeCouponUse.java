package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="upgrade_coupon_uses")
public class UpgradeCouponUse {
    public enum Status { RESERVED, USED, RELEASED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long couponId;
    @Column(nullable=false) private Long storeId;
    @Column(nullable=false,unique=true,length=36) private String adjustmentId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status=Status.RESERVED;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    public Long getId(){return id;}
    public Long getCouponId(){return couponId;} public void setCouponId(Long v){couponId=v;}
    public Long getStoreId(){return storeId;} public void setStoreId(Long v){storeId=v;}
    public String getAdjustmentId(){return adjustmentId;} public void setAdjustmentId(String v){adjustmentId=v;}
    public Status getStatus(){return status;} public void setStatus(Status v){status=v;}
    public Instant getCreatedAt(){return createdAt;}
}
