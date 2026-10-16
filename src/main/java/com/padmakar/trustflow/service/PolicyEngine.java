package com.padmakar.trustflow.service;

import com.padmakar.trustflow.ai.Prediction;
import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.web.CaseForm;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PolicyEngine {

    public String reviewReason(
        CaseForm form,
        Policy policy,
        Prediction prediction,
        boolean aiFailed
    ) {
        List<String> reasons = new ArrayList<>();
        if (TextReviewGuard.requiresReview(form.getTitle(), form.getDescription())) reasons.add(
            "Text indicates a possible exception or unresolved evidence; human review required (" +
                TextReviewGuard.VERSION +
                ")"
        );
        if (aiFailed) reasons.add("AI unavailable: manual review required");
        if (policy.isAlwaysReview()) reasons.add("Case type requires human review");
        if (
            form.getType() == CaseType.EXPENSE &&
            form.getAmount().compareTo(policy.getAmountThreshold()) > 0
        ) reasons.add("Amount exceeds " + policy.getAmountThreshold());
        if (form.getType() == CaseType.ACCESS && "ADMIN".equals(form.getAccessLevel())) reasons.add(
            "Privileged access requires human review"
        );
        if (prediction.confidence() < policy.getMinConfidence()) reasons.add(
            "Score below configured threshold"
        );
        if (prediction.recommendation() != Recommendation.APPROVE) reasons.add(
            "Recommendation requires human review"
        );
        return String.join("; ", reasons);
    }
}
