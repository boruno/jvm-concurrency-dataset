import os
import json
import numpy as np
from math import log2, floor
from scipy.spatial.distance import pdist, squareform

dataset_path = "./clusters"  # Path to the folder containing JSON and TXT files
output_file = "HDBSCAN_final_reduction.txt"

def load_json_data(taskname):
    json_path = os.path.join(dataset_path, f"{taskname}_data.json")
    with open(json_path, 'r') as f:
        data = json.load(f)
    return data

def calculate_cluster_center(embeddings, cluster_labels, cluster_id):
    # Find the center of the cluster (point closest to all other points in the cluster).
    cluster_points = embeddings[cluster_labels == cluster_id]
    distances = squareform(pdist(cluster_points)) # Pairwise distances
    center_index = np.argmin(distances.sum(axis=0))
    return np.where(cluster_labels == cluster_id)[0][center_index] # Return the index in the original embeddings

def select_representative_points(embeddings, cluster_centers, N):
    # Select N points that are maximally spaced apart.
    selected_points = [cluster_centers[0]]
    cluster_centers_set = set(cluster_centers) # To track remaining points
    cluster_centers_set.remove(cluster_centers[0])

    while len(selected_points) < N and cluster_centers_set:
        remaining_points = list(cluster_centers_set)
        # Calculate distances between selected points and remaining points
        distances = np.array([np.linalg.norm(embeddings[selected_points[-1]] - embeddings[p]) for p in remaining_points])

        # Select the point that is farthest from the last selected point
        next_point = remaining_points[np.argmax(distances)]
        selected_points.append(next_point)
        cluster_centers_set.remove(next_point)

    return selected_points

def process_task(taskname):
    data = load_json_data(taskname)
    embeddings = np.array(data['embeddings'])
    cluster_labels = np.array(data['cluster_labels'])
    file_paths = data['file_paths']

    # Calculate N – number of files to select
    num_files = len(file_paths)
    N = max(1, floor(log2(num_files)))  # Ensure at least 1 file is selected

    # Ignore the largest cluster and small clusters (with 1-2 files)
    unique_labels, counts = np.unique(cluster_labels, return_counts=True)
    valid_clusters = unique_labels[(counts > 2) & (unique_labels != np.argmax(counts))]

    if len(valid_clusters) < N:
        valid_clusters = unique_labels[counts > 1]  # Workaround to include some small clusters if needed

    # Find cluster centers
    cluster_centers = []
    for cluster_id in valid_clusters:
        center_idx = calculate_cluster_center(embeddings, cluster_labels, cluster_id)
        cluster_centers.append(center_idx)

    # Select N maximally spaced points
    representative_points = select_representative_points(embeddings, cluster_centers, N)

    # Get filenames for the selected points
    selected_files = [file_paths[i] for i in representative_points]

    # Write results to the output file
    with open(output_file, 'a') as f:
        f.write(f"{taskname}: {','.join(selected_files)}\n")
    print(f"Processed {taskname}: selected {len(selected_files)} files.")


if __name__ == "__main__":
    if os.path.exists(output_file):
        os.remove(output_file)

    task_files = [f for f in os.listdir(dataset_path) if f.endswith('_data.json')]
    tasknames = [f.replace('_data.json', '') for f in task_files]

    for taskname in tasknames:
        process_task(taskname)
