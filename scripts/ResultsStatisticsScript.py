import os
import re
import time
from collections import defaultdict

# Define error messages
not_found = {
    "No bugs found",
    "Execution ended on timeout:",
    "No errors or Lincheck-specific results found.",
    "No Lincheck results found.",
    "Test is not working"
}

lincheck_error_messages = {
    "= The execution failed with an unexpected exception =": "Unexpected exception",
    "= The execution has hung =": "Execution hung",
    "= The execution has hung, see the thread dump =": "Execution hung",
    "= Invalid execution results =": "Invalid execution results",  # most often non-linearizability
    "= Validation function": "Validation function error",
    "The algorithm should be non-blocking": "Non-blocking algorithm",
}

# Path to the directory containing subfolders
base_path = "./test-results"

# Output file
output_file = "testing_statistics.txt"

def parse_results(file_path):
    with open(file_path, "r") as file:
        lines = file.readlines()

    results = []
    current_file = None
    current_method = None
    current_time = 0.0
    file_found = False

    for line in lines:
        line = line.strip()
        if line.startswith("Processing file:"):
            current_file = line.split("Processing file:")[1].strip()
            current_method = None
            file_found = True
        elif line.startswith("Test method:"):
            current_method = line.split("Test method:")[1].strip()
        elif line.startswith("Testing time:"):
            current_time = round(float(line.split("Testing time:")[1].strip().split(" ")[0]), 2)
        elif "Test is not working" in line:
            results.append((current_file, "Unknown", "Test failure", current_time))
        elif current_method:
            if any(n in line for n in not_found):
                results.append((current_file, current_method, "Correct", current_time))
            else:
                for key, error_type in lincheck_error_messages.items():
                    if key in line:
                        results.append((current_file, current_method, error_type, current_time))
                        break

    if file_found and not results:
        results.append((current_file, "Unknown", "No recorded test", 0.0))

    return results

def process_statistics():
    statistics = defaultdict(lambda: defaultdict(int))
    overall_stats = {
        "compilation_errors": 0,
        "total_files": 0, "correct_files": 0, "incorrect_files": 0,
        "lincheck_bug_files": 0, "total_time": 0.0
    }

    for folder_name in os.listdir(base_path):
        folder_path = os.path.join(base_path, folder_name)
        if not os.path.isdir(folder_path):
            continue

        result_file = os.path.join(folder_path, "results.txt")
        if not os.path.exists(result_file):
            continue

        results = parse_results(result_file)
        file_stats = defaultdict(list)
        total_folder_time = 0.0

        for filename, method, error_type, exec_time in results:
            file_stats[filename].append(error_type)
            total_folder_time += round(exec_time, 2)

        statistics[folder_name]["Number of files processed"] = len(file_stats)
        statistics[folder_name]["Total execution time"] = total_folder_time
        overall_stats["total_files"] += len(file_stats)
        overall_stats["total_time"] += round(total_folder_time, 2)

        for errors in file_stats.values():
            if "Correct" in errors:
                statistics[folder_name]["Correct"] += 1
                overall_stats["correct_files"] += 1
            elif "Lincheck bug" in errors:
                statistics[folder_name]["Lincheck bug"] += 1
                overall_stats["lincheck_bug_files"] += 1
            else:
                error_type = errors[0]  # Take the first encountered error
                statistics[folder_name][error_type] += 1
                overall_stats["incorrect_files"] += 1

    return statistics, overall_stats

def write_statistics(statistics, overall_stats):
    with open(output_file, "w") as file:
        for task, stats in statistics.items():
            file.write(f"{task}\n")
            for key, value in stats.items():
                file.write(f"{key}: {value}\n")
            file.write("\n")

        # Overall statistics
        total_files = overall_stats["total_files"]
        correct_files = overall_stats["correct_files"]
        incorrect_files = overall_stats["incorrect_files"]
        lincheck_bug_files = overall_stats["lincheck_bug_files"]
        total_time = overall_stats["total_time"]
        valid_files = total_files - overall_stats["compilation_errors"] - overall_stats["lincheck_bug_files"]
        incorrectness_percentage = (incorrect_files / valid_files) * 100 if valid_files > 0 else 0

        file.write("Overall Statistics\n")
        file.write(f"Compilation errors: {overall_stats['compilation_errors']}\n")
        file.write(f"Total number of files processed: {total_files}\n")
        file.write(f"Total execution time: {total_time:.2f} seconds\n")
        file.write(f"Correct: {correct_files}\n")
        file.write(f"Incorrect: {incorrect_files}\n")
        file.write(f"Incorrectness: {incorrectness_percentage:.2f}%\n")
        file.write(f"Lincheck bugs: {lincheck_bug_files}\n")

if __name__ == "__main__":
    statistics, overall_stats = process_statistics()
    write_statistics(statistics, overall_stats)
    print(f"Statistics written to {output_file}")
