package br.com.nuvemcustomfields.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_journey_audit", indexes = {
        @Index(name = "idx_journey_store_date", columnList = "store_id,occurred_at,id"),
        @Index(name = "idx_journey_date", columnList = "occurred_at")})
public class UserJourneyAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "store_id", nullable = false) public Long storeId;
    @Column(name = "occurred_at", nullable = false, updatable = false) public Instant occurredAt = Instant.now();
    @Column(name = "completed_at") public Instant completedAt;
    @Column(name = "journey_id", nullable = false, length = 36) public String journeyId;
    @Column(nullable = false, length = 24) public String actor;
    @Column(nullable = false, length = 160) public String action;
    @Column(nullable = false, length = 8) public String method;
    @Column(nullable = false, length = 200) public String route;
    @Column(name = "product_id") public Long productId;
    @Column(name = "selected_plan", length = 40) public String selectedPlan;
    @Column(name = "field_id") public Long fieldId;
    @Column(nullable = false, length = 24) public String outcome = "STARTED";
    @Column(name = "http_status") public Integer httpStatus;
    @Column(name = "before_state", columnDefinition = "LONGTEXT") public String beforeState;
    @Column(name = "after_state", columnDefinition = "LONGTEXT") public String afterState;
}
