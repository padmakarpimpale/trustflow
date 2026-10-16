package com.padmakar.trustflow.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "review_policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private CaseType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountThreshold;

    private double minConfidence;

    private boolean alwaysReview;

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public CaseType getType() {
        return type;
    }

    public void setType(CaseType type) {
        this.type = type;
    }

    public BigDecimal getAmountThreshold() {
        return amountThreshold;
    }

    public void setAmountThreshold(BigDecimal amountThreshold) {
        this.amountThreshold = amountThreshold;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public boolean isAlwaysReview() {
        return alwaysReview;
    }

    public void setAlwaysReview(boolean alwaysReview) {
        this.alwaysReview = alwaysReview;
    }
}
