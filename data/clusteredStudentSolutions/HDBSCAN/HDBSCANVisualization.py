import os
import json
import numpy as np
import matplotlib.pyplot as plt
from sklearn.manifold import TSNE

output_path = "."

if not os.path.exists(os.path.join(output_path, "graphs")):
    os.makedirs(os.path.join(output_path, "graphs"))


def visualize_clusters(embeddings, labels, task_name):
    num_samples = len(embeddings)

    if num_samples < 2:
        print(f"Not enough samples to visualize for {task_name}. Skipping visualization.")
        return

    # Optional: Add small noise to prevent numerical issues
    embeddings += np.random.normal(0, 1e-8, embeddings.shape)

    perplexity = min(30, num_samples - 1)  # Ensure perplexity is less than the number of samples

    try:
        tsne = TSNE(n_components=2, random_state=42, perplexity=perplexity)
        reduced_embeddings = tsne.fit_transform(embeddings)

        plt.figure(figsize=(10, 8))
        plt.scatter(reduced_embeddings[:, 0], reduced_embeddings[:, 1], c=labels, cmap='Spectral', s=50)
        plt.title(f't-SNE visualization of clusters for {task_name}')
        plt.savefig(os.path.join(output_path, "graphs", f'{task_name}_clusters.png'))
        plt.close()
    except Exception as e:
        print(f"Failed to generate t-SNE visualization for {task_name} due to an error: {e}")


def visualize_task_from_json(json_file):
    with open(json_file, 'r') as f:
        data = json.load(f)

    task_name = data['task_name']
    embeddings = np.array(data['embeddings'])
    labels = np.array(data['cluster_labels'])

    visualize_clusters(embeddings, labels, task_name)


if __name__ == "__main__":
    json_files = [f for f in os.listdir(os.path.join(output_path, "clusters")) if f.endswith('_data.json')]

    for json_file in json_files:
        visualize_task_from_json(os.path.join(output_path, "clusters", json_file))
