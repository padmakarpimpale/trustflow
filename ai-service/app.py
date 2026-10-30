"""Inference from a persisted trained model. No decision-by-keyword substitute."""

from pathlib import Path
from typing import Literal
import joblib
import numpy as np
import warnings
from sklearn.exceptions import InconsistentVersionWarning
from fastapi import FastAPI, Request
import logging
import re
import time
import uuid
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parent
MODEL_PATH = ROOT / "artifacts/model.joblib"
if not MODEL_PATH.exists():
    raise RuntimeError("Run python train.py before starting the inference service.")
# Only load the local artifact produced by our training script, never user uploads.
with warnings.catch_warnings():
    warnings.simplefilter("error", InconsistentVersionWarning)
    try:
        bundle = joblib.load(MODEL_PATH)
    except InconsistentVersionWarning as error:
        raise RuntimeError(
            "Model dependency mismatch. Run python train.py in this environment, then restart."
        ) from error
model, VERSION = bundle["pipeline"], bundle["version"]
vectorizer = model.named_steps["vectorizer"]
classifier = model.named_steps["classifier"]
app = FastAPI(title="TrustFlow ML recommendation service", version="2.0.0")


@app.middleware("http")
async def request_id(request: Request, call_next):
    identifier = request.headers.get("X-Request-ID", "")
    if not re.fullmatch(r"[A-Za-z0-9-]{1,64}", identifier):
        identifier = str(uuid.uuid4())
    start = time.monotonic()
    response = await call_next(request)
    response.headers["X-Request-ID"] = identifier
    logging.getLogger("uvicorn.error").info(
        "requestId=%s status=%s durationMs=%.1f", identifier,
        response.status_code, (time.monotonic() - start) * 1000)
    return response


class CaseInput(BaseModel):
    type: Literal["EXPENSE", "ACCESS"]
    title: str = Field(min_length=1, max_length=120)
    description: str = Field(min_length=10, max_length=2000)
    amount: float | None = Field(default=None, ge=0, allow_inf_nan=False)
    accessLevel: Literal["STANDARD", "ADMIN", ""] | None = None


@app.get("/health")
def health():
    return {"status": "ok", "model": VERSION, "mode": "trained-model"}


@app.post("/predict")
def predict(case: CaseInput):
    text = case.title + " " + case.description
    vector = vectorizer.transform([text])
    tokens = vectorizer.build_tokenizer()(text.lower())
    coverage = sum(token in vectorizer.vocabulary_ for token in tokens) / max(
        len(tokens), 1
    )
    if vector.nnz == 0 or coverage < 0.25:
        return {
            "recommendation": "REVIEW",
            "confidence": 0.0,
            "explanation": "Insufficient known vocabulary for reliable classification. A reviewer must assess the request.",
            "modelVersion": VERSION,
        }
    probabilities = classifier.predict_proba(vector)[0]
    index = int(np.argmax(probabilities))
    # Additive positive TF-IDF contributions to the selected class's linear score.
    contributions = vector.toarray()[0] * classifier.coef_[index]
    names = vectorizer.get_feature_names_out()
    influential = [
        str(names[i]) for i in np.argsort(contributions)[::-1] if contributions[i] > 0
    ][:4]
    return {
        "recommendation": str(classifier.classes_[index]),
        "confidence": round(float(probabilities[index]), 4),
        "explanation": "Terms supporting this classification: "
        + ", ".join(influential)
        + ". Learned from synthetic examples; score is uncalibrated. Amount and access rules are checked separately.",
        "modelVersion": VERSION,
    }
