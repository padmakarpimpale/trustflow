package com.padmakar.trustflow.web;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PolicyForm(
    @NotNull Long version,
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    BigDecimal amountThreshold,
    @DecimalMin("0.0") @DecimalMax("1.0") double minConfidence,
    boolean alwaysReview
) {}
