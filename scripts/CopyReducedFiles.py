import os
import shutil

# Paths
initial_dataset_path = "../data/studentSolutions"
reduction_file_path = "../data/clusteredStudentSolutions/editDistanceClustering/EDC_final_reduction.txt"
results_path = "../data/clusteredStudentSolutions/editDistanceClustering/results"

def copy_reduced_files():
    # Dictionary to track all the files that should be present for each task folder
    required_files_per_task = {}

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

            # Track the files that should exist in the folder
            if folder_name not in required_files_per_task:
                required_files_per_task[folder_name] = set()
            required_files_per_task[folder_name].add(filename)

            if not os.path.exists(destination_folder_path):
                os.makedirs(destination_folder_path)

            if os.path.exists(destination_file_path):
                # File already exists in the result folder, so skip
                print(f"Skipping {filename}, already exists in {destination_folder_path}")
                continue

            if os.path.exists(source_file_path):
                shutil.copy(source_file_path, destination_file_path)
                print(f"Copied {filename} to {destination_folder_path}")
            else:
                # Warning for files not found in the source directory
                print(f"Warning: {filename} not found in {source_file_path}")

    # Cleaning up files left from previous reductions
    for folder_name, required_files in required_files_per_task.items():
        destination_folder_path = os.path.join(results_path, folder_name)
        if os.path.exists(destination_folder_path):
            current_files = set(os.listdir(destination_folder_path))
            files_to_remove = current_files - required_files

            for file_to_remove in files_to_remove:
                file_path = os.path.join(destination_folder_path, file_to_remove)
                os.remove(file_path)
                print(f"Deleted leftover file: {file_to_remove} from {destination_folder_path}")



if __name__ == "__main__":
    copy_reduced_files()
