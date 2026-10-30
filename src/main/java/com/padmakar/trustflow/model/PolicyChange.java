package com.padmakar.trustflow.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only application record; database administrators can still alter database data. */
@Entity
@Table(name = "policy_change")
public class PolicyChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long policyId;

    @Column(nullable = false, updatable = false, length = 80)
    private String actor;

    @Column(nullable = false, updatable = false, length = 1000)
    private String beforeValues;

    @Column(nullable = false, updatable = false, length = 1000)
    private String afterValues;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PolicyChange() {}

    public PolicyChange(Long policyId, String actor, String beforeValues, String afterValues) {
        this.policyId = policyId;
        this.actor = actor;
        this.beforeValues = beforeValues;
        this.afterValues = afterValues;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public String getActor() {
        return actor;
    }

    public String getBeforeValues() {
        return beforeValues;
    }

    public String getAfterValues() {
        return afterValues;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
