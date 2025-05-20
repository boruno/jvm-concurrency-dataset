import os

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
base_path = "test-results/test-results-2.38"
output_file = "short_stats-2.38.txt"

def parse_results(file_path):
    results = {}
    current_file = None
    current_method = None
    current_time = 0.0
    current_test_log = []

    with open(file_path, "r") as file:
        lines = file.readlines()

        for line in lines:
            line = line.strip()

            if line.startswith("Processing file:"):
                if current_file and current_method:
                    process_test_log(results, current_method, current_test_log, current_time)
                current_file = line.split("Processing file:")[1].strip()
                current_method = None
                current_time = 0.0
                current_test_log = []

            elif line.startswith("Test method:"):
                if current_file and current_method:
                    process_test_log(results, current_method, current_test_log, current_time)
                current_method = line.split("Test method:")[1].strip()
                current_time = 0.0
                current_test_log = []

            current_test_log.append(line)

            if line.startswith("Testing time:"):
                try:
                    current_time = round(float(line.split("Testing time:")[1].strip().split(" ")[0]), 2)
                except ValueError:
                    current_time = 0.0

        if current_file and current_method:
            process_test_log(results, current_method, current_test_log, current_time)

    return results

def process_test_log(results, method, test_log, test_time):
    strategy = "modelChecking" if "modelChecking" in method.lower() else "stress"
    if strategy not in results:
        results[strategy] = {}
    if method not in results[strategy]:
        results[strategy][method] = {"Processed": 0, "Non-executable": 0, "Correct": 0, "Incorrect": 0, "Total time": 0.0}

    results[strategy][method]["Processed"] += 1
    results[strategy][method]["Total time"] += test_time

    test_log_str = "\n".join(test_log)
    if any(msg in test_log_str for msg in not_found):
        results[strategy][method]["Correct"] += 1
    else:
        for key, error_type in lincheck_error_messages.items():
            if key in test_log_str:
                results[strategy][method]["Incorrect"] += 1
                return
        results[strategy][method]["Non-executable"] += 1

def process_statistics(base_path):
    all_results = {"modelChecking": {}, "stress": {}}

    for folder in os.listdir(base_path):
        folder_path = os.path.join(base_path, folder)
        if os.path.isdir(folder_path):
            results_file = os.path.join(folder_path, "results.txt")
            if os.path.exists(results_file):
                folder_results = parse_results(results_file)
                for strategy, methods in folder_results.items():
                    for method, stats in methods.items():
                        if method not in all_results[strategy]:
                            all_results[strategy][method] = {"Processed": 0, "Non-executable": 0, "Correct": 0, "Incorrect": 0, "Total time": 0.0}
                        for key in stats:
                            all_results[strategy][method][key] += stats[key]

    return all_results

def write_statistics(results, output_file):
    with open(output_file, "w") as f:
        for strategy in ["modelChecking", "stress"]:
            f.write(f"{strategy} Strategy\n")
            f.write("====================================\n")
            total_files = sum(stats['Processed'] for stats in results[strategy].values())
            total_time = sum(stats['Total time'] for stats in results[strategy].values())
            total_correct = sum(stats['Correct'] for stats in results[strategy].values())
            total_incorrect = sum(stats['Incorrect'] for stats in results[strategy].values())
            total_non_executable = sum(stats['Non-executable'] for stats in results[strategy].values())

            for method, stats in results[strategy].items():
                f.write(f"Test method: {method}\n")
                f.write(f"Number of files processed: {stats['Processed']}\n")
                f.write(f"Non-executable tests: {stats['Non-executable']}\n")
                f.write(f"Correct: {stats['Correct']}\n")
                f.write(f"Incorrect: {stats['Incorrect']}\n")
                f.write(f"Execution time: {stats['Total time']:.2f} seconds\n\n")

            f.write(f"Total number of files processed: {total_files}\n")
            f.write(f"Total execution time: {total_time:.2f} seconds\n")
            f.write(f"Total Non-executable tests: {total_non_executable}\n")
            f.write(f"Total Correct: {total_correct}\n")
            f.write(f"Total Incorrect: {total_incorrect}\n\n")

if __name__ == "__main__":
    statistics = process_statistics(base_path)
    write_statistics(statistics, output_file)
    print(f"Statistics written to {output_file}")
