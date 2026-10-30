package com.padmakar.trustflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "case_record")
public class CaseRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id")
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 30)
    private String accessLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recommendation recommendation;

    private Double confidence;

    @Column(length = 2000)
    private String explanation;

    @Column(length = 100)
    private String modelVersion;

    @Column(length = 1000)
    private String escalationReason;

    private Long policyVersion;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant resolvedAt;

    @Column(precision = 12, scale = 2)
    private BigDecimal policyAmountThreshold;

    private Double policyMinConfidence;
    private Boolean policyAlwaysReview;

    @Column(length = 80)
    private String routingVersion;

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public CaseType getType() {
        return type;
    }

    public void setType(CaseType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }

    public Long getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(Long policyVersion) {
        this.policyVersion = policyVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public BigDecimal getPolicyAmountThreshold() {
        return policyAmountThreshold;
    }

    public void setPolicyAmountThreshold(BigDecimal value) {
        this.policyAmountThreshold = value;
    }

    public Double getPolicyMinConfidence() {
        return policyMinConfidence;
    }

    public void setPolicyMinConfidence(Double value) {
        this.policyMinConfidence = value;
    }

    public Boolean getPolicyAlwaysReview() {
        return policyAlwaysReview;
    }

    public void setPolicyAlwaysReview(Boolean value) {
        this.policyAlwaysReview = value;
    }

    public String getRoutingVersion() {
        return routingVersion;
    }

    public void setRoutingVersion(String value) {
        this.routingVersion = value;
    }
}
