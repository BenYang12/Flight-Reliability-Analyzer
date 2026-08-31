"""
One flight in -> its archetype out. The round-trip check on Phase 4's artifacts.


My phase 4 will write three files into flight-analyzer/: scaler.pkl, kmeans_model.pkl, and thresholds.json
The Flask service is about to depend on all three.

The test is a round-trip: take real rows out of clustered_data.csv, push them back
through the SAVED scaler and model, and check the predicted cluster matches the
label the pipeline already wrote on that row. 

This verifies feature ORDER. StandardScaler and KMeans see a bare numeric matrix with no column names. Hand them taxi_in where
taxi_out belongs and nothing raises; the numbers are all plausible minutes. 

Usage:
    python analyze_flight.py
"""

import json
import os
import joblib
import pandas as pd

from normalize_data import CAUSE_COLUMNS, FEATURES

HERE = os.path.dirname(__file__)
CLUSTERED_CSV = os.path.join(HERE, "clustered_data.csv")
ANALYZER_DIR = os.path.join(HERE, os.pardir, "flight-analyzer")

# How many rows to round-trip. Enough to catch a systematic error, small enough to
# run in a second.
SAMPLE_SIZE = 500
RANDOM_SEED = 42


def load_artifacts():
    """The three files Flask will load, loaded the same way Flask will load them."""
    scaler = joblib.load(os.path.join(ANALYZER_DIR, "scaler.pkl"))
    model = joblib.load(os.path.join(ANALYZER_DIR, "kmeans_model.pkl"))

    with open(os.path.join(ANALYZER_DIR, "thresholds.json")) as f:
        thresholds = json.load(f)

    return scaler, model, thresholds


def classify(flights, scaler, model, thresholds):
    """Assign each flight an archetype. `flights` is a DataFrame of raw features.

    Returns a list of (cluster_id, archetype_name) pairs.
    """
    features = flights.reindex(columns=FEATURES)

    # Same null handling as training: an absent cause means zero minutes from it.
    features[CAUSE_COLUMNS] = features[CAUSE_COLUMNS].fillna(0)
    clusters = model.predict(scaler.transform(features))

    return [(int(c), thresholds["clusters"][str(c)]["name"]) for c in clusters]


def main():
    scaler, model, thresholds = load_artifacts()

    sample = pd.read_csv(CLUSTERED_CSV).sample(
        n=SAMPLE_SIZE, random_state=RANDOM_SEED
    )
    predictions = classify(sample, scaler, model, thresholds)

    expected = sample.cluster.tolist()
    matches = sum(p[0] == e for p, e in zip(predictions, expected))

    print(f"Round-tripped {SAMPLE_SIZE} flights through the saved artifacts.")
    print(f"  cluster agreement: {matches}/{SAMPLE_SIZE} ({matches / SAMPLE_SIZE:.1%})")

    print("\nFirst five, as the service will see them:")
    for (_, name), (_, row) in zip(predictions[:5], sample.head().iterrows()):
        print(
            f"  {row.flight_number:<7} {row.origin}->{row.dest}   "
            f"dep {row.dep_delay_min:>6.0f}  arr {row.arr_delay_min:>6.0f}   {name}"
        )

    # A non-zero exit code makes this usable as a real check, not just a printout.
    if matches != SAMPLE_SIZE:
        raise SystemExit("\nArtifacts do NOT reproduce the training assignment.")


if __name__ == "__main__":
    main()
