package com.padmakar.trustflow.ai;

import com.padmakar.trustflow.model.Recommendation;

public record Prediction(
    Recommendation recommendation,
    double confidence,
    String explanation,
    String modelVersion
) {}
