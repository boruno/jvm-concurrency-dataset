import os
import json
import numpy as np
from math import log2, floor

dataset_path = "./clusters"  # Path to the folder containing JSON and TXT files
output_file = "EDC_final_reduction.txt"

def load_json_data(taskname):
    # Load JSON distance matrix and cluster data for a given task.
    json_path = os.path.join(dataset_path, f"{taskname}_intermediate.json")
    with open(json_path, 'r') as f:
        data = json.load(f)
    return data

def calculate_cluster_center(distance_matrix, cluster_labels, cluster_id):
    # Find the center of the cluster (point closest to all other points in the cluster).
    cluster_indices = np.where(cluster_labels == cluster_id)[0]
    cluster_distances = distance_matrix[np.ix_(cluster_indices, cluster_indices)]
    center_index = np.argmin(cluster_distances.sum(axis=1))  # Point with the smallest sum of distances
    return cluster_indices[center_index]

def select_representative_points(distance_matrix, cluster_centers, N):
    # Select N points that are maximally spaced apart, based on the distance matrix.
    if not cluster_centers:
        return []

    selected_points = [cluster_centers[0]]
    cluster_centers_set = set(cluster_centers)
    cluster_centers_set.remove(cluster_centers[0])

    while len(selected_points) < N and cluster_centers_set:
        remaining_points = list(cluster_centers_set)
        # Calculate distances from the last selected point to the remaining points
        distances = np.array([distance_matrix[selected_points[-1], p] for p in remaining_points])

        # Select the point that is farthest from the last selected point
        next_point = remaining_points[np.argmax(distances)]
        selected_points.append(next_point)
        cluster_centers_set.remove(next_point)

    return selected_points

def process_task(taskname):
    # Process each task, reduce files, and write the results.
    data = load_json_data(taskname)
    distance_matrix = np.array(data['distance_matrix'])
    cluster_labels = np.array(data['cluster_labels'])
    file_paths = data['files']

    # Calculate N – number of files to select
    num_files = len(file_paths)
    N = max(1, floor(log2(num_files)))  # Ensure at least 1 file is selected

    # Ignore the largest cluster and small clusters (with 1-2 files)
    unique_labels, counts = np.unique(cluster_labels, return_counts=True)
    valid_clusters = unique_labels[(counts > 2) & (unique_labels != np.argmax(counts))]

    # Handle case where no valid clusters are found
    if len(valid_clusters) < N:
        valid_clusters = unique_labels[counts > 1]  # Workaround to include some small clusters if needed

    if not valid_clusters.size:
        print(f"No valid clusters found for {taskname}, skipping.")
        return

    # Find cluster centers
    cluster_centers = []
    for cluster_id in valid_clusters:
        center_idx = calculate_cluster_center(distance_matrix, cluster_labels, cluster_id)
        cluster_centers.append(center_idx)

    # If no cluster centers are found, skip this task
    if not cluster_centers:
        print(f"Warning: No cluster centers found for {taskname}, skipping.")
        return

    # Select N maximally spaced points
    representative_points = select_representative_points(distance_matrix, cluster_centers, N)

    # Get filenames for the selected points
    selected_files = [file_paths[i] for i in representative_points]

    # Write results to the output file
    with open(output_file, 'a') as f:
        f.write(f"{taskname}: {','.join(selected_files)}\n")
    print(f"Processed {taskname}: selected {len(selected_files)} files.")

if __name__ == "__main__":
    if os.path.exists(output_file):
        os.remove(output_file)

    # Get task names from the cluster files in the folder
    task_files = [f for f in os.listdir(dataset_path) if f.endswith('_intermediate.json')]
    tasknames = [f.replace('_intermediate.json', '') for f in task_files]

    for taskname in tasknames:
        process_task(taskname)
