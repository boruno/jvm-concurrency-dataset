import os
import hdbscan
import torch
import json
from transformers import RobertaTokenizer, RobertaModel

dataset_path = "/Users/Dmitrii.Kotov/repos/concurrency-tools/lincheck-incorrect-data-structures-dataset/data/studentSolutions"
output_path = "."
tokenizer = RobertaTokenizer.from_pretrained("microsoft/codebert-base")
model = RobertaModel.from_pretrained("microsoft/codebert-base")

if not os.path.exists(os.path.join(output_path, "clusters")):
    os.makedirs(os.path.join(output_path, "clusters"))


def get_embedding(code):
    inputs = tokenizer(code, return_tensors="pt", truncation=True, max_length=512)
    outputs = model(**inputs)
    return outputs.last_hidden_state.mean(dim=1).detach()


def cluster_embeddings(embeddings):
    clusterer = hdbscan.HDBSCAN(min_cluster_size=10, metric='euclidean')
    cluster_labels = clusterer.fit_predict(embeddings)
    return cluster_labels


def process_task_folder(task_folder):
    task_name = os.path.basename(task_folder)

    print(f"Processing task: {task_name}")

    files = [f for f in os.listdir(task_folder) if f.endswith('.kt')]
    if not files:
        print(f"No Kotlin files found in {task_folder}")
        return

    embeddings = []
    file_paths = []

    for file in files:
        file_path = os.path.join(task_folder, file)
        with open(file_path, 'r') as f:
            code = f.read()
        embedding = get_embedding(code)
        embeddings.append(embedding)
        file_paths.append(file)

    if len(embeddings) < 2:
        print(f"Not enough files to cluster in {task_name}")
        return

    # Stack embeddings and convert to numpy
    embeddings = torch.vstack(embeddings).numpy()
    cluster_labels = cluster_embeddings(embeddings)

    # Save embeddings and cluster labels to JSON
    output_data = {
        "task_name": task_name,
        "embeddings": embeddings.tolist(),
        "cluster_labels": cluster_labels.tolist(),
        "file_paths": file_paths
    }

    json_path = os.path.join(output_path, "clusters", f'{task_name}_data.json')
    with open(json_path, 'w') as f:
        json.dump(output_data, f)

    result_file_path = os.path.join(output_path, "clusters", f'{task_name}_clusters.txt')
    with open(result_file_path, 'w') as f:
        f.write(f"Taskname: {task_name}\n")
        clusters = {}
        for idx, label in enumerate(cluster_labels):
            if label not in clusters:
                clusters[label] = []
            clusters[label].append(file_paths[idx])

        for cluster_label, files in clusters.items():
            f.write(f"Cluster#{cluster_label + 1}: {', '.join(files)}\n")

    print(f"Completed processing for {task_name}. Results stored in {json_path} and {result_file_path}")


if __name__ == "__main__":
    task_folders = [os.path.join(dataset_path, folder) for folder in os.listdir(dataset_path)]

    for task_folder in task_folders:
        if os.path.isdir(task_folder):
            process_task_folder(task_folder)
