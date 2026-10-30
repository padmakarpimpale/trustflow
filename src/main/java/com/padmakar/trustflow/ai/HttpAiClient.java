package com.padmakar.trustflow.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padmakar.trustflow.web.CaseForm;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trustflow.ai.mode", havingValue = "http")
public class HttpAiClient implements AiClient {

    private final ObjectMapper mapper;
    private final String url;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    public HttpAiClient(ObjectMapper mapper, @Value("${trustflow.ai.url}") String url) {
        this.mapper = mapper;
        this.url = url;
    }

    public Prediction predict(CaseForm form) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .header(
                    "X-Request-ID",
                    java.util.Objects.toString(
                        org.slf4j.MDC.get("requestId"),
                        java.util.UUID.randomUUID().toString()
                    )
                )
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(form)))
                .build();
            HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() != 200) throw new IllegalStateException("AI service error");
            Prediction prediction = mapper.readValue(response.body(), Prediction.class);
            if (
                prediction.recommendation() == null ||
                !Double.isFinite(prediction.confidence()) ||
                prediction.confidence() < 0 ||
                prediction.confidence() > 1 ||
                prediction.explanation() == null ||
                prediction.explanation().length() > 2000 ||
                prediction.modelVersion() == null ||
                prediction.modelVersion().length() > 100
            ) throw new IllegalStateException("Invalid AI response");
            return prediction;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("AI service unavailable", e);
        }
    }
}
