package br.com.nuvemcustomfields.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "plan_events")
public class PlanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_plan")
    private PlanType fromPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_plan", nullable = false)
    private PlanType toPlan;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getStoreId() {
        return storeId;
    }

    public void setStoreId(Long storeId) {
        this.storeId = storeId;
    }

    public PlanType getFromPlan() {
        return fromPlan;
    }

    public void setFromPlan(PlanType fromPlan) {
        this.fromPlan = fromPlan;
    }

    public PlanType getToPlan() {
        return toPlan;
    }

    public void setToPlan(PlanType toPlan) {
        this.toPlan = toPlan;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
