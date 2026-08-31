"""
Entire offline Pipeline 
Usage: python pipeline.py

BTS raw CSVs -> training set -> clusters -> named archetypes:

1. Download BTS On-Time Performance months        (get_data.py)
2. Clean, engineer features -> training_data.csv  (get_data.py)
3. normalize()            -> scale the 14 features onto one scale
4. run_k_means()          -> fit KMeans, k=6
5. export_labeled_data()  -> clustered_data.csv + kmeans_model.pkl + scaler.pkl with help of jolib
6. Name each cluster from its dominant delay cause -> thresholds.json (interpret_data.py)

Steps 3-6 run here. Steps 1-2 are a separate script (get_data.py).

Everything here is deterministic: same CSV in, same six named clusters out.

Steps 5 and 6 both write into flight-analyzer/ - the two .pkl files and
thresholds.json. That directory is the handoff point between the offline pipeline
and the Flask service; nothing at deploy time retrains.
"""

from interpret_data import main as interpret
from normalize_data import normalize, run_k_means, export_labeled_data


def main():
    # normalize() hands back three things because the next two steps each need a
    # different one: the frame for export, the matrix for fitting, the scaler for
    # saving alongside the model.
    df, scaled, scaler = normalize()

    model, labels = run_k_means(scaled)

    export_labeled_data(df, labels, model, scaler) # in normalize_data.py

    # Cluster sizes are the one-line sanity check
    print(f"Clustered {len(labels):,} flights into {model.n_clusters} groups.")
    for cluster in range(model.n_clusters):
        count = (labels == cluster).sum()
        print(f"  cluster {cluster}: {count:>7,} flights ({count / len(labels):.1%})")

    # Step 6. Re-reads clustered_data.csv from disk rather than taking the frame
    # we already have in memory. Slower by a few seconds, and worth it: it proves
    # the committed CSV is a complete input on its own, so interpret_data.py can be
    # re-run alone while iterating on the naming rule without refitting KMeans.
    interpret()


# Only runs when this file is executed directly, not when something imports it.
if __name__ == "__main__":
    main()
