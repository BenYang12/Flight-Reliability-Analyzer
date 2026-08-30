"""
Right now, a cluster is an integer. To name it honestly, I need... 
1. A profile of each cluster -> what does avg flight in cluster actually look like? That's what lets me say "cluster 0 is congestion"
2. Corpus percentiles - the 25th and 75th percentile of every feature across all 200k flights

Corpus Percentiles:
- tells me where a value sits compared with the entire dataset
- Here, "corpus" is roughly 200,000 flights in my training dataset
- ex. if taxi-out p75 were 24 minutes, flight with 30 minute taxi-out would be unusually high. 


I need to use Corpus Percentiles to interpret numbered clusters in understandable terms. 
They translate raw measurements and arbitrary cluster numbers into evidence-based, traveler-friendly descriptions relative to what is normal in my flight dataset
- unusually long taxi-out
- low arrival delay
- high weather delay
- typical recovery time

These percentiles give words such as low, typical, and high an objective meaning:
1. <= p25 -> relatively low
2.  around p50 -> typical
3.  >= p75 -> relatively high

clustered_data.csv -> per-cluster profiles + corpus percentiles -> thresholds.json

Phase 3 handed back six numbered clusters. A number is not an answer: "your flight
is a cluster 4" means nothing to a traveler.

Phase 4 fixes that in two steps:

  1. MEASURE  - profile every cluster and compute the corpus
     percentiles that every later rule threshold is derived from.
  2. NAME     (next checkpoint) - a deterministic rule turns a profile into an
     archetype name, and the whole thing is written to thresholds.json.

The one hardcoded number in this phase is 15 minutes: the FAA's definition of
an on-time arrival.

Usage:
    python interpret_data.py
"""

import os

import pandas as pd


from normalize_data import CAUSE_COLUMNS, FEATURES

# Config
HERE = os.path.dirname(__file__)
CLUSTERED_CSV = os.path.join(HERE, "clustered_data.csv")

# The percentiles every rule threshold is derived from. p25/p75 mark "low for this corpus" and "high for this corpus"
PERCENTILES = [0.25, 0.50, 0.75]

# BTS only requires a carrier to attribute a delay cause when arrival delay is 15+ minutes
# null for most rows, which is genuinely 0. 
FILL_ZERO = CAUSE_COLUMNS

# The four features don't describe how a flight performed 
# They still belong in the profile table, but a percentile of `month` is not a useful rule.
CONTEXT_FEATURES = ["distance", "dep_hour", "day_of_week", "month"]

# Everything else: the performance features a rule can meaningfully threshold on.
PERFORMANCE_FEATURES = [f for f in FEATURES if f not in CONTEXT_FEATURES]


def load_clustered():
    """Read clustered_data.csv and apply the same null handling training used."""
    df = pd.read_csv(CLUSTERED_CSV)
    df[FILL_ZERO] = df[FILL_ZERO].fillna(0)
    return df


def corpus_percentiles(df):
    """p25/p50/p75 of every performance feature, across all flights.

    This is the DEFAULT threshold set: what "high" and "low" mean when we have no
    better reference. Returned as {feature: {"p25": ..., "p50": ..., "p75": ...}}.
    """
    # .quantile() on a DataFrame returns a frame indexed by percentile, one column
    # per feature. Transposing puts features on the rows, which is the shape the
    # dict comprehension below wants.
    quantiles = df[PERFORMANCE_FEATURES].quantile(PERCENTILES).T

    return {
        feature: {
            "p25": round(row[0.25], 3),
            "p50": round(row[0.50], 3),
            "p75": round(row[0.75], 3),
        }
        # .iterrows() yields (row label, row contents) pairs - here (feature name,
        # that feature's three percentiles).
        for feature, row in quantiles.iterrows()
    }


def cluster_profiles(df):
    """One profile per cluster: how big it is, what it averages, and why it is late.

    The `dominant_cause` field is the whole point of the exercise. BTS makes every
    carrier attribute delay minutes to one of four causes, so a cluster's largest
    average cause is ground truth about what that group of flights has in common -
    not my interpretation of a scatter plot.
    """
    profiles = {}

    # groupby("cluster") splits the frame into six sub-frames, one per cluster id.
    for cluster_id, rows in df.groupby("cluster"):
        means = rows[FEATURES].mean()

        # Average minutes attributed to each of the four BTS causes, largest first.
        cause_means = {cause: round(means[cause], 2) for cause in CAUSE_COLUMNS}
        dominant_cause = max(cause_means, key=cause_means.get)

        profiles[int(cluster_id)] = {
            "n": len(rows),
            "share": round(len(rows) / len(df), 4),
            "means": {feature: round(means[feature], 2) for feature in FEATURES},
            "cause_means": cause_means,
            "dominant_cause": dominant_cause,

            # The share of this cluster's flights that arrived within 15 minutes -
            # the FAA on-time definition. A cluster's single most legible statistic.
            "on_time_rate": round((rows.arr_delay_min <= 15).mean(), 4),

            # Percentiles computed *within* the cluster. Next checkpoint decides
            # whether a cluster has enough rows for these to be trustworthy; two of
            # the six do not, and fall back to the corpus numbers above.
            "percentiles": {
                feature: {
                    "p25": round(rows[feature].quantile(0.25), 3),
                    "p50": round(rows[feature].quantile(0.50), 3),
                    "p75": round(rows[feature].quantile(0.75), 3),
                }
                for feature in PERFORMANCE_FEATURES
            },
        }

    return profiles


def print_profiles(profiles, corpus):
    """The table I have to be able to defend out loud, printed for a human."""
    print(f"\n{'=' * 78}")
    print("  CORPUS PERCENTILES  (the definition of 'high' and 'low' for every rule)")
    print(f"{'=' * 78}")
    print(f"  {'feature':<22} {'p25':>10} {'p50':>10} {'p75':>10}")
    for feature, p in corpus.items():
        print(f"  {feature:<22} {p['p25']:>10.2f} {p['p50']:>10.2f} {p['p75']:>10.2f}")

    print(f"\n{'=' * 78}")
    print("  CLUSTER PROFILES  (sorted largest first)")
    print(f"{'=' * 78}")

    # Largest cluster first: the two big ones are most of the corpus, and reading
    # them first makes the long tail read as a tail rather than as six equal groups.
    ordered = sorted(profiles.items(), key=lambda kv: -kv[1]["n"])

    for cluster_id, profile in ordered:
        means = profile["means"]
        print(
            f"\n  cluster {cluster_id}   "
            f"{profile['n']:>7,} flights ({profile['share']:.1%})   "
            f"on-time {profile['on_time_rate']:.1%}"
        )
        print(
            f"    dep_delay {means['dep_delay_min']:>8.1f}   "
            f"arr_delay {means['arr_delay_min']:>8.1f}   "
            f"recovery {means['recovery']:>7.1f}"
        )
        print(
            f"    taxi_out  {means['taxi_out']:>8.1f}   "
            f"dep_hour  {means['dep_hour']:>8.1f}   "
            f"distance {means['distance']:>7.0f}"
        )
        causes = "  ".join(
            f"{cause.replace('_delay', ''):>13} {value:>7.1f}"
            for cause, value in profile["cause_means"].items()
        )
        print(f"    causes:{causes}")
        print(f"    dominant cause -> {profile['dominant_cause']}")

    print(f"\n{'=' * 78}")


def main():
    df = load_clustered()
    corpus = corpus_percentiles(df)
    profiles = cluster_profiles(df)
    print_profiles(profiles, corpus)


if __name__ == "__main__":
    main()
