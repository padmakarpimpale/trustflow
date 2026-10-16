package com.padmakar.trustflow.ai;

import com.padmakar.trustflow.model.Recommendation;
import com.padmakar.trustflow.web.CaseForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trustflow.ai.mode", havingValue = "demo", matchIfMissing = true)
public class DemoAiClient implements AiClient {

    public Prediction predict(CaseForm form) {
        String text = form.getDescription().toLowerCase(java.util.Locale.ROOT);
        // Deterministic fixtures for an offline demonstration, not a trained model.
        if (text.contains("personal")) return new Prediction(
            Recommendation.REJECT,
            0.91,
            "Demo rule: personal use needs a reviewer decision.",
            "demo-rules-v1"
        );
        if (text.contains("unclear")) return new Prediction(
            Recommendation.REVIEW,
            0.40,
            "Demo rule: unclear justification.",
            "demo-rules-v1"
        );
        return new Prediction(
            Recommendation.APPROVE,
            0.90,
            "Demo recommendation only; business rules are applied separately.",
            "demo-rules-v1"
        );
    }
}
