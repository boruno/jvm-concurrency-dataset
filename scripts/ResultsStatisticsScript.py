import os
import re
from collections import defaultdict

# Define error messages
not_found = {
    "No errors or Lincheck-specific results found.",
    "No Lincheck results found.",
    "Execution ended on timeout."
}

lincheck_error_messages = {
    "= The execution failed with an unexpected exception =": "Unexpected exception",
    "= The execution has hung =": "Execution hung",
    "= The execution has hung, see the thread dump =": "Execution hung",
    "= Invalid execution results =": "Invalid execution results",
    "= Validation function": "Validation function error",
    "The algorithm should be non-blocking": "Non-blocking algorithm",
    "Wow! You've caught a bug in Lincheck.": "Lincheck bug"
}

# Path to the directory containing subfolders
base_path = "test-results"

# Output file
output_file = "testing_statistics.txt"

def parse_results(file_path):
    with open(file_path, "r") as file:
        lines = file.readlines()

    results = []
    current_file = None
    current_method = None
    for line in lines:
        line = line.strip()
        if line.startswith("Processing file:"):
            current_file = line.split("Processing file:")[1].strip()
            current_method = None
        elif line.startswith("Test method:"):
            current_method = line.split("Test method:")[1].strip()
        elif current_method:
            if line in not_found:
                results.append((current_file, current_method, "no_error"))
            else:
                for key, error_type in lincheck_error_messages.items():
                    if key in line:
                        results.append((current_file, current_method, error_type))
                        break
    return results

def process_statistics():
    statistics = defaultdict(lambda: defaultdict(int))
    overall_stats = {"total_files": 0, "correct_files": 0, "incorrect_files": 0, "lincheck_bug_files": 0}

    for folder_name in os.listdir(base_path):
        folder_path = os.path.join(base_path, folder_name)
        if not os.path.isdir(folder_path):
            continue

        result_file = os.path.join(folder_path, "results.txt")
        if not os.path.exists(result_file):
            continue

        results = parse_results(result_file)
        file_stats = defaultdict(list)
        for filename, method, error_type in results:
            file_stats[filename].append(error_type)

        statistics[folder_name]["Number of files processed"] = len(file_stats)
        overall_stats["total_files"] += len(file_stats)

        for errors in file_stats.values():
            if errors == ["no_error", "no_error"]:
                statistics[folder_name]["Correct"] += 1
                overall_stats["correct_files"] += 1
            elif "Lincheck bug" in errors:
                statistics[folder_name]["Lincheck bug"] += 1
                overall_stats["incorrect_files"] += 1
                overall_stats["lincheck_bug_files"] += 1
            elif any(e != "no_error" for e in errors):
                error_type = errors[0] if errors[0] != "no_error" else errors[1]
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
        incorrectness_percentage = (incorrect_files / total_files) * 100 if total_files > 0 else 0

        file.write("Overall Statistics\n")
        file.write(f"Total number of files processed: {total_files}\n")
        file.write(f"Correct: {correct_files}\n")
        file.write(f"Incorrect: {incorrect_files}\n")
        file.write(f"Incorrectness: {incorrectness_percentage:.2f}%\n")
        file.write(f"Lincheck bugs: {lincheck_bug_files}\n")

if __name__ == "__main__":
    statistics, overall_stats = process_statistics()
    write_statistics(statistics, overall_stats)
    print(f"Statistics written to {output_file}")
