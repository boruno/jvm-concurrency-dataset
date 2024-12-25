import os
import json
import numpy as np
import matplotlib.pyplot as plt
from sklearn.manifold import TSNE

output_path = "."

def visualize_clusters(task_name):
    # Construct the path to the intermediate file
    intermediate_path = os.path.join(output_path, "clusters", f"{task_name}_intermediate.json")

    # Debugging: Print the full path to check correctness
    print(f"Looking for file at: {os.path.abspath(intermediate_path)}")

    # Check if the file exists
    if not os.path.exists(intermediate_path):
        print(f"File not found: {intermediate_path}")
        return

    with open(intermediate_path, 'r') as f:
        intermediate_data = json.load(f)

    distance_matrix = np.array(intermediate_data["distance_matrix"])
    cluster_labels = np.array(intermediate_data["cluster_labels"])
    files = intermediate_data["files"]

    num_samples = len(distance_matrix)

    if num_samples < 2:
        print(f"Not enough samples to visualize for {task_name}. Skipping visualization.")
        return

    # Dynamically set perplexity, ensuring it is less than the number of samples
    perplexity = min(30, num_samples - 1)

    # Compute t-SNE with compatible initialization method for precomputed distance matrix
    tsne = TSNE(n_components=2, metric="precomputed", init="random", random_state=42, perplexity=perplexity)
    reduced_embeddings = tsne.fit_transform(distance_matrix)

    plt.figure(figsize=(10, 8))
    plt.scatter(reduced_embeddings[:, 0], reduced_embeddings[:, 1], c=cluster_labels, cmap='Spectral', s=50)
    plt.title(f't-SNE visualization of clusters for {task_name}')

    # Save the visualization in the correct nested directory
    plt.savefig(os.path.join(output_path, "graphs", f'{task_name}_clusters.png'))
    plt.close()
    print(f"Visualization completed for {task_name}")


if __name__ == "__main__":
    task_names = []
    for task_name in task_names:
        visualize_clusters(task_name)
