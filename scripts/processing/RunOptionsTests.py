#!/usr/bin/env python3
import os
import shutil
import subprocess
import re
import sys

# Change these paths as needed:
ORIG_PROJECTS_PATH = "../../template-projects"  # original projects folder
TEMP_BASE_DIR = "./temp_projects_options"       # where temporary copies will be stored
TEST_RUNNER_SCRIPT = "StudentSolutionTestWithOptions.py"    # the test runner
DATASET_PATH = "../../data/clusteredStudentSolutions/HDBSCAN/results"  # unchanged
RESULTS_BASE_DIR = "./options_testing"           # base directory for test results

# Default parameter values (as used in your tests)
DEFAULT_ACTORS = (0, 0)          # actorsBefore, actorsAfter
DEFAULT_ITERATIONS = 50
DEFAULT_INVOCATIONS = 1000
DEFAULT_THREADS = (3, 3)         # threads, actorsPerThread

# Define the parameter scenarios to test.
# First, include the full default configuration (will be run once)
scenarios = []
scenarios.append({
    "category": "default",
    "label": "default",
    "params": {
        "actors": DEFAULT_ACTORS,
        "iterations": DEFAULT_ITERATIONS,
        "invocations": DEFAULT_INVOCATIONS,
        "threads": DEFAULT_THREADS
    }
})

# Actors: only one non-default scenario: 3+3 (default for others)
scenarios.append({
    "category": "actors",
    "label": "3-3",
    "params": {
        "actors": (3, 3),
        "iterations": DEFAULT_ITERATIONS,
        "invocations": DEFAULT_INVOCATIONS,
        "threads": DEFAULT_THREADS
    }
})

# Iterations: non-default values (excluding default 50)
for it in [1, 10, 25, 100, 200]:
    scenarios.append({
        "category": "iterations",
        "label": str(it),
        "params": {
            "actors": DEFAULT_ACTORS,
            "iterations": it,
            "invocations": DEFAULT_INVOCATIONS,
            "threads": DEFAULT_THREADS
        }
    })

# Invocations: non-default values (excluding default 1000)
for inv in [1, 10, 100, 2500, 5000, 10000, 25000, 50000]:
    scenarios.append({
        "category": "invocations",
        "label": str(inv),
        "params": {
            "actors": DEFAULT_ACTORS,
            "iterations": DEFAULT_ITERATIONS,
            "invocations": inv,
            "threads": DEFAULT_THREADS
        }
    })

# Threads and actorsPerThread: all pairs except (3,3)
threads_options = []
for t in range(2, 6):
    for a in range(2, 6):
        if (t, a) != DEFAULT_THREADS:
            threads_options.append( (t, a) )
for (t, a) in threads_options:
    scenarios.append({
        "category": "threads",
        "label": f"{t}-{a}",
        "params": {
            "actors": DEFAULT_ACTORS,
            "iterations": DEFAULT_ITERATIONS,
            "invocations": DEFAULT_INVOCATIONS,
            "threads": (t, a)
        }
    })

# Function to update Kotlin test files with given parameter values
def update_test_files(root_dir, params):
    # Replacements that work for single-task files (method calls)
    common_replacements = {
        r"\.actorsBefore\(\s*\d+\s*\)": f".actorsBefore({params['actors'][0]})",
        r"\.actorsAfter\(\s*\d+\s*\)": f".actorsAfter({params['actors'][1]})",
        r"\.iterations\(\s*\d+\s*\)": f".iterations({params['iterations']})",
        r"\.invocationsPerIteration\(\s*\d+\s*\)": f".invocationsPerIteration({params['invocations']})",
        r"\.threads\(\s*\d+\s*\)": f".threads({params['threads'][0]})",
        r"\.actorsPerThread\(\s*\d+\s*\)": f".actorsPerThread({params['threads'][1]})"
    }

    # Extra replacements for template tests (TestBase constructor defaults)
    template_replacements = {
        r"scenarios:\s*Int\s*=\s*\d+": f"scenarios: Int = {params['iterations']}",
        r"threads:\s*Int\s*=\s*\d+": f"threads: Int = {params['threads'][0]}",
        r"actorsBefore:\s*Int\s*=\s*\d+": f"actorsBefore: Int = {params['actors'][0]}"
    }

    for subdir, _, files in os.walk(root_dir):
        for file in files:
            if file.endswith(".kt"):
                file_path = os.path.join(subdir, file)
                with open(file_path, "r") as f:
                    content = f.read()
                # Apply common replacements to all Kotlin test files.
                for pattern, replacement in common_replacements.items():
                    content = re.sub(pattern, replacement, content)
                # If the file contains TestBase, apply additional template-specific replacements.
                if "abstract class TestBase(" in content:
                    for pattern, replacement in template_replacements.items():
                        content = re.sub(pattern, replacement, content)
                with open(file_path, "w") as f:
                    f.write(content)

def run_scenario(scenario):
    cat = scenario["category"]
    label = scenario["label"]
    params = scenario["params"]

    # Determine result folder name. (For default, we simply use "default".)
    results_folder = os.path.join(RESULTS_BASE_DIR, f"{cat}_{label}")
    if os.path.exists(results_folder):
        print(f"Results folder {results_folder} exists. Skipping scenario {cat} {label}.")
        return

    # Create a temporary copy of the projects folder
    temp_projects_dir = os.path.join(TEMP_BASE_DIR, f"{cat}_{label}")
    if os.path.exists(temp_projects_dir):
        shutil.rmtree(temp_projects_dir)
    print(f"Copying projects to {temp_projects_dir} ...")
    shutil.copytree(ORIG_PROJECTS_PATH, temp_projects_dir)

    # Update all Kotlin test files in this temporary copy
    print(f"Updating test files in {temp_projects_dir} with parameters: {params}")
    update_test_files(temp_projects_dir, params)

    # Build command to call the modified test runner.
    # Pass the temporary projects directory and the target results folder.
    cmd = [
        sys.executable, TEST_RUNNER_SCRIPT,
        "--projects_path", temp_projects_dir,
        "--results_path", results_folder
    ]
    # Add any other arguments as needed (e.g., tasks, testing_method)
    print(f"Running test runner for scenario {cat} {label} ...")
    ret = subprocess.run(cmd)
    if ret.returncode != 0:
        print(f"Test runner failed for scenario {cat} {label}.")
    else:
        print(f"Scenario {cat} {label} finished successfully.")
    # Optionally, you might want to remove the temporary copy if no longer needed.
    # shutil.rmtree(temp_projects_dir)

def main():
    os.makedirs(TEMP_BASE_DIR, exist_ok=True)
    os.makedirs(RESULTS_BASE_DIR, exist_ok=True)
    for scenario in scenarios:
        run_scenario(scenario)

if __name__ == "__main__":
    main()
