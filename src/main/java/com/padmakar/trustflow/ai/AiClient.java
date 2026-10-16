package com.padmakar.trustflow.ai;

import com.padmakar.trustflow.web.CaseForm;

public interface AiClient {
    Prediction predict(CaseForm form);
}
