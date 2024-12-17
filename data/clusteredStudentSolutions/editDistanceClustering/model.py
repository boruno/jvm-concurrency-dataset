import os
import numpy as np
from sklearn.cluster import AgglomerativeClustering
import json
import Levenshtein as lev

# Define dataset path
dataset_path = "lincheck-incorrect-data-structures-dataset/data/studentSolutions"
output_path = "."

def load_files(task_folder):
    files = []
    for filename in os.listdir(task_folder):
        if filename.endswith('.kt'):
            with open(os.path.join(task_folder, filename), 'r') as file:
                files.append((filename, file.read()))
    return files

def compute_edit_distance_matrix(files):
    num_files = len(files)
    distance_matrix = np.zeros((num_files, num_files))

    for i in range(num_files):
        for j in range(i + 1, num_files):
            distance = lev.distance(files[i][1], files[j][1])
            distance_matrix[i, j] = distance
            distance_matrix[j, i] = distance  # Symmetric matrix

    return distance_matrix

def cluster_files(distance_matrix, n_clusters=None):
    if n_clusters is None:
        n_clusters = int(np.sqrt(len(distance_matrix)))

    clustering = AgglomerativeClustering(
        n_clusters=n_clusters, metric="precomputed", linkage="average"
    )
    cluster_labels = clustering.fit_predict(distance_matrix)
    return cluster_labels

def save_clusters(task_name, files, cluster_labels, distance_matrix):
    clusters_path = os.path.join(output_path, "clusters", f"{task_name}_clusters.txt")
    clusters = {}
    for i, label in enumerate(cluster_labels):
        if label not in clusters:
            clusters[label] = []
        clusters[label].append(files[i][0])

    with open(clusters_path, 'w') as f:
        f.write(f"Taskname: {task_name}\n")
        for label, filenames in clusters.items():
            f.write(f"Cluster #{label + 1}: {', '.join(filenames)}\n")

    # Save intermediate results for visualization
    intermediate_data = {
        "task_name": task_name,
        "distance_matrix": distance_matrix.tolist(),  # Convert to list for JSON serialization
        "cluster_labels": cluster_labels if isinstance(cluster_labels, list) else cluster_labels.tolist(),
        "files": [f[0] for f in files]
    }

    intermediate_path = os.path.join(output_path, "clusters", f"{task_name}_intermediate.json")
    with open(intermediate_path, 'w') as f:
        json.dump(intermediate_data, f)


def process_task_folder(task_folder, skip_folders):
    task_name = os.path.basename(task_folder)

    if task_name in skip_folders:
        print(f"Skipping folder '{task_name}' as it was intentionally excluded.")
        return

    files = load_files(task_folder)

    if len(files) == 1:
        cluster_labels = [0]
        distance_matrix = np.zeros((1, 1))
        save_clusters(task_name, files, cluster_labels, distance_matrix)
        print(f"Task {task_name} has only one file, created a single cluster.")
        return

    distance_matrix = compute_edit_distance_matrix(files)
    cluster_labels = cluster_files(distance_matrix)
    save_clusters(task_name, files, cluster_labels, distance_matrix)
    print(f"Processed task folder: {task_name}")

if __name__ == "__main__":
    task_folders = [os.path.join(dataset_path, folder) for folder in os.listdir(dataset_path)]
    task_folders = [folder for folder in task_folders if os.path.isdir(folder)]  # Filter out non-directories
    skip_folders = []
    if not os.path.exists(os.path.join(output_path, "clusters")):
        os.makedirs(os.path.join(output_path, "clusters"))

    for task_folder in task_folders:
        process_task_folder(task_folder, skip_folders)
