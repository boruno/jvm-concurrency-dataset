import os
from collections import defaultdict
from typing import Dict, Tuple, List

folder_1_path = "./test-results-2.36"
folder_2_path = "./test-results-2.36"
test_method_1 = "modelCheckingTest"
test_method_2 = "stressTest"
output_file = "stress_vs_mc-comparison_results.txt"

lincheck_error_messages = {
    "Non-linearizability": "Invalid execution results",
    "Non-atomicity": "Execution hung",
    "Exception": "Unexpected exception",
    "Validation error": "Validation function error",
    "Non-blocking": "Non-blocking algorithm"
}


def parse_results(file_path):
    results = []
    current_file = None
    current_method = None
    current_time = 0.0
    file_found = False
    current_test_log = []

    with open(file_path, "r") as file:
        lines = file.readlines()

        for i, line in enumerate(lines):
            line = line.strip()

            if line.startswith("Processing file:"):
                # Process the last test of previous file if exists
                if current_file and current_method:
                    log_content = "\n".join(current_test_log)
                    results.append(process_current_test(current_file, current_method, log_content, current_time))

                current_file = line.split("Processing file:")[1].strip()
                current_method = None
                current_time = 0.0
                file_found = True
                current_test_log = []

            elif line.startswith("Test method:"):
                # Process previous test method if exists
                if current_file and current_method:
                    log_content = "\n".join(current_test_log)
                    results.append(process_current_test(current_file, current_method, log_content, current_time))

                current_method = line.split("Test method:")[1].strip()
                current_time = 0.0
                current_test_log = []

            # Add line to current test log
            current_test_log.append(line)

            if line.startswith("Testing time:"):
                current_time = round(float(line.split("Testing time:")[1].strip().split(" ")[0]), 2)

        # Process the very last test if exists
        if current_file and current_method:
            log_content = "\n".join(current_test_log)
            results.append(process_current_test(current_file, current_method, log_content, current_time))

    if file_found and not results:
        results.append((current_file, "Unknown", "No recorded test", 0.0))

    return results


def process_current_test(file_name, method, log_content, time):
    # Check for Lincheck bugs
    if "Wow! You've caught a bug in Lincheck." in log_content:
        return file_name, method, "Lincheck bug", time

    # Check for timeouts
    if "Execution ended on timeout:" in log_content:
        return file_name, method, "Timeout", time

    # Check for test failures
    if "Test is not working" in log_content:
        return file_name, method, "Test failure", time

    # Check for correct results
    if "No bugs found" in log_content:
        return file_name, method, "Correct", time

    # Check for specific concurrency bugs
    if "Bugs found: Hung" in log_content or "The execution has hung" in log_content:
        return file_name, method, "Execution hung", time

    if "Non-blocking check failed" in log_content:
        return file_name, method, "Non-blocking algorithm", time

    if "Validation function error" in log_content:
        return file_name, method, "Validation function error", time

    if any(exc in log_content for exc in ["Exception", "Error:", "Throwable"]):
        return file_name, method, "Unexpected exception", time

    # If bugs found but not matching above categories, mark as invalid execution results
    if "Bugs found" in log_content:
        return file_name, method, "Invalid execution results", time

    # Default case
    return file_name, method, "Test failure", time

def process_statistics(base_path: str, test_method) -> Tuple[Dict, Dict]:
    all_categories = {
        "Unexpected exception": 0,
        "Execution hung": 0,
        "Invalid execution results": 0,
        "Validation function error": 0,
        "Non-blocking algorithm": 0
    }

    statistics = {}
    overall_stats = {
        "Total number of files processed": 0,
        "Testing errors": 0,
        "Correct": 0,
        "Incorrect": 0,
        "Lincheck bugs": 0,
        "Timeouts": 0,
        "Total execution time": 0.0
    }

    for folder in os.listdir(base_path):
        folder_path = os.path.join(base_path, folder)
        if os.path.isdir(folder_path):
            folder_stats = {
                "Testing errors": 0,
                "Lincheck bugs": 0,
                "Correct": 0,
                "Concurrency bugs": 0,
                "Timeouts": 0,
                "bug_categories": dict(all_categories),
                "total_time": 0.0
            }

            results_file = os.path.join(folder_path, "results.txt")
            if os.path.exists(results_file):
                file_results = {}
                results = parse_results(results_file)

                for file_name, method, error_type, time in results:
                    if method != test_method:
                        continue
                    if file_name not in file_results:
                        file_results[file_name] = {"tests": [], "total_time": 0.0}
                    file_results[file_name]["tests"].append((method, error_type, time))
                    file_results[file_name]["total_time"] += time

                for file_data in file_results.values():
                    folder_stats["total_time"] += file_data["total_time"]

                    # Check for timeouts
                    timeouts = sum(1 for _, error_type, _ in file_data["tests"] if error_type == "Timeout")
                    test_errors = sum(1 for _, error_type, _ in file_data["tests"] if error_type == "Test failure")

                    if timeouts > 0 or (timeouts >= 1 and test_errors >= 1):
                        folder_stats["Timeouts"] += 1
                    elif any(error == "Test failure" for _, error, _ in file_data["tests"]):
                        folder_stats["Testing errors"] += 1
                    elif any(error == "Lincheck bug" for _, error, _ in file_data["tests"]):
                        folder_stats["Lincheck bugs"] += 1
                    elif all(error == "Correct" for _, error, _ in file_data["tests"]):
                        folder_stats["Correct"] += 1
                    else:
                        folder_stats["Concurrency bugs"] += 1
                        for _, error, _ in file_data["tests"]:
                            if error in folder_stats["bug_categories"]:
                                folder_stats["bug_categories"][error] += 1

                if file_results:
                    statistics[folder] = folder_stats
                    overall_stats["Total number of files processed"] += len(file_results)
                    overall_stats["Testing errors"] += folder_stats["Testing errors"]
                    overall_stats["Correct"] += folder_stats["Correct"]
                    overall_stats["Lincheck bugs"] += folder_stats["Lincheck bugs"]
                    overall_stats["Incorrect"] += folder_stats["Concurrency bugs"]
                    overall_stats["Timeouts"] += folder_stats["Timeouts"]
                    overall_stats["Total execution time"] += folder_stats["total_time"]

    return statistics, overall_stats


def write_comparison(stats1, overall1, stats2, overall2, output_file):
    folder1 = folder_1_path.removeprefix("./test-results-")
    folder2 = folder_2_path.removeprefix("./test-results-")

    # Append testing methods to the folder names
    folder1_header = f"{folder1}-{test_method_1.removesuffix('Test')}"
    folder2_header = f"{folder2}-{test_method_2.removesuffix('Test')}"

    with open(output_file, "w") as f:
        # Write header using the header variables with left alignment over 45 characters
        f.write(f"{folder1_header:<45}|{folder2_header:<45}\n")
        f.write("-" * 91 + "\n")
        f.write(f"{'':<45}|\n")

        # Write overall statistics
        f.write(f"{'Overall Statistics:':<45}|{'':<45}\n")
        for key in overall1.keys():
            val1 = overall1[key]
            val2 = overall2[key]
            if isinstance(val1, float):
                if key == "Total execution time":
                    f.write(f"{key}: {val1} seconds".ljust(45) + "|" + f"{key}: {val2:.2f} seconds".ljust(45) + "\n")
                else:
                    f.write(f"{key}: {val1:.2f}".ljust(45) + "|" + f"{key}: {val2:.2f}".ljust(45) + "\n")
            else:
                f.write(f"{key}: {val1}".ljust(45) + "|" + f"{key}: {val2}".ljust(45) + "\n")

        # Write divider and section header
        f.write("-" * 91 + "\n")
        f.write(f"{'Detailed Statistics by Data Structure:':<45}|{'':<45}\n")
        f.write("-" * 91 + "\n")

        # Process each data structure
        all_folders = sorted(set(list(stats1.keys()) + list(stats2.keys())))
        for folder in all_folders:
            # Write data structure name
            f.write(f"{folder}:".ljust(45) + "|" + f"{'':<45}\n")

            folder_stats1 = stats1.get(folder, {})
            folder_stats2 = stats2.get(folder, {})

            # Write main statistics
            main_stats = ["Testing errors", "Lincheck bugs", "Correct", "Concurrency bugs",
                          "Timeouts"]
            for stat in main_stats:
                val1 = folder_stats1.get(stat, 0)
                val2 = folder_stats2.get(stat, 0)
                f.write(f"{stat}: {val1}".ljust(45) + "|" + f"{stat}: {val2}".ljust(45) + "\n")

            # Write total time
            time1 = folder_stats1.get("total_time", 0)
            time2 = folder_stats2.get("total_time", 0)
            f.write(f"Total time: {time1:.2f} seconds".ljust(45) + "|" + f"Total time: {time2:.2f} seconds".ljust(45) + "\n")

            # Write bug categories if there are any concurrency bugs
            if folder_stats1.get("Concurrency bugs", 0) > 0 or folder_stats2.get("Concurrency bugs", 0) > 0:
                f.write(f"{'Detailed bug categories:':<45}|{'Detailed bug categories:':<45}\n")

                bugs1 = folder_stats1.get("bug_categories", {})
                bugs2 = folder_stats2.get("bug_categories", {})

                # Ensure all categories are present
                categories = ["Unexpected exception", "Execution hung", "Invalid execution results",
                              "Validation function error", "Non-blocking algorithm"]
                for bugs in [bugs1, bugs2]:
                    for category in categories:
                        if category not in bugs:
                            bugs[category] = 0

                # Write each category
                for category in categories:
                    val1 = bugs1.get(category, 0)
                    val2 = bugs2.get(category, 0)
                    f.write(f"    {category}: {val1}".ljust(45) + "|" + f"    {category}: {val2}".ljust(45) + "\n")

            # Add empty line after each data structure
            f.write(f"{'':<45}|\n")

            # Add division line between data structures (except after the last one)
            if folder != all_folders[-1]:
                f.write("-" * 91 + "\n")


def main():
    # Process both versions
    stats1, overall1 = process_statistics(folder_1_path, test_method_1)
    stats2, overall2 = process_statistics(folder_2_path, test_method_2)

    # Write comparison
    write_comparison(stats1, overall1, stats2, overall2, output_file)


if __name__ == "__main__":
    main()
