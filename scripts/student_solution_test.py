import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
from lincheck_report_parser import LincheckReport
import re
import time

# Paths
DATASET_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "../data/clusteredStudentSolutions/HDBSCAN/results"))
TARGET_TEMPLATE_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "../template-projects"))
TEST_RESULTS_PATH = "./test-results"
JAVA_HOME = ""

# Ensure test results directory exists
os.makedirs(TEST_RESULTS_PATH, exist_ok=True)

# Mapping of single-task file names to project names
SINGLE_TASK_PROJECTS = {
    "AtomicArray": "casn",
    "AtomicArrayNoAba": "casn-without-aba",
    "DynamicArray": "dynamic-array",
    "FAAQueue": "faa-queue",
    "FCPriorityQueue": "fc-priority-queue",
    "FineGrainedBank": "fine-grained-bank",
    "IntIntHashMap": "hash-map",
    "LinkedListSet": "linked-list-set",
    "MSQueue": "msqueue",
    "ShardedCounter": "sharded-counter",
    "SkipList": "skip-list",
    "SynchronousQueue": "syncronous-queue",
    "TreiberStack": "treiber-stack",
    "TreiberStackWithElimination": "stack-elimination"
}

# Mapping of template-based file names to project names
TEMPLATE_PROJECTS = {
    "AtomicArrayWithCAS2": "template4",
    "AtomicArrayWithCAS2AndImplementedDCSS": "template4",
    "AtomicArrayWithCAS2Simplified": "template4",
    "AtomicArrayWithCAS2SingleWriter": "template4",
    "AtomicArrayWithDCSS": "template3",
    "AtomicCounterArray": "template1",
    "CoarseGrainedBank": "template4",
    "ConcurrentHashTable": "template4",
    "ConcurrentHashTableWithoutResize": "template4",
    "DynamicArraySimplified": "template1",
    "FAABasedQueue": "template4",
    "FAABasedQueueSimplified": "template4",
    "FlatCombiningQueue": "template4",
    "MSQueueWithConstantTimeRemove": "template4",
    "MSQueueWithLinearTimeNonParallelRemove": "template44",
    "MSQueueWithLinearTimeRemove": "template4",
    "MSQueueWithOnlyLogicalRemove": "template4",
    "SingleWriterHashTable": "template4"
}

# Function to scan the file for the package statement
def get_package_name(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
        match = re.search(r'package (day\d)', content)
        if match:
            return match.group(1)
    return None

# Function to process files in single-task projects
def process_single_task_file(file_name, solution_file):
    project_name = SINGLE_TASK_PROJECTS[file_name]
    project_path = os.path.join(PROJECTS_PATH, project_name)
    src_path = os.path.join(project_path, "src")
    dummy_file = os.path.join(src_path, f"{file_name}.kt")

    # Replace dummy file and run tests
    backup_file = f"{dummy_file}.bak"
    shutil.move(dummy_file, backup_file)
    shutil.copy(solution_file, dummy_file)

    try:
        run_gradle(project_path)
    finally:
        shutil.move(backup_file, dummy_file)

# Function to process files in template projects
def process_template_file(file_name, solution_file, package_name):
    project_name = TEMPLATE_PROJECTS[file_name]
    project_path = os.path.join(PROJECTS_PATH, project_name)
    src_path = os.path.join(project_path, "src", package_name)
    dummy_file = os.path.join(src_path, f"{file_name}.kt")

    # Replace dummy file and run tests
    backup_file = f"{dummy_file}.bak"
    shutil.move(dummy_file, backup_file)
    shutil.copy(solution_file, dummy_file)

    try:
        run_gradle(project_path)
    finally:
        shutil.move(backup_file, dummy_file)

# Function to run gradle build and test
def run_gradle(project_path):
    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME
    result = subprocess.run(["./gradlew", "clean", "test"], cwd=project_path, capture_output=True, text=True, env=env)
    return result.stdout + "\n" + result.stderr

# Start time
overall_start_time = time.time()

# Process each folder and file in the dataset
for task_folder in os.listdir(DATASET_PATH):
    task_path = os.path.join(DATASET_PATH, task_folder)
    if os.path.isdir(task_path):
        for solution_file in os.listdir(task_path):
            if solution_file.endswith(".kt"):
                solution_path = os.path.join(task_path, solution_file)
                package_name = get_package_name(solution_path)
                file_name = os.path.splitext(solution_file)[0].split('-')[0]

                if file_name in SINGLE_TASK_PROJECTS and not package_name:
                    process_single_task_file(file_name, solution_path)
                elif file_name in TEMPLATE_PROJECTS and package_name:
                    process_template_file(file_name, solution_path, package_name)

# End time
overall_end_time = time.time()

# Calculate and print the total time taken
total_time = overall_end_time - overall_start_time
print(f"Total time taken to process all folders: {total_time:.2f} seconds")

