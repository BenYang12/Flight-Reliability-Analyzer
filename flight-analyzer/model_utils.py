"""
One flight operation -> a cluster -> a list of short descriptive phrases.

This is the deterministic half of the service.

Layout follows the reference service:
    1. the artifacts
    2. the per-archetype threshold dicts + a DEFAULT fallback
    3. interpret_cluster(row) -> the phrase list
    4. the LLM call (next checkpoint)
"""

import json
import os

import anthropic
import joblib
import pandas as pd

HERE = os.path.dirname(__file__)

# Loaded at import, not per request. Flask reuses the module across requests, so
# unpickling a model on every call would add hundreds of milliseconds for nothing.
SCALER = joblib.load(os.path.join(HERE, "scaler.pkl"))
MODEL = joblib.load(os.path.join(HERE, "kmeans_model.pkl"))

with open(os.path.join(HERE, "thresholds.json")) as f:
    _THRESHOLDS = json.load(f)

# cluster id (as a string, because JSON keys always are) -> name + description
CLUSTERS = _THRESHOLDS["clusters"]

# archetype name -> {feature: {p25, p50, p75}}
THRESHOLD_SETS = _THRESHOLDS["thresholds"]

# The fallback used when an archetype has no threshold set of its own. Corpus-wide
# percentiles: the honest answer to "high compared to what?" when we have nothing
# more specific.
DEFAULT = THRESHOLD_SETS["DEFAULT"]

# The FAA on-time definition, carried in the file rather than retyped here.
ON_TIME_MINUTES = _THRESHOLDS["on_time_minutes"]

# The 14 features the model was fitted on, in fit order. Read off the scaler
# itself rather than restated: the artifact is the source of truth, and a list
# that disagreed with it would produce silently wrong clusters.
FEATURES = list(SCALER.feature_names_in_)

CAUSE_COLUMNS = [
    "carrier_delay", "weather_delay", "nas_delay", "late_aircraft_delay",
]

# Phrases, keyed by feature, as (below p25, above p75). A feature inside its
# typical band contributes nothing - saying "typical taxi-out" about every
# ordinary flight would bury the two or three phrases that actually matter.
PHRASES = {
    "dep_delay_min": ("pushed back early", "left well behind schedule"),
    "arr_delay_min": ("landed comfortably early", "landed well behind schedule"),
    "taxi_out": ("quick taxi to the runway", "unusually long taxi before takeoff"),
    "taxi_in": ("reached the gate quickly", "long wait for a gate after landing"),
    "recovery": ("lost further time in the air", "made up time in the air"),
    "delay_ratio": (None, "delay was large relative to the length of the flight"),
}

# The subset of PHRASES where a HIGH value is bad news. `recovery` is deliberately
# absent: high recovery means the crew made up time, so it must never contribute to
# a severity judgement.
SEVERITY_FEATURES = [
    "dep_delay_min", "arr_delay_min", "taxi_out", "taxi_in", "delay_ratio",
]

# Cause phrases fire only when the cause has minutes attributed at all, so they
# need one phrase rather than a low/high pair.
CAUSE_PHRASES = {
    "carrier_delay": ("some delay attributed to the airline",
                      "substantial delay attributed to the airline"),
    "weather_delay": ("some delay attributed to weather",
                      "substantial delay attributed to weather"),
    "nas_delay": ("some delay attributed to air traffic control",
                  "substantial delay attributed to congestion and air traffic control"),
    "late_aircraft_delay": ("some delay inherited from a late inbound aircraft",
                            "substantial delay inherited from a late inbound aircraft"),
}


def derive_features(row):
    """Fill in the three engineered features from the raw fields.

    Spring sends what it has - scheduled and actual times, taxi minutes, the BTS
    cause breakdown. These three are computed, and the computation lives here
    rather than in Java because Python owns the model: a second implementation of
    delay_ratio on the other side of an HTTP call is a second thing to keep in
    sync, and the day they disagree the clusters go quietly wrong.

    Mirrors get_data.py's build_training_set() exactly.
    """
    derived = dict(row)

    # BTS packs clock times as integers: 1435 means 14:35. // 100 takes the hour,
    # and % 24 folds 2400 (a legal BTS value for midnight) back to hour 0.
    derived.setdefault("dep_hour", (row["crs_dep_time"] // 100) % 24)

    # Positive means the crew clawed time back between wheels-up and wheels-down.
    derived.setdefault("recovery", row["dep_delay_min"] - row["arr_delay_min"])

    # Arrival delay as a fraction of the scheduled block time: 20 minutes late on a
    # 60-minute hop is a worse operation than 20 minutes late on a transcon.
    derived.setdefault(
        "delay_ratio", row["arr_delay_min"] / row["crs_elapsed_time"]
    )

    return derived


def classify(row):
    """Predict the cluster for one flight and return (cluster_id, archetype name)."""
    # A one-row DataFrame with named columns, so scikit-learn can check them
    # against the names it memorised at fit time. Handing it a bare array instead
    # would let a column-order mistake through as a plausible wrong answer rather
    # than an error - see ml/analyze_flight.py.
    features = pd.DataFrame([row]).reindex(columns=FEATURES)

    # A cause with no minutes attributed is genuinely zero, exactly as in training.
    features[CAUSE_COLUMNS] = features[CAUSE_COLUMNS].fillna(0)

    # transform(), never fit_transform(): the scaler's means and standard
    # deviations belong to the training corpus, not to this one flight.
    cluster_id = int(MODEL.predict(SCALER.transform(features))[0])

    return cluster_id, CLUSTERS[str(cluster_id)]["name"]


def interpret_cluster(row, archetype):
    """Compare one flight against percentile thresholds -> a list of phrases.

    Every phrase is the result of a threshold comparison, and every threshold came
    from a corpus percentile. No branch here encodes an opinion.

    Two reference sets, answering two different questions:

        DEFAULT (corpus)  "is this long or short compared to flights generally?"
        the archetype's   "is this extreme even for flights of this pattern?"

    The absolute phrases MUST come from the corpus. These archetypes are defined by
    the very outcomes being described - Late-Aircraft Cascade IS the set of very
    late flights - so judging a 90-minute-late flight against its own archetype
    puts it below that archetype's p25 and yields "landed comfortably early" about
    a flight that landed late. That is a base-rate error, and it is why the
    reference service's per-category pattern does not transfer here unchanged: its
    categories are roles, which are an input, not an outcome.

    The per-archetype set still earns its place, for the second question only.
    """
    # An archetype with no thresholds of its own is judged against the corpus for
    # both questions - the DEFAULT fallback in the reference's shape.
    peers = THRESHOLD_SETS.get(archetype, DEFAULT)
    phrases = []

    # Set when a SEVERITY feature is beyond the p75 of its own archetype: bad even
    # among flights sharing its pattern. One phrase rather than one per feature -
    # the signal is "a bad example of its kind", not which column said so.
    extreme_for_archetype = False

    # The headline fact, and the only hardcoded number in the service: the FAA
    # defines an on-time arrival as within 15 minutes.
    on_time = row["arr_delay_min"] <= ON_TIME_MINUTES
    phrases.append(
        "arrived on time by the FAA's definition" if on_time
        else "arrived late by the FAA's definition"
    )

    for feature, (low_phrase, high_phrase) in PHRASES.items():
        value = row[feature]

        if value <= DEFAULT[feature]["p25"] and low_phrase:
            phrases.append(low_phrase)
        elif value >= DEFAULT[feature]["p75"] and high_phrase:
            phrases.append(high_phrase)

            if feature in SEVERITY_FEATURES and value >= peers[feature]["p75"]:
                extreme_for_archetype = True

    for cause, (some_phrase, lots_phrase) in CAUSE_PHRASES.items():
        # A cause absent from the payload means no minutes were attributed to it.
        minutes = row.get(cause) or 0

        # Zero is not a low value here. The cause thresholds were computed only
        # over flights that HAD that cause (see ml/interpret_data.py), so scoring a
        # zero against them would answer a question nobody asked.
        if minutes <= 0:
            continue

        # Cause thresholds are identical in every set, so DEFAULT is the same
        # lookup as the archetype's - used here for consistency with the above.
        phrases.append(
            lots_phrase if minutes >= DEFAULT[cause]["p75"] else some_phrase
        )

    # Gated on lateness as well: "severe for its pattern" is a meaningless thing to
    # say about a flight that arrived on time, however unusual one of its columns.
    if extreme_for_archetype and not on_time:
        phrases.append("severe even compared with other flights of this pattern")

    return phrases


def analyze(row, with_summary=False):
    """The full result for one flight operation.

    `facts` is the only field the LLM stage is allowed to see. Everything else is
    for the UI, which renders the numbers itself.

    with_summary is off by default so the batch endpoint cannot accidentally fire
    one API call per flight - see app.py.
    """
    features = derive_features(row)
    cluster_id, archetype = classify(features)
    facts = interpret_cluster(features, archetype)

    return {
        "cluster": cluster_id,
        "archetype": archetype,
        "description": CLUSTERS[str(cluster_id)]["description"],
        "on_time": features["arr_delay_min"] <= ON_TIME_MINUTES,
        "facts": facts,

        # None whenever the LLM is unavailable. The four fields above are unaffected.
        "summary": summarize(archetype, facts) if with_summary else None,
    }


# The LLM finishing layer.

# Everything above this line is deterministic. This is the only part that talks to
# a model, and it is deliberately the last thing that happens: by the time it runs,
# every fact is already decided, so it has nothing left to do but choose words.
#
# What it receives is the whole point. It gets the archetype NAME and the phrase
# list - no minutes, no percentages, no rates. Note what is withheld: the archetype
# `description` sits right there in the analyze() result, but it reads "92% on time;
# departs around 9:00..." and is therefore off limits. A model that has never been
# shown a statistic cannot misreport one; that is a structural guarantee rather than
# an instruction it might ignore.

# Reached through OpenRouter, which serves an Anthropic-Messages-compatible
# endpoint - so the anthropic SDK below is unchanged and only the model id is
# namespaced. Point it somewhere else by setting ANTHROPIC_BASE_URL back to the
# default and using a bare model id; nothing else in this file depends on it.
MODEL_NAME = "anthropic/claude-haiku-4.5"

# A short paragraph, so the response ceiling is small on purpose.
MAX_TOKENS = 512

SYSTEM_PROMPT = """You write one short paragraph for an air traveler about a \
single flight operation.

You will be given the operation's archetype and a list of established facts about \
it. Write 2-3 plain sentences conveying those facts and what they mean for a \
traveler.

Rules:
- Use only the facts given. They are the complete record of this flight.
- Never state a number, percentage, duration, or rate. You have not been given \
any, and inventing one would misinform the reader.
- No bullet points, no headings, no preamble. Just the paragraph."""

# Built on first use rather than at import, so the service starts without a key and
# degrades instead of crashing - see summarize().
_client = None


def _get_client():
    """The Anthropic client, or None when no credentials are configured.

    The check has to happen here rather than in summarize()'s except clause: an
    unauthenticated request raises TypeError, which is NOT an AnthropicError and
    would escape as a 500 instead of degrading.

    It asks the CLIENT what it resolved rather than reading ANTHROPIC_API_KEY
    directly, because the SDK accepts credentials from several places. Testing the
    env var would report "unavailable" for a perfectly working client configured
    some other way.
    """
    global _client

    if _client is None:
        client = anthropic.Anthropic()

        # Both are None when the SDK found no credentials anywhere.
        if not (client.api_key or client.auth_token):
            return None

        _client = client

    return _client


def summarize(archetype, facts):
    """Phrase a finished fact list as a paragraph. Returns None if unavailable.

    None is a designed outcome, not a failure to handle later. No API key, a cold
    or unreachable service, a rate limit, a refusal - all return None, and the
    caller still sends every deterministic fact. That is the "analysis unavailable,
    statistics still shown" state the UI is required to design for.
    """
    client = _get_client()
    if client is None:
        return None

    # The entire payload the model ever sees.
    prompt = "Archetype: {}\n\nFacts:\n{}".format(
        archetype, "\n".join(f"- {fact}" for fact in facts)
    )

    try:
        response = client.messages.create(
            model=MODEL_NAME,
            max_tokens=MAX_TOKENS,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )
    # AnthropicError is the base of every SDK failure - HTTP status, connection,
    # auth. Caught as one class rather than the usual most-specific-first chain
    # because there is nothing to differentiate: every branch degrades identically.
    # It deliberately does NOT catch our own bugs, which should still surface.
    except anthropic.AnthropicError:
        return None

    # A safety refusal arrives as a normal 200 with empty content, so it has to be
    # checked rather than caught.
    if response.stop_reason == "refusal":
        return None

    # .content is a list of blocks; the text ones are what was written.
    return "".join(b.text for b in response.content if b.type == "text").strip()
