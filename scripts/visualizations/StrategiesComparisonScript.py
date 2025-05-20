import os
import re
import matplotlib.pyplot as plt

def parse_bug_line(bug_line):
    """
    Given a line like:
      Bugs found: Data structure invariant/validation violation
    Returns True if a bug is indicated and False if not.
    """
    if not bug_line:  # Prevent IndexError if bug_line is empty
        return False

    content = bug_line.split("Bugs found:", 1)[-1].strip().lower()
    if content == "0" or content.startswith("0") or "no bugs found" in content:
        return False
    return True


def process_results_file(filepath):
    """
    Processes a results.txt file by splitting its content into test-case blocks.
    Extracts results for modelCheckingTest and stressTest.
    Returns a list of tuples (model_success, stress_success) for each test case.
    """
    results = []
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"[ERROR] Cannot read file {filepath}: {e}")
        return results

    # Ensure the file starts with the marker so splitting works correctly.
    if not content.startswith("Processing file:"):
        content = "Processing file:\n" + content

    # Split the file into blocks based on "Processing file:" markers.
    blocks = re.split(r"(?=Processing file:)", content)
    for block in blocks:
        block = block.strip()
        if not block:
            continue

        # Extract filename (for debugging)
        filename_match = re.search(r"Processing file: (.+)", block)
        filename = filename_match.group(1) if filename_match else "[UNKNOWN FILE]"

        model_success = False
        stress_success = False

        # Adjusted regex: now looks for "Testing time" followed by "Bugs found:"
        model_match = re.search(r"Test method:.*?modelCheckingTest[\s\S]+?Testing time:.*?\nBugs found: (.+)", block, re.IGNORECASE)
        stress_match = re.search(r"Test method:.*?stressTest[\s\S]+?Testing time:.*?\nBugs found: (.+)", block, re.IGNORECASE)

        # Check if modelCheckingTest found a bug
        if model_match:
            bug_line = model_match.group(1).strip()
            model_success = parse_bug_line(bug_line)
            print(f"[DEBUG] {filename} - modelCheckingTest: {bug_line} -> {'BUG FOUND' if model_success else 'NO BUG'}")
        else:
            print(f"[DEBUG] {filename} - modelCheckingTest: NO MATCH FOUND")
            model_success = False  # Default to False if no match is found

        # Check if stressTest found a bug
        if stress_match:
            bug_line = stress_match.group(1).strip()
            stress_success = parse_bug_line(bug_line)
            print(f"[DEBUG] {filename} - stressTest: {bug_line} -> {'BUG FOUND' if stress_success else 'NO BUG'}")
        else:
            print(f"[DEBUG] {filename} - stressTest: NO MATCH FOUND")
            stress_success = False  # Default to False if no match is found

        results.append((model_success, stress_success))
    return results

def process_all_results(root_dir):
    """
    Recursively walks root_dir to find every results.txt file,
    processes each one for its test cases, and aggregates outcomes.
    """
    outcomes = {
        "both": 0,  # Both tests found a concurrency bug
        "only_model": 0,  # Only modelCheckingTest found a bug
        "only_stress": 0,  # Only stressTest found a bug
        "none": 0  # Neither test found a bug
    }
    total_test_cases = 0

    print(f"[INFO] Scanning directory tree from: {os.path.abspath(root_dir)}\n")
    for dirpath, _, filenames in os.walk(root_dir):
        print(f"[DEBUG] Checking directory: {dirpath}")
        for filename in filenames:
            if filename.lower().endswith("results.txt"):
                filepath = os.path.join(dirpath, filename)
                print(f"  -> Processing file: {filepath}")
                test_cases = process_results_file(filepath)
                total_test_cases += len(test_cases)
                for model_success, stress_success in test_cases:
                    if model_success and stress_success:
                        outcomes["both"] += 1
                    elif not model_success and stress_success:  # If only stress test finds a bug
                        outcomes["only_stress"] += 1
                    elif model_success and not stress_success:  # If only model checking finds a bug
                        outcomes["only_model"] += 1
                    else:
                        outcomes["none"] += 1  # Neither test found a bug


    print(f"\n[INFO] Total test cases processed: {total_test_cases}")
    return outcomes

def plot_outcomes(outcomes):
    """
    Plots a bar chart summarizing the aggregated outcomes.
    """
    labels = [
        "Both tests success",
        "Only modelChecking success",
        "Only stress success",
        "None test success"
    ]
    counts = [
        outcomes["both"],
        outcomes["only_model"],
        outcomes["only_stress"],
        outcomes["none"]
    ]

    plt.figure(figsize=(8, 6))
    bars = plt.bar(labels, counts, color=["green", "blue", "orange", "gray"])
    plt.ylabel("Number of Test Cases")
    plt.title("Concurrency Violation Detection (Per Test Case)")
    for bar in bars:
        height = bar.get_height()
        plt.text(bar.get_x() + bar.get_width()/2, height, f"{int(height)}", ha="center", va="bottom")
    plt.tight_layout()
    plt.savefig("concurrency_violation_summary_per_test_case.png")
    plt.show()

if __name__ == "__main__":
    test_results_path = "../processing/test-results/test-results-2.36"

    outcomes = process_all_results(test_results_path)
    print("\n[INFO] Aggregated outcomes:", outcomes)
    plot_outcomes(outcomes)
