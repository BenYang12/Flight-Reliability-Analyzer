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
import joblib
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.preprocessing import StandardScaler

# Config
HERE = os.path.dirname(__file__)
TRAINING_CSV = os.path.join(HERE, "training_data.csv")
CLUSTERED_CSV = os.path.join(HERE, "clustered_data.csv")

# The model is useless without the scaler that produced its centers, so the two
# are written together and must always travel together. Both now land in
# flight-analyzer/ alongside thresholds.json: Flask is the only thing that reads
# them, and the deploy target does not retrain. Writing them there directly rather
# than copying means there is never a stale second copy to diverge from.
ANALYZER_DIR = os.path.join(HERE, os.pardir, "flight-analyzer")
MODEL_PKL = os.path.join(ANALYZER_DIR, "kmeans_model.pkl")
SCALER_PKL = os.path.join(ANALYZER_DIR, "scaler.pkl")

# I hand KMeans a number of groups and it obeys. Thus, I have to justify it.

# Sweeping k=3..10 (elbow + silhouette):
#   - the elbow was inconclusive.
#   - k=3 scored a misleadingly high silhouette (0.44) by parking 88.5% of
#     flights in one cluster. Would lead to useless model
#   - across k=4..10 silhouette was flat (0.10-0.13), so it could not decide either.

# With both tests inconclusive, the tiebreaker was interpretability.
# k=6 splits the two large "clean flight" groups by departure hour rather than by
# calendar month: 8:48am departures land 7.6 min early, 5:36pm departures land 0.9 min late. That is the late-aircraft cascade emerging on its own, and it is the single largest cause of
# US delay minutes
N_CLUSTERS = 6

# Pins cluster numbering across runs
RANDOM_STATE = 42

# Try 10 different random starts and keep the best. KMeans can converge to a poor
# local solution from an unlucky start!
N_INIT = 10

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

# hand 200,000 x 14 scaled matrix to KMeans, ask for 6 groups, and get back...
# 1. model
# 2. labels
def run_k_means(scaled):
    """Fit KMeans on the scaled matrix. Returns the trained model and one cluster
    number per flight, in the same row order as the training frame."""
    model = KMeans(n_clusters=N_CLUSTERS, random_state=RANDOM_STATE, n_init=N_INIT)

    # fit_predict = fit (find the 6 cluster centers) then predict (assign every row to its nearest center), in one pass. 
    # Returns a numpy array of 200,000
    # integers are arbitrary labels
    labels = model.fit_predict(scaled)

    return model, labels



# return new clustered_data.csv which is essentially cluster column stapeled on to original training dataset.
def export_labeled_data(df, labels, model, scaler):
    # .copy() so the caller's frame is left untouched
    labeled = df.copy()
    labeled["cluster"] = labels

    # index=False: pandas writes its row numbers as an unnamed first column
    # otherwise, which reads as a stray field to anything parsing this later.
    labeled.to_csv(CLUSTERED_CSV, index=False)



    # flight-analyzer/ may not exist yet on a first run.
    os.makedirs(ANALYZER_DIR, exist_ok=True)

    # serialize an arbitrary Python object and save it to a file on disk.
    # .dump() saves and converts trained model into a .pkl (pickle) file
    joblib.dump(model, MODEL_PKL)
    joblib.dump(scaler, SCALER_PKL)

    return labeled
