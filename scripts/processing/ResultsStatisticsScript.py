import os

# Set of test methods to include in statistics.
# By default, process both testing methods.
ALLOWED_TEST_METHODS = {"modelCheckingTest", "stressTest"}
# ALLOWED_TEST_METHODS = {"modelCheckingTest"}
# ALLOWED_TEST_METHODS = {"stressTest"}

# Define error messages
not_found = {
    "No bugs found"
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
base_path = "test-results/test-results"
output_file = "test-results/testing_statistics.txt"


def parse_results(file_path):
    results = []
    current_file = None
    current_method = None
    current_time = 0.0
    file_found = False
    current_test_log = []
    skip_current_test = False
    allowed_test_in_file = False

    with open(file_path, "r") as file:
        lines = file.readlines()

        for i, line in enumerate(lines):
            line = line.strip()

            if line.startswith("Processing file:"):
                # Process the last test of the previous file if it was allowed
                if current_file and current_method and not skip_current_test:
                    test_log_str = "\n".join(current_test_log)
                    if "Wow! You've caught a bug in Lincheck." in test_log_str:
                        results.append((current_file, current_method, "Lincheck bug", current_time))
                    elif "Execution ended on timeout:" in test_log_str:
                        results.append((current_file, current_method, "Timeout", current_time))

                current_file = line.split("Processing file:")[1].strip()
                current_method = None
                current_time = 0.0
                file_found = True
                current_test_log = []
                skip_current_test = False
                allowed_test_in_file = False

            elif line.startswith("Test method:"):
                # Process previous test method if exists and if it was allowed
                if current_file and current_method and not skip_current_test:
                    test_log_str = "\n".join(current_test_log)
                    if "Wow! You've caught a bug in Lincheck." in test_log_str:
                        results.append((current_file, current_method, "Lincheck bug", current_time))
                    elif "Execution ended on timeout:" in test_log_str:
                        results.append((current_file, current_method, "Timeout", current_time))

                # Determine if the test method is allowed
                method_candidate = line.split("Test method:")[1].strip()
                if method_candidate not in ALLOWED_TEST_METHODS:
                    skip_current_test = True
                    current_method = method_candidate  # Record method name for reference
                else:
                    skip_current_test = False
                    current_method = method_candidate
                    allowed_test_in_file = True

                current_time = 0.0
                current_test_log = []

            # Add line to current test log
            current_test_log.append(line)

            if line.startswith("Testing time:"):
                try:
                    current_time = round(float(line.split("Testing time:")[1].strip().split(" ")[0]), 2)
                except ValueError:
                    current_time = 0.0

            elif "Test is not working" in line:
                if not skip_current_test:
                    test_log_str = "\n".join(current_test_log)
                    if "Lincheck bug" in line:
                        results.append((current_file, current_method or "Unknown", "Lincheck bug", current_time))
                    elif "Execution ended on timeout:" in test_log_str:
                        results.append((current_file, current_method or "Unknown", "Timeout", current_time))
                    else:
                        results.append((current_file, current_method or "Unknown", "Testing error", current_time))
                current_test_log = []

            elif current_method and not skip_current_test:
                if any(n in line for n in not_found):
                    results.append((current_file, current_method, "Correct", current_time))
                    current_test_log = []
                else:
                    for key, error_type in lincheck_error_messages.items():
                        if key in line:
                            results.append((current_file, current_method, error_type, current_time))
                            current_test_log = []
                            break

        # Process the very last test if exists and if it was allowed
        if current_file and current_method and not skip_current_test:
            test_log_str = "\n".join(current_test_log)
            if "Wow! You've caught a bug in Lincheck." in test_log_str:
                results.append((current_file, current_method, "Lincheck bug", current_time))
            elif "Execution ended on timeout:" in test_log_str:
                results.append((current_file, current_method, "Timeout", current_time))

    # Append a default result only if at least one allowed test method was found in the file
    if file_found and allowed_test_in_file and not results:
        results.append((current_file, "Unknown", "No recorded test", 0.0))

    return results


def determine_final_result(tests):
    """
    Determine a final result based on test results using the new prioritization strategy:
    - If there is only one test for a file – take its result.
    - If both tests are present (modelCheckingTest and stressTest) – pick the result with priority:
        1. Incorrect (concurrency violation): "Unexpected exception", "Execution hung",
           "Invalid execution results", "Validation function error", "Non-blocking algorithm"
        2. Testing errors: "Testing error"
        3. Timeouts: "Timeout"
        4. Lincheck bugs: "Lincheck bug"
        5. Correct: "Correct"
    """
    if len(tests) == 1:
        return tests[0][1]

    priority = {
        "Unexpected exception": 1,
        "Execution hung": 1,
        "Invalid execution results": 1,
        "Validation function error": 1,
        "Non-blocking algorithm": 1,
        "Testing error": 2,
        "Timeout": 3,
        "Lincheck bug": 4,
        "Correct": 5
    }

    def get_priority(result):
        return priority.get(result, 6)

    # Pick the result with the highest priority (lowest number)
    final_result = min((test[1] for test in tests), key=get_priority)
    return final_result


def process_statistics(base_path):
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
        "Timeout": 0,
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
                "Timeout": 0,
                "bug_categories": dict(all_categories),
                "total_time": 0.0
            }

            file_results = {}
            results_file = os.path.join(folder_path, "results.txt")

            if os.path.exists(results_file):
                results = parse_results(results_file)

                # Group results by file name
                for file_name, method, error_type, time in results:
                    if file_name not in file_results:
                        file_results[file_name] = {"tests": [], "total_time": 0.0}
                    file_results[file_name]["tests"].append((method, error_type, time))
                    file_results[file_name]["total_time"] += time

                # Process each file's results
                for file_name, file_data in file_results.items():
                    final_result = determine_final_result(file_data["tests"])
                    folder_stats["total_time"] += file_data["total_time"]

                    # Check for timeout conditions
                    timeouts = sum(1 for _, error_type, _ in file_data["tests"] if error_type == "Timeout")
                    test_errors = sum(1 for _, error_type, _ in file_data["tests"] if error_type == "Testing error")

                    if timeouts == len(file_data["tests"]) or (timeouts >= 1 and test_errors >= 1):
                        folder_stats["Timeout"] += 1
                    elif final_result == "Lincheck bug":
                        folder_stats["Lincheck bugs"] += 1
                    elif final_result == "Testing error":
                        folder_stats["Testing errors"] += 1
                    elif final_result == "Correct":
                        folder_stats["Correct"] += 1
                    else:  # It's a concurrency bug
                        folder_stats["Concurrency bugs"] += 1
                        # Only update bug_categories for actual concurrency bugs
                        if final_result in folder_stats["bug_categories"]:
                            folder_stats["bug_categories"][final_result] += 1

                if file_results:
                    statistics[folder] = folder_stats

                    # Update overall statistics
                    overall_stats["Total number of files processed"] += len(file_results)
                    overall_stats["Testing errors"] += folder_stats["Testing errors"]
                    overall_stats["Correct"] += folder_stats["Correct"]
                    overall_stats["Lincheck bugs"] += folder_stats["Lincheck bugs"]
                    overall_stats["Timeout"] += folder_stats["Timeout"]
                    overall_stats["Incorrect"] += folder_stats["Concurrency bugs"]  # Only count concurrency bugs
                    overall_stats["Total execution time"] += folder_stats["total_time"]

    return statistics, overall_stats


def write_statistics(statistics, overall_stats, output_file):
    with open(output_file, "w") as f:
        for folder, stats in statistics.items():
            f.write(f"{folder}\n")
            total_files = sum(v for k, v in stats.items() if k not in ['bug_categories', 'total_time'])

            f.write(f"Number of files processed: {total_files}\n")
            f.write(f"Testing errors: {stats['Testing errors']}\n")
            f.write(f"Lincheck bugs: {stats['Lincheck bugs']}\n")
            f.write(f"Timeouts: {stats['Timeout']}\n")
            f.write(f"Correct: {stats['Correct']}\n")
            f.write(f"Concurrency bugs: {stats['Concurrency bugs']}\n")

            # Always write all bug categories
            for category, count in stats['bug_categories'].items():
                f.write(f"    {category}: {count}\n")

            f.write(f"Total execution time: {stats['total_time']:.2f} seconds\n")
            f.write("\n")

        # Write overall statistics
        f.write("Overall Statistics\n")
        f.write(f"Total number of files processed: {overall_stats['Total number of files processed']}\n")
        f.write(f"Testing errors: {overall_stats['Testing errors']}\n")
        f.write(f"Correct: {overall_stats['Correct']}\n")
        f.write(f"Incorrect: {overall_stats['Incorrect']}\n")

        # Calculate incorrectness percentage only from files with valid results
        valid_results = overall_stats['Correct'] + overall_stats['Incorrect']
        if valid_results > 0:
            incorrectness = (overall_stats['Incorrect'] / valid_results * 100)
        else:
            incorrectness = 0.0

        f.write(f"Incorrectness: {incorrectness:.2f}%\n")
        f.write(f"Lincheck bugs: {overall_stats['Lincheck bugs']}\n")
        f.write(f"Timeouts: {overall_stats['Timeout']}\n")
        f.write(f"Total execution time: {overall_stats['Total execution time']:.2f} seconds\n")

if __name__ == "__main__":
    statistics, overall_stats = process_statistics(base_path)
    write_statistics(statistics, overall_stats, output_file)
    print(f"Statistics written to {output_file}")
