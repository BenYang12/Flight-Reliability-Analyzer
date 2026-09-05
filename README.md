# LateBird ✈️

A full-stack flight reliability tool that answers one question: **is this flight usually late?**
No login required! Just enter route or a flight number and get on-time rates, delay causes, and
the best hour to depart, all derived from the federal on-time record.

---

## 🔍 Features

- ✈️ **Flight Reliability Lookup**
  - On-time rate, delay causes, and the 20 most recent operations for any covered flight number.
  - Expand any operation for a plain-English reading of what happened.

- 🕐 **Best Time to Fly**
  - Ranks all 24 departure hours on a route and recommends a window.
  - `LAX–SFO` departing 05:00–07:59 is **87% on time** against a route average of 56%.

- 🧠 **ML-Based Delay Archetypes**
  - KMeans clustering on normalized BTS data assigns each operation a labeled archetype.
  - An LLM phrases the finished fact set — it never sees raw numbers and cannot invent a statistic.

- 🛡️ **Sample-Guarded Statistics**
  - No rate is displayed below 10 completed flights. "100% on time" from 3 flights is a bug, not a feature.

- 🗃️ **Cached BTS + OpenSky Data**
  - Historical schedules and delays come from BTS; OpenSky supplies recent actual operations only.
  - OpenSky has no scheduled-departure field, so its rows are never labeled as delays.

---

## 📊 Coverage

Statistics cover **871,139 flights** across the **30 busiest US airports** and **13 carriers**,
from **March–September 2026**. A route is answerable only when _both_ endpoints are in that set,
so the route picker offers exactly those 30 airports and nothing else.

On time means **arriving within 15 minutes** of schedule — the FAA's definition.

---

## 🧱 Tech Stack

### Backend

- **Spring Boot 3 (Java 17)** — REST API, OpenSky ingestion, nightly aggregate job.
- **PostgreSQL** — stores flights, airports, carriers, and the route reliability aggregate.

### Frontend

- **Next.js 15 (TypeScript, App Router)** — Server Components by default, typed fetch wrappers.
- **shadcn/ui + Tailwind CSS v4** — semantic delay palette, light and dark themes.

### Machine Learning

- **Python (Flask)** — microservice that classifies one operation and phrases it.
- **scikit-learn** — StandardScaler + KMeans.
- **Pandas** — preprocessing and aggregation.

Ownership is strict: Python owns the model, Java owns the API and database. Spring talks to Flask
over HTTP rather than reimplementing the model.

---

### 🧪 Machine Learning Pipeline

- BTS CSV → Postgres → StandardScaler + KMeans → labeled clusters + `thresholds.json`.
- Rule thresholds come from corpus percentiles, never hand-tuned numbers.
- The trained `.pkl` files are committed; the deploy target does not retrain.

---

## 📥 Getting Started (Dev)

```bash
# Database
docker compose up -d

# ML pipeline (one time, populates Postgres)
cd ml
pip install -r requirements.txt
python pipeline.py

# ML Microservice — port 5001, since macOS AirPlay owns 5000
cd flight-analyzer
pip install -r requirements.txt
flask --app app run --port 5001

# Backend (Spring Boot) — port 8081
cd server
./mvnw spring-boot:run

# Frontend — port 3000
cd frontend
npm install
npm run dev
```

---

## About

This project was created by Benjamin Yang (me) <yangbenjamin19@gmail.com>.

## 📄 Disclaimer

> **LateBird** is not endorsed by, affiliated with, or representative of the US Bureau of
> Transportation Statistics, the Federal Aviation Administration, OpenSky Network, or any airline.
> Statistics are derived from publicly available federal on-time performance data and describe
> historical patterns only — they are not a prediction about any future flight.
