package br.com.nuvemcustomfields.entity;

import java.time.Instant;

public class PlanEvent {

    private Long id;
    private Long storeId;
    private PlanType fromPlan;
    private PlanType toPlan;
    private String source;
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
