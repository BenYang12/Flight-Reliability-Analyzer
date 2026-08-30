"""
Entire offline Pipeline 
Usage: python pipeline.py

BTS raw CSVs -> training set -> clusters -> named archetypes:

1. Download BTS On-Time Performance months        (get_data.py)
2. Clean, engineer features -> training_data.csv  (get_data.py)
3. normalize()            -> scale the 14 features onto one scale
4. run_k_means()          -> fit KMeans, k=6
5. export_labeled_data()  -> clustered_data.csv + kmeans_model.pkl + scaler.pkl with help of jolib
6. Name each cluster from its dominant delay cause (interpret_data.py, implement next with phase 4)

Only steps 3-5 run so far in this codebase 
Steps 1-2 are a separate script done with help of claude
Step 6 does not exist yet.

Everything here is deterministic: same CSV in, same six clusters out.
"""

from normalize_data import normalize, run_k_means, export_labeled_data


def main():
    # normalize() hands back three things because the next two steps each need a
    # different one: the frame for export, the matrix for fitting, the scaler for
    # saving alongside the model.
    df, scaled, scaler = normalize()

    model, labels = run_k_means(scaled)

    export_labeled_data(df, labels, model, scaler)

    # Cluster sizes are the one-line sanity check
    print(f"Clustered {len(labels):,} flights into {model.n_clusters} groups.")
    for cluster in range(model.n_clusters):
        count = (labels == cluster).sum()
        print(f"  cluster {cluster}: {count:>7,} flights ({count / len(labels):.1%})")


# Only runs when this file is executed directly, not when something imports it.
if __name__ == "__main__":
    main()
