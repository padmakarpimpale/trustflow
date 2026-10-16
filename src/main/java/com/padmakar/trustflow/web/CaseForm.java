package com.padmakar.trustflow.web;

import com.padmakar.trustflow.model.CaseType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class CaseForm {

    @NotNull
    private CaseType type;

    @NotBlank
    @Size(max = 120)
    private String title;

    @NotBlank
    @Size(min = 10, max = 2000)
    private String description;

    @DecimalMin("0.01")
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;

    @Pattern(regexp = "^(STANDARD|ADMIN)?$")
    private String accessLevel;

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
}
