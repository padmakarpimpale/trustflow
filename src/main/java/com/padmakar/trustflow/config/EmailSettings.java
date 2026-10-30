package com.padmakar.trustflow.config;

import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "trustflow.email")
public class EmailSettings {

    @NotBlank
    @Email
    private String from;

    @Pattern(regexp = "https?://[^\\s]+")
    private String baseUrl = "http://localhost:8080";

    @Min(1)
    @Max(10)
    private int maxAttempts = 5;

    @Min(1)
    @Max(3600)
    private long retrySeconds = 30;

    private Map<String, @Email String> recipients = new HashMap<>();

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetrySeconds() {
        return retrySeconds;
    }

    public void setRetrySeconds(long retrySeconds) {
        this.retrySeconds = retrySeconds;
    }

    public Map<String, String> getRecipients() {
        return recipients;
    }

    public void setRecipients(Map<String, String> recipients) {
        this.recipients = recipients;
    }
}
