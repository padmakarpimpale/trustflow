package com.padmakar.trustflow.web;

import jakarta.validation.constraints.*;

public record ReviewForm(
    @NotNull Long version,
    @Pattern(regexp = "APPROVED|REJECTED") @NotBlank String decision,
    @NotBlank @Size(min = 5, max = 1000) String reason
) {}
