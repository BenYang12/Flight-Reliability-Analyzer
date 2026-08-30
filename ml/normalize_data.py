"""
training_data.csv -> scaled feature matrix -> KMeans -> clustered_data.csv + saved model

get_data.py already produced the training set
Phase 3 turns it into clusters, which will involve three steps....

1. normalize() -> select model features and put them on one scale
2. run_k_means() -> fit clustering model
3. export_labeled_data() -> write labeled CSV (training rows + cluster assigned to each flight) and persist scaler + model (save trained clustering model)

Nothing here names or interprets a cluster. Numbered groups are the only deliverable for now

"""

import os
import pandas as pd
from sklearn.preprocessing import StandardScaler

# Config
TRAINING_CSV = os.path.join(os.path.dirname(__file__), "training_data.csv")

# 14 Model Features
# Once I decided to exclude...
#   security_delay  - nonzero in a rounding error's worth of rows; a feature that is always the same value tells KMeans nothing.
#   crs_elapsed_time - it is already folded into delay_ratio below.
#   flight_number / carrier_iata / origin / dest / flight_date - identifiers
#                     and categories, not numeric magnitudes. Euclidean
#                     distance between two airport codes is meaningless.
FEATURES = [
    # Raw operational measurements.
    "dep_delay_min", "arr_delay_min", "taxi_out", "taxi_in", "distance",

    # BTS's own attribution of WHY a flight was late.
    "carrier_delay", "weather_delay", "nas_delay", "late_aircraft_delay",

    # Late-aircraft cascades build over a day, and weather exposure is seasonal, so time-of-day and time-of-year matter.
    "dep_hour", "day_of_week", "month",


    # derived from feature engineering I carried out in get_data.py:
    #   delay_ratio = arr_delay / scheduled_block_time  (delay relative to trip length)
    #   recovery    = dep_delay - arr_delay             (minutes made up in the air; positive means the crew clawed time back with stuff like jet streams)
    "delay_ratio", "recovery",
]

# BTS only requires a carrier to attribute a cause when arrival delay is 15+
# minutes, so these four columns are null on roughly 75% of rows. 
# Null here means "no reportable delay from this cause," which is genuinely zero
# minutes. Thus, filling with 0 will record the fact rather than inventing one.
# Dropping the null rows instead would delete every on-time flight and leave a model that has never seen a clean operation.
CAUSE_COLUMNS = [
    "carrier_delay", "weather_delay", "nas_delay", "late_aircraft_delay",
]


def normalize():
    df = pd.read_csv(TRAINING_CSV)

    # Copy so the fill below never mutates a slice of the original frame.
    features = df[FEATURES].copy()
    features[CAUSE_COLUMNS] = features[CAUSE_COLUMNS].fillna(0)

    scaler = StandardScaler()
    # fit_transform = learn each column's mean/std (fit), then apply the
    # rescaling (transform). Returns a plain numpy array, not a DataFrame.
    scaled = scaler.fit_transform(features)

    return df, scaled, scaler
