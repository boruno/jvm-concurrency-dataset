import os
import shutil

# Paths
initial_dataset_path = "data/studentSolutions"
reduction_file_path = "data/clusteredStudentSolutions/EditDistanceClustering/EditDistanceClustering_final_reduction.txt"
results_path = "data/clusteredStudentSolutions/EditDistanceClustering/results"

def copy_reduced_files():
    with open(reduction_file_path, 'r') as f:
        reduction_data = f.readlines()

    for line in reduction_data:
        task_name, filenames_str = line.strip().split(": ")
        filenames = filenames_str.split(",")

        for filename in filenames:
            folder_name = filename.split('-')[0]

            source_file_path = os.path.join(initial_dataset_path, folder_name, filename)
            destination_folder_path = os.path.join(results_path, folder_name)
            destination_file_path = os.path.join(destination_folder_path, filename)

            if not os.path.exists(destination_folder_path):
                os.makedirs(destination_folder_path)

            if os.path.exists(source_file_path):
                shutil.copy(source_file_path, destination_file_path)
                # print(f"Copied {filename} to {destination_folder_path}")
            else:
                print(f"Warning: {filename} not found in {source_file_path}")

if __name__ == "__main__":
    copy_reduced_files()
