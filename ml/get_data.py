"""
BTS/TranStats raw monthly files -> get_data.py cleans them -> training_data.csv (filtered sample) and postgres

This is the one-time bulk load. It reads the raw monthly downloads from
TranStats, reshapes them into the exact column layout of the`flights` table,
loads them into Postgres, and writes the training set Phase 3 will cluster.

Usage:
    python get_data.py
"""

import glob
import io
import os
import zipfile

import pandas as pd
import psycopg

# Config
RAW_DIR = os.path.join(os.path.dirname(__file__), "data", "raw")
TRAINING_CSV = os.path.join(os.path.dirname(__file__), "training_data.csv")

# Same database application.yaml points at (host port 5434, per docker-compose).
# Read from the environment so a real deployment never needs this default.
DSN = os.environ.get(
    "DB_DSN", "postgresql://latebird:latebird@localhost:5434/latebird"
)

# Rows per COPY chunk. Keeps peak memory flat regardless of corpus size.
COPY_CHUNK_SIZE = 100_000

# The 30 busiest US airports, pinned rather than derived from the data.
# The filter requires BOTH endpoints to be in this set. Requiring only one
# endpoint would nearly triple the row count
TOP_30 = {
    "ATL", "DFW", "DEN", "ORD", "LAX", "CLT", "MCO", "LAS", "PHX", "MIA",
    "SEA", "IAH", "JFK", "EWR", "FLL", "MSP", "SFO", "DTW", "BOS", "SLC",
    "PHL", "BWI", "TPA", "SAN", "LGA", "MDW", "BNA", "IAD", "DCA", "AUS",
}

# KMeans does not need 871k rows to find stable clusters, I want committed CSV to stay browsable on GitHub. and the committed
# Postgres keeps every row; this sample is only what the model trains on.
TRAINING_SAMPLE_SIZE = 200_000

# Fixed seed so the committed CSV is reproducible from the same four months.
RANDOM_SEED = 42

# Columns we consume.
SOURCE_COLUMNS = [
    "FlightDate", "Month", "DayOfWeek",
    "Flight_Number_Reporting_Airline",
    "IATA_CODE_Reporting_Airline",
    "Origin", "Dest", "Distance",
    "CRSDepTime", "DepTime", "DepDelay", "TaxiOut",
    "TaxiIn", "CRSArrTime", "ArrTime", "ArrDelay",
    "CRSElapsedTime",
    "Cancelled", "Diverted",
    "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay",
    "LateAircraftDelay",
]

INT_COLUMNS = [
    "Month", "DayOfWeek", "Distance", "Flight_Number_Reporting_Airline",
    "CRSDepTime", "DepTime", "DepDelay", "TaxiOut",
    "TaxiIn", "CRSArrTime", "ArrTime", "ArrDelay", "CRSElapsedTime",
    "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay",
    "LateAircraftDelay",
]

# BTS source column -> our `flights` column. Everything not listed here is
# either derived (flight_number), constant (source), or deliberately absent
# (callsign, cluster_id).
COLUMN_MAP = {
    "FlightDate": "flight_date",
    "Month": "month",
    "DayOfWeek": "day_of_week",
    "IATA_CODE_Reporting_Airline": "carrier_iata",
    "Origin": "origin",
    "Dest": "dest",
    "Distance": "distance",
    "CRSDepTime": "crs_dep_time",
    "DepTime": "dep_time",
    "DepDelay": "dep_delay_min",
    "CRSArrTime": "crs_arr_time",
    "ArrTime": "arr_time",
    "CRSElapsedTime": "crs_elapsed_time",
    "ArrDelay": "arr_delay_min",
    "TaxiOut": "taxi_out",
    "TaxiIn": "taxi_in",
    "Cancelled": "cancelled",
    "Diverted": "diverted",
    "CarrierDelay": "carrier_delay",
    "WeatherDelay": "weather_delay",
    "NASDelay": "nas_delay",
    "SecurityDelay": "security_delay",
    "LateAircraftDelay": "late_aircraft_delay",
}

# Exactly what the committed training CSV contains, in order. 
# Pinned, so Phase 3 reads a stable schema and no stray BTS column leaks in.
TRAINING_COLUMNS = [
    "flight_number", "carrier_iata", "origin", "dest", "flight_date",
    "dep_delay_min", "arr_delay_min", "taxi_out", "taxi_in", "distance",
    "carrier_delay", "weather_delay", "nas_delay", "security_delay",
    "late_aircraft_delay",
    "dep_hour", "day_of_week", "month",
    "crs_elapsed_time", "delay_ratio", "recovery",
]

# The `flights` columns COPY writes, in the order the CSV rows are generated.
# Not in this list:
#   id         - Postgres generates it
#   callsign   - OpenSky's field only
#   cluster_id - Phase 3 assigns it
FLIGHT_COLUMNS = [
    "flight_number", "carrier_iata", "origin", "dest", "flight_date",
    "crs_dep_time", "dep_time", "crs_arr_time", "arr_time", "crs_elapsed_time",
    "dep_delay_min", "arr_delay_min",
    "cancelled", "diverted",
    "carrier_delay", "weather_delay", "nas_delay", "security_delay",
    "late_aircraft_delay",
    "distance", "day_of_week", "month", "taxi_out", "taxi_in",
    "source",
]


# Reading
def csv_handle(path):
    if not path.endswith(".zip"):
        return open(path, "rb")
    archive = zipfile.ZipFile(path)
    members = [n for n in archive.namelist() if n.lower().endswith(".csv")]
    if len(members) != 1:
        raise ValueError(f"Expected exactly one .csv inside {path}, found {members}")
    return archive.open(members[0])


def read_month(path):
    """Read one monthly file and return it in `flights`-table shape."""
    df = pd.read_csv(
        csv_handle(path),
        usecols=SOURCE_COLUMNS,
        dtype={c: "Int64" for c in INT_COLUMNS},
        parse_dates=["FlightDate"],
    )
    before = len(df)

    # Both endpoints must be in scope, or route statistics come out lopsided.
    df = df[df.Origin.isin(TOP_30) & df.Dest.isin(TOP_30)].copy()

    # "UA" + 523 -> "UA523", the form a user actually types into the search box.
    df["flight_number"] = (
        df.IATA_CODE_Reporting_Airline + df.Flight_Number_Reporting_Airline.astype(str)
    )

    # Cancelled/Diverted arrive as 0.0/1.0 floats.
    df["Cancelled"] = df.Cancelled.fillna(0).astype(bool)
    df["Diverted"] = df.Diverted.fillna(0).astype(bool)

    df = df.rename(columns=COLUMN_MAP)
    df["source"] = "BTS"

    print(f"  {os.path.basename(path):<62} {before:>8,} -> {len(df):>8,}")
    return df


def load_all():
    """Read every monthly file in data/raw and stack them into one frame."""
    files = sorted(glob.glob(os.path.join(RAW_DIR, "*.zip")) +
                   glob.glob(os.path.join(RAW_DIR, "*.csv")))
    if not files:
        raise SystemExit(f"No BTS files found in {RAW_DIR}")

    print(f"Reading {len(files)} monthly file(s)  (rows in -> rows after top-30 filter)")
    frames = [read_month(f) for f in files]
    df = pd.concat(frames, ignore_index=True)

    # The `flights` table has UNIQUE (flight_number, flight_date, origin, dest).
    # BTS does contain occasional duplicates on that key, so we resolve them
    # here rather than letting Postgres reject the whole batch.
    before = len(df)
    df = df.drop_duplicates(
        subset=["flight_number", "flight_date", "origin", "dest"], keep="first"
    )
    dropped = before - len(df)
    if dropped:
        print(f"\nDropped {dropped:,} duplicate rows on (flight_number, date, origin, dest)")

    return df



# Training set
def build_training_set(df):
    """The rows Phase 3 clusters, plus the two features the DB does not store.

    Cancelled and diverted flights stay in the database — they are the
    numerator of cancel_rate — but they cannot be clustered: they have no
    arrival time, so every delay feature is null.
    """
    training = df[~df.cancelled & ~df.diverted & df.arr_delay_min.notna()].copy()
    training["dep_hour"] = (training.crs_dep_time // 100) % 24
    training["recovery"] = training.dep_delay_min - training.arr_delay_min
    training["delay_ratio"] = training.arr_delay_min / training.crs_elapsed_time

    if len(training) > TRAINING_SAMPLE_SIZE:
        # Fixed seed: the committed CSV must be reproducible from the same input.
        training = training.sample(n=TRAINING_SAMPLE_SIZE, random_state=RANDOM_SEED)

    return training[TRAINING_COLUMNS]


def summarize(df, training):
    """Print the same sanity numbers we validated the raw download against."""
    completed = df[~df.cancelled & ~df.diverted & df.arr_delay_min.notna()]
    print(f"\n{'=' * 70}")
    print(f"  total rows (-> Postgres) : {len(df):,}")
    print(f"  date range               : {df.flight_date.min().date()} -> {df.flight_date.max().date()}")
    print(f"  distinct routes          : {df.groupby(['origin', 'dest']).ngroups:,}")
    print(f"  distinct flight numbers  : {df.flight_number.nunique():,}")
    print(f"  cancelled                : {df.cancelled.sum():,} ({df.cancelled.mean():.2%})")
    print(f"  diverted                 : {df.diverted.sum():,} ({df.diverted.mean():.2%})")
    print(f"  ON-TIME RATE             : {(completed.arr_delay_min <= 15).mean():.2%}   (expect ~0.75-0.82)")
    print(f"\n  training rows (-> CSV)   : {len(training):,}")
    print(f"  dep_hour range           : {training.dep_hour.min()} - {training.dep_hour.max()}   (must be 0-23)")
    print(f"  recovery mean            : {training.recovery.mean():.2f} min")
    print(f"{'=' * 70}")


# Postgres load
def load_to_postgres(df):
    """Bulk-load every row into `flights` using COPY.

    COPY streams rows straight into the table in one statement instead of
    running 871k INSERTs. 

    """
    print(f"\nLoading {len(df):,} rows into Postgres...")

    # Idempotency
    with psycopg.connect(DSN) as conn, conn.cursor() as cur:
        cur.execute("DELETE FROM flights WHERE source = 'BTS'")
        print(f"  cleared {cur.rowcount:,} existing BTS rows")

        columns = ", ".join(FLIGHT_COLUMNS)
        copy_sql = f"COPY flights ({columns}) FROM STDIN WITH (FORMAT csv, NULL '')"

        with cur.copy(copy_sql) as copy:
            for start in range(0, len(df), COPY_CHUNK_SIZE):
                chunk = df.iloc[start:start + COPY_CHUNK_SIZE]
                buffer = io.StringIO()
                chunk[FLIGHT_COLUMNS].to_csv(
                    buffer, index=False, header=False, na_rep=""
                )
                copy.write(buffer.getvalue())
                print(f"  copied {min(start + COPY_CHUNK_SIZE, len(df)):>8,} / {len(df):,}")

        conn.commit()

    print("Load complete.")


def main():
    df = load_all()
    training = build_training_set(df)
    summarize(df, training)
    load_to_postgres(df)
    training.to_csv(TRAINING_CSV, index=False, na_rep="")
    size_mb = os.path.getsize(TRAINING_CSV) / 1_000_000
    print(f"\nWrote {TRAINING_CSV}  ({size_mb:.1f} MB)")


if __name__ == "__main__":
    main()
