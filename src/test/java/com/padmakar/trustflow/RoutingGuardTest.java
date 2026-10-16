package com.padmakar.trustflow;

import static org.junit.jupiter.api.Assertions.*;

import com.padmakar.trustflow.ai.Prediction;
import com.padmakar.trustflow.model.*;
import com.padmakar.trustflow.service.PolicyEngine;
import com.padmakar.trustflow.web.CaseForm;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoutingGuardTest {

    private final PolicyEngine engine = new PolicyEngine();

    String route(String title, String text, double score, String amount) {
        var form = new CaseForm();
        form.setTitle(title);
        form.setDescription(text);
        form.setType(CaseType.EXPENSE);
        form.setAmount(new BigDecimal(amount));
        var policy = new Policy();
        policy.setAmountThreshold(new BigDecimal("500"));
        policy.setMinConfidence(0.8);
        return engine.reviewReason(
            form,
            policy,
            new Prediction(Recommendation.APPROVE, score, "Model prediction", "test"),
            false
        );
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "Taxi fare not approved by my manager with no receipt attached",
            "Manager hasn't approved the client meeting expense",
            "Manager hasn’t approved the client meeting expense",
            "APPROVAL IS PENDING for the customer workshop",
            "Receipt missing for approved business travel",
            "Manager approval awaited without evidence",
            "Business trip includes personal expenses",
            "Duplicate invoice for a client meeting",
            "No problems with this approved business expense",
            "Client workshop with unverified approval",
            "NOT-approved business travel",
            "ＮＯ receipt for the business trip",
        }
    )
    void highScoreCannotBypassTextGuard(String text) {
        assertTrue(route("Expense request", text, 0.99, "20").contains("text-guard-v1"));
    }

    @Test
    void titleIsAlsoChecked() {
        assertTrue(
            route("Missing invoice", "Approved business travel", 0.99, "20").contains(
                "text-guard-v1"
            )
        );
    }

    @Test
    void clearApprovalRemainsEligibleAtExactThreshold() {
        assertEquals(
            "",
            route(
                "Client travel",
                "Taxi fare approved by my manager with receipt attached",
                0.8,
                "500"
            )
        );
    }

    @Test
    void lowScoreStillEscalates() {
        assertTrue(
            route("Client travel", "Taxi fare for a client meeting", 0.5182, "20").contains(
                "Score below"
            )
        );
    }

    @Test
    void highAmountStillEscalates() {
        assertTrue(
            route("Client travel", "Approved taxi fare", 0.99, "500.01").contains("Amount exceeds")
        );
    }
}
