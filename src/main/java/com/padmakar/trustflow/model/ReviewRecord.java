package com.padmakar.trustflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_record")
public class ReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "case_id", unique = true)
    private CaseRecord caseRecord;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reviewer_id")
    private UserAccount reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus decision;

    @Column(nullable = false, length = 1000)
    private String reason;

    private boolean overridden;

    @Column(nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public CaseRecord getCaseRecord() {
        return caseRecord;
    }

    public void setCaseRecord(CaseRecord caseRecord) {
        this.caseRecord = caseRecord;
    }

    public UserAccount getReviewer() {
        return reviewer;
    }

    public void setReviewer(UserAccount reviewer) {
        this.reviewer = reviewer;
    }

    public CaseStatus getDecision() {
        return decision;
    }

    public void setDecision(CaseStatus decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isOverridden() {
        return overridden;
    }

    public void setOverridden(boolean overridden) {
        this.overridden = overridden;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
