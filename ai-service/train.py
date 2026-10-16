"""Train only on train.csv; report unchanged holdout results from test.csv."""

import csv
import hashlib
import json
from pathlib import Path
import joblib
import sklearn
from sklearn.pipeline import Pipeline
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix

ROOT = Path(__file__).resolve().parent


def rows(name):
    with (ROOT / "data" / name).open() as file:
        return list(csv.DictReader(file))


def train():
    training, test = rows("train.csv"), rows("test.csv")
    texts = [r["text"] for r in training]
    labels = [r["label"] for r in training]
    assert not set(texts) & {r["text"] for r in test}, "Train/test overlap"
    model = Pipeline(
        [
            ("vectorizer", TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True)),
            ("classifier", LogisticRegression(C=8.0, max_iter=2000, random_state=42)),
        ]
    )
    model.fit(texts, labels)
    # Hyperparameters are fixed before examining holdout results.
    predictions = model.predict([r["text"] for r in test])
    actual = [r["label"] for r in test]
    digest = hashlib.sha256((ROOT / "data/train.csv").read_bytes()).hexdigest()
    version = "tfidf-lr-v2-" + digest[:10]
    report = {
        "modelVersion": version,
        "trainingRows": len(training),
        "testRows": len(test),
        "dataset": "Manually authored synthetic coursework scenarios, not real claims",
        "limitations": "Small related-vocabulary holdout; not independent real-world validation. Scores are uncalibrated. No automatic learning from reviewer decisions.",
        "sklearnVersion": sklearn.__version__,
        "trainingSha256": digest,
        "accuracy": accuracy_score(actual, predictions),
        "classificationReport": classification_report(
            actual, predictions, output_dict=True, zero_division=0
        ),
        "labels": list(model.classes_),
        "confusionMatrix": confusion_matrix(
            actual, predictions, labels=model.classes_
        ).tolist(),
        "predictions": [
            {"text": r["text"], "actual": r["label"], "predicted": str(pred)}
            for r, pred in zip(test, predictions)
        ],
    }
    (ROOT / "artifacts").mkdir(exist_ok=True)
    joblib.dump(
        {"pipeline": model, "version": version}, ROOT / "artifacts/model.joblib"
    )
    (ROOT / "artifacts/evaluation.json").write_text(json.dumps(report, indent=2))
    print(
        json.dumps(
            {
                k: report[k]
                for k in ["modelVersion", "trainingRows", "testRows", "accuracy"]
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    train()
