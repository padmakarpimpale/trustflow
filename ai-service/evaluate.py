"""Report a fixed synthetic challenge set without training on it or tuning thresholds.

This measures the classifier and a hypothetical score-only gate, not Java policy routing.
"""
import csv
import json
from pathlib import Path
import numpy as np
from sklearn.metrics import accuracy_score, log_loss
from app import model, predict, CaseInput, VERSION

ROOT = Path(__file__).resolve().parent

def evaluate():
    with (ROOT / "data/challenge.csv").open() as source:
        rows = list(csv.DictReader(source))
    texts = [row["text"] for row in rows]
    labels = [row["label"] for row in rows]
    probabilities = model.predict_proba(texts)
    predicted = model.predict(texts)
    classes = list(model.classes_)
    target = np.array([[int(label == value) for value in classes] for label in labels])
    decisions = []
    for row in rows:
        output = predict(CaseInput(type="EXPENSE", title="Expense", description=row["text"], amount=20))
        decisions.append({**row, **output})
    eligible = [r for r in decisions if r["recommendation"] == "APPROVE" and r["confidence"] >= .8]
    report = {
        "modelVersion": VERSION, "examples": len(rows),
        "limitations": "Author-designed synthetic challenge set; not real-world or independent external validation. Raw metrics use text alone; service outputs include the fixed title Expense. No retraining or threshold tuning performed.",
        "rawClassifierAccuracy": accuracy_score(labels, predicted),
        "rawClassifierLogLoss": log_loss(labels, probabilities, labels=classes),
        "rawClassifierMulticlassBrier": float(np.mean(np.sum((probabilities-target)**2, axis=1))),
        "scoreOnlyGateCandidates": len(eligible),
        "scoreOnlyGateUnsafeCandidates": sum(r["label"] != "APPROVE" for r in eligible),
        "scoreOnlyGateReviewRate": 1 - len(eligible)/len(rows),
        "scope": "Score-only gate excludes Java amount/access/text rules. See integrated tests for actual workflow routing.",
        "cases": decisions,
    }
    (ROOT / "artifacts/challenge-evaluation.json").write_text(json.dumps(report, indent=2))
    print(json.dumps({k:v for k,v in report.items() if k != "cases"}, indent=2))

if __name__ == "__main__": evaluate()
