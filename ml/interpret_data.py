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

Phase 4 fixes that in two steps by reading clustered_data.csv:

  1. MEASURE  - profile every cluster and compute the corpus
     percentiles that every later rule threshold is derived from.
  2. NAME     (next checkpoint) - a deterministic rule turns a profile into an
     archetype name, and the whole thing is written to thresholds.json.

The one hardcoded number in this phase is 15 minutes: the FAA's definition of
an on-time arrival.

Usage:
    python interpret_data.py
"""

import json
import os
import pandas as pd


from normalize_data import CAUSE_COLUMNS, FEATURES

# read clustered_data.csv
HERE = os.path.dirname(__file__)
CLUSTERED_CSV = os.path.join(HERE, "clustered_data.csv")

ANALYZER_DIR = os.path.join(HERE, os.pardir, "flight-analyzer")
THRESHOLDS_JSON = os.path.join(ANALYZER_DIR, "thresholds.json")

# Hardcoded FAA definition of an on-time arrival
ON_TIME_MINUTES = 15
MIN_SAMPLE_SHARE = 0.01

# Archetype names
ARCHETYPES = {
    "clean_morning": "Clean Morning Operation",
    "evening_recovery": "Evening Recovery",
    "nas_delay": "Congestion-Bound",
    "late_aircraft_delay": "Late-Aircraft Cascade",
    "carrier_delay": "Carrier Meltdown",
    "weather_delay": "Weather-Stranded",
}

# percentiles every rule threshold is derived from -> p25/p75 mark "low for this corpus" and "high for this corpus"
PERCENTILES = [0.25, 0.50, 0.75]

# BTS only requires a carrier to attribute a delay cause when arrival delay is 15+ minutes
# null for most rows, which is genuinely 0. 
FILL_ZERO = CAUSE_COLUMNS

# The four features don't describe how a flight performed 
# They still belong in the profile table, but a percentile of `month` is not a useful rule.
CONTEXT_FEATURES = ["distance", "dep_hour", "day_of_week", "month"]

# Everything else -> the performance features a rule can meaningfully threshold on.
PERFORMANCE_FEATURES = [f for f in FEATURES if f not in CONTEXT_FEATURES]


#l load clustered_data.csv with 0s filled in for clean flights

def load_clustered():
    """Read clustered_data.csv and apply the same null handling training used."""
    df = pd.read_csv(CLUSTERED_CSV)
    df[FILL_ZERO] = df[FILL_ZERO].fillna(0)
    return df


# p25/p50/p75 of every performance feature across all 200k flights
# Returned as {feature: {"p25": ..., "p50": ..., "p75": ...}}.
def corpus_percentiles(df):
    # .quantile(PERCENTILES) calculates all three percentiles for every selected column
    quantiles = df[PERFORMANCE_FEATURES].quantile(PERCENTILES).T #transpose result so each feature becomes a row

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

# delay-cause percentiles are calculated only among flights that actually experience that cause
def cause_percentiles(df):
    return {
        cause: {
            "p25": round(nonzero.quantile(0.25), 3),
            "p50": round(nonzero.quantile(0.50), 3),
            "p75": round(nonzero.quantile(0.75), 3),
        }
        for cause in CAUSE_COLUMNS
        for nonzero in [df.loc[df[cause] > 0, cause]]
    }


# two pivots 
# both measured: on-time rate 0.7628, median dep_hour 13
def corpus_reference(df):
    return {
        "on_time_rate": round((df.arr_delay_min <= ON_TIME_MINUTES).mean(), 4),
        "dep_hour_p50": float(df.dep_hour.quantile(0.50)),
    }


# Turn one cluster profile into an archetype name, by rule.
# Two stages in order:
"""
    1. Is this cluster on time more often than the corpus is? If so it does not
        HAVE a cause, and asking "why is it late" produces nonsense. 
        Clean clusters are then split by departure hour, because that is what
        KMeans itself used to separate them.

    2. Only once a cluster is late more often than the corpus do I assign it its larget average BTS cause
"""
def name_cluster(profile, reference):
    
    means = profile["means"]

    if profile["on_time_rate"] >= reference["on_time_rate"]:
        if means["dep_hour"] <= reference["dep_hour_p50"]:
            return "clean_morning"
        return "evening_recovery"
    return profile["dominant_cause"]


def describe_cluster(profile, rule_outcome):
    means = profile["means"]
    on_time = f"{profile['on_time_rate']:.0%} on time"

    if rule_outcome == "clean_morning":
        return (
            f"{on_time}; departs around {means['dep_hour']:.0f}:00 and arrives "
            f"{abs(means['arr_delay_min']):.0f} min early on average."
        )

    if rule_outcome == "evening_recovery":
        return (
            f"{on_time}; departs around {means['dep_hour']:.0f}:00 roughly "
            f"{means['dep_delay_min']:.0f} min late but makes back "
            f"{means['recovery']:.0f} min in the air."
        )

    cause_minutes = profile["cause_means"][rule_outcome]
    cause_label = rule_outcome.replace("_delay", "").replace("_", " ")
    return (
        f"{on_time}; arrives {means['arr_delay_min']:.0f} min late on average, "
        f"{cause_minutes:.0f} min of it attributed to {cause_label}."
    )


# Core profiling function
# For each cluster, calculate Flight Count, Corpus Share, Mean of every model feature, Mean minutes of every BTS cause, dominant cause, etc.
def cluster_profiles(df):
    profiles = {}

    # groupby("cluster") splits the frame into six sub-frames, one per cluster id.
    for cluster_id, rows in df.groupby("cluster"):
        means = rows[FEATURES].mean()

        # Average minutes attributed to each of the four BTS causes, largest first.
        cause_means = {cause: round(means[cause], 2) for cause in CAUSE_COLUMNS}
        dominant_cause = max(cause_means, key=cause_means.get) #compares dictionary values while returning corresponding key.
        
        #e.g. dominant_cause == "nas_delay"

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


def build_thresholds(df, corpus, causes, profiles, reference):
    """Assemble everything Flask needs into one JSON-serialisable dict.

    Two keys carry the working data, mirroring the shape the Flask service expects
    (a per-category threshold dict plus a DEFAULT fallback):

      clusters    cluster id -> archetype name, description, and size. This is the
                  lookup that turns model.predict() == 4 into "Late-Aircraft
                  Cascade".
      thresholds  archetype name -> per-feature p25/p50/p75, plus a "DEFAULT"
                  entry. Flask compares one flight's values against these to build
                  its phrase list.
    """
    min_sample_size = int(len(df) * MIN_SAMPLE_SHARE)

    clusters = {}
    thresholds = {}

    for cluster_id, profile in profiles.items():
        outcome = name_cluster(profile, reference)
        name = ARCHETYPES[outcome]

        # Two clusters resolving to the same archetype would silently overwrite
        # each other's thresholds below. Cannot happen at k=6 on this corpus, but
        # this file is re-run whenever the pipeline is, and a silent overwrite is
        # the kind of bug that surfaces three phases later.
        assert name not in thresholds, f"two clusters both named {name!r}"

        clusters[str(cluster_id)] = {
            "name": name,
            "description": describe_cluster(profile, outcome),
            "n": profile["n"],
            "share": profile["share"],
            "on_time_rate": profile["on_time_rate"],
            "dominant_cause": profile["dominant_cause"],
        }

        # Small clusters borrow the corpus percentiles. A "high taxi-out for a
        # Weather-Stranded flight" derived from 59 rows is a statement about those
        # 59 rows; the corpus number is the honest fallback.
        own_thresholds = profile["n"] >= min_sample_size
        feature_thresholds = dict(profile["percentiles"] if own_thresholds else corpus)

        # Cause thresholds are always the nonzero-corpus ones - identical in every
        # archetype. "A lot of NAS delay" has to mean the same thing everywhere, or
        # the phrase is comparing a flight against itself.
        feature_thresholds.update(causes)

        thresholds[name] = feature_thresholds

    default = dict(corpus)
    default.update(causes)
    thresholds["DEFAULT"] = default

    return {
        # Provenance, so the file explains itself to anyone reading it on GitHub.
        "generated_from": {
            "rows": len(df),
            "clusters": len(profiles),
            "min_sample_size": min_sample_size,
        },
        "on_time_minutes": ON_TIME_MINUTES,
        "corpus_on_time_rate": reference["on_time_rate"],
        "clusters": clusters,
        "thresholds": thresholds,
    }


def write_thresholds(payload):
    # flight-analyzer/ may not exist yet the first time this runs.
    os.makedirs(ANALYZER_DIR, exist_ok=True)

    with open(THRESHOLDS_JSON, "w") as f:
        # indent=2 because this file is committed and meant to be read by a human
        # browsing the repo, not only parsed by Flask.
        json.dump(payload, f, indent=2)
        f.write("\n")

    print(f"\nWrote {os.path.relpath(THRESHOLDS_JSON, HERE)}")


def print_names(payload):
    """The six named clusters, largest first - the deliverable of Phase 4."""
    print(f"\n{'=' * 78}")
    print("  NAMED ARCHETYPES")
    print(f"{'=' * 78}")

    ordered = sorted(payload["clusters"].items(), key=lambda kv: -kv[1]["n"])
    fallback_below = payload["generated_from"]["min_sample_size"]

    for cluster_id, cluster in ordered:
        borrowed = "  (thresholds: corpus fallback)" if cluster["n"] < fallback_below else ""
        print(f"\n  cluster {cluster_id} -> {cluster['name']}{borrowed}")
        print(f"    {cluster['n']:,} flights ({cluster['share']:.1%})")
        print(f"    {cluster['description']}")

    print(f"\n{'=' * 78}")


def main():
    df = load_clustered()
    corpus = corpus_percentiles(df)
    causes = cause_percentiles(df)
    profiles = cluster_profiles(df)
    reference = corpus_reference(df)

    print_profiles(profiles, corpus)

    payload = build_thresholds(df, corpus, causes, profiles, reference)
    print_names(payload)
    write_thresholds(payload)


if __name__ == "__main__":
    main()
