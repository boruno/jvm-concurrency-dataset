#!/usr/bin/env python3
import os
import shutil
import subprocess
import re
import time
import argparse
from enum import Enum

# The following enums remain as before
class TestResult(Enum):
    CORRECT = "No bugs found"
    BUG_FOUND = "Bugs found"
    TEST_ERROR = "Test is not working"
    END_ON_TIMEOUT = "Timeout"

class TestErrorType(Enum):
    COMPILATION_ERROR = "Compilation error"
    LINCHECK_BUG = "Tool bug (Lincheck bug)"
    OTHER_ERROR = "Other error"

class BugType(Enum):
    NON_LINEARIZABILITY = "Non-linearizability (consistency violation)"
    OBSTRUCTION_FREEDOM_VIOLATION = "Obstruction-freedom violation (non-blocking violation)"
    EXECUTION_HUNG = "Hung (deadlock/livelock)"
    INVARIANT_VIOLATION = "Data structure invariant/validation violation"
    UNEXPECTED_EXCEPTION = "Unexpected exceptions"
    OTHER_ERROR = "Other error"

# Parse command line arguments
parser = argparse.ArgumentParser(description="Run tests for dataset with configurable paths")
parser.add_argument("--projects_path", type=str, default="../../template-projects",
                    help="Path to the projects directory (will be the temporary copy)")
parser.add_argument("--results_path", type=str, default="./test-results-2.39",
                    help="Path to the test results directory")
parser.add_argument("-tasks", nargs="*", default=["all_tests"],
                    help="Space-separated list of task names, or 'all_tests' to run all tasks")
parser.add_argument("-testing_method", choices=["lincheck_mc", "lincheck_stress", "all_methods"],
                    default="all_methods", help="Testing method to use")
args = parser.parse_args()

# Use provided paths
PROJECTS_PATH = args.projects_path
TEST_RESULTS_PATH = args.results_path
# TEMP_DIR_PATH is used to temporarily store a dummy file copy inside the project
TEMP_DIR_PATH = os.path.join(PROJECTS_PATH, "temp")

# Other paths remain the same
DATASET_PATH = "../../data/clusteredStudentSolutions/HDBSCAN/results"
JAVA_HOME = ""

# Folders to run (as in your original script)
RUN_FOLDERS = [
    "MSQueueWithConstantTimeRemove",
    "FCPriorityQueue",
    "FAABasedQueue",
    "FlatCombiningQueue",
    "FineGrainedBank",
    "MSQueueWithOnlyLogicalRemove",
    "ConcurrentHashTableWithoutResize",
    "AtomicArrayNoAba",
    "AtomicArrayWithCAS2",
    "AtomicArrayWithCAS2Simplified",
    "TreiberStack",
    "MSQueue",
    "FAABasedQueueSimplified",
    "IntIntHashMap",
    "AtomicCounterArray",
    "AtomicArrayWithCAS2SingleWriter",
    "MSQueueWithLinearTimeNonParallelRemove",
    "SkipList",
    "AtomicArray",
    "AtomicArrayWithCAS2AndImplementedDCSS",
    "SynchronousQueue",
    "DynamicArraySimplified",
    "TreiberStackWithElimination",
    "DynamicArray",
    "SingleWriterHashTable",
    "CoarseGrainedBank",
    "AtomicArrayWithDCSS",
    "ConcurrentHashTable",
    "MSQueueWithLinearTimeRemove",
    "LinkedListSet",
    "ShardedCounter",
    "FAAQueue"
]

# Mapping dictionaries remain as provided
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
    "SynchronousQueue": "synchronous-queue",
    "TreiberStack": "treiber-stack",
    "TreiberStackWithElimination": "stack-elimination"
}

SINGLE_TASK_TEST_CLASS = {
    "AtomicArray": "AtomicArrayTest",
    "AtomicArrayNoAba": "AtomicArrayNoAbaTest",
    "DynamicArray": "DynamicArrayTest",
    "FAAQueue": "FAAQueueTest",
    "FCPriorityQueue": "FCPriorityQueueTest",
    "FineGrainedBank": "FineGrainedBankTest",
    "IntIntHashMap": "IntIntHashMapTest",
    "LinkedListSet": "LinkedListSetTest",
    "MSQueue": "MSQueueTest",
    "ShardedCounter": "ShardedCounterTest",
    "SkipList": "LinkedListTest",
    "SynchronousQueue": "SynchronousQueueTest",
    "TreiberStack": "TreiberStackTest",
    "TreiberStackWithElimination": "TreiberStackWithEliminationTest"
}

TEMPLATE_PROJECT_TEST_CLASS = {
    "AtomicArrayWithCAS2": "AtomicArrayWithCAS2Test",
    "AtomicArrayWithCAS2AndImplementedDCSS": "AtomicArrayWithCAS2AndImplementedDCSSTest",
    "AtomicArrayWithCAS2Simplified": "AtomicArrayWithCAS2SimplifiedTest",
    "AtomicArrayWithCAS2SingleWriter": "AtomicArrayWithCAS2SingleWriterTest",
    "AtomicArrayWithDCSS": "AtomicArrayWithDCSSTest",
    "AtomicCounterArray": "AtomicCounterArrayTest",
    "CoarseGrainedBank": "CoarseGrainedBankTest",
    "ConcurrentHashTable": "ConcurrentHashTableTest",
    "ConcurrentHashTableWithoutResize": "ConcurrentHashTableWithoutResizeTest",
    "DynamicArray": "DynamicArrayTest",
    "DynamicArraySimplified": "DynamicArraySimplifiedTest",
    "FAABasedQueue": "FAABasedQueueTest",
    "FAABasedQueueSimplified": "FAABasedQueueSimplifiedTest",
    "FineGrainedBank": "FineGrainedBankTest",
    "FlatCombiningQueue": "FlatCombiningQueueTest",
    "IntIntHashMap": "IntIntHashMapTest",
    "MSQueue": "MSQueueTest",
    "MSQueueWithConstantTimeRemove": "MSQueueWithConstantTimeRemoveTest",
    "MSQueueWithLinearTimeNonParallelRemove": "MSQueueWithLinearTimeNonParallelRemoveTest",
    "MSQueueWithLinearTimeRemove": "MSQueueWithLinearTimeRemoveTest",
    "MSQueueWithOnlyLogicalRemove": "MSQueueWithOnlyLogicalRemoveTest",
    "SingleWriterHashTable": "SingleWriterHashTableTest",
    "TreiberStack": "TreiberStackTest",
    "TreiberStackWithElimination": "TreiberStackWithEliminationTest",
}

def run_gradle_test_with_timeout(project_path, test_class, test_method, timeout=300):
    start_time = time.time()
    try:
        gradle_command = [
            "./gradlew", "test",
            f"--tests={test_class}.{test_method}",
            "--warning-mode=none", "--console=plain"
        ]
        result = subprocess.run(
            gradle_command,
            cwd=project_path,
            capture_output=True,
            text=True,
            check=True,
            timeout=timeout
        )
        end_time = time.time()
        duration = end_time - start_time
        return result.stdout, None, duration
    except subprocess.TimeoutExpired:
        subprocess.run(["pkill", "-f", f"./gradlew test --tests={test_class}.{test_method}"], cwd=project_path)
        end_time = time.time()
        duration = end_time - start_time
        return None, f"Execution ended on timeout: {timeout} seconds.", duration
    except subprocess.CalledProcessError as e:
        end_time = time.time()
        duration = end_time - start_time
        error_message = e.stdout + e.stderr
        task_line_match = re.search(r"> Task :test", error_message)
        if task_line_match:
            start_index = task_line_match.start()
            end_index = error_message.find("FAILURE: Build failed with an exception.", start_index)
            if end_index != -1:
                error_message = error_message[start_index:end_index].strip()
        return None, error_message, duration

def extract_lincheck_output(output):
    if output is None:
        return TestResult.CORRECT.value
    if "Timeout:" in output:
        return TestResult.END_ON_TIMEOUT.value
    lincheck_error_messages = {
        "= The execution failed with an unexpected exception =": BugType.UNEXPECTED_EXCEPTION,
        "= The execution has hung =": BugType.EXECUTION_HUNG,
        "= The execution has hung, see the thread dump =": BugType.EXECUTION_HUNG,
        "= Invalid execution results =": BugType.NON_LINEARIZABILITY,
        "= Validation function": BugType.INVARIANT_VIOLATION,
        "The algorithm should be non-blocking": BugType.OBSTRUCTION_FREEDOM_VIOLATION,
    }
    for message, bug_type in lincheck_error_messages.items():
        lincheck_start = output.find(message)
        if lincheck_start != -1:
            lincheck_end = output.find("Finished generating", lincheck_start)
            if lincheck_end == -1:
                lincheck_end = len(output)
            return f"{TestResult.BUG_FOUND.value}: {bug_type.value}\n{output[lincheck_start:lincheck_end].strip()}"
    if "Wow! You've caught a bug in Lincheck." in output:
        return f"{TestResult.TEST_ERROR.value}: {TestErrorType.LINCHECK_BUG.value}"
    return f"{TestResult.TEST_ERROR.value}: {TestErrorType.OTHER_ERROR.value}, see error logs:\n {output}"

def process_single_task(file_path, project_name, log_file, task_name, test_methods):
    project_path = os.path.join(PROJECTS_PATH, project_name)
    src_path = os.path.join(project_path, "src")
    dest_file = os.path.join(src_path, task_name + ".kt")
    temp_file = os.path.join(TEMP_DIR_PATH, "tmp.txt")
    shutil.copyfile(dest_file, temp_file)
    with open(file_path, "r") as test_file:
        test_content = test_file.read()
    with open(temp_file, "r") as dummy_file:
        dummy_content = dummy_file.read()
        package_match = re.search(r"^package\s+\S+", dummy_content, re.MULTILINE)
        if package_match:
            test_content = re.sub(r"^//\s*(package\s+\S+)", r"\1", test_content, flags=re.MULTILINE)
    with open(dest_file, "w") as dest:
        dest.write(test_content)
    test_class = SINGLE_TASK_TEST_CLASS[task_name]
    with open(log_file, "a") as log:
        log.write(f"Processing file: {os.path.basename(file_path)}\n")
        for test_method in test_methods:
            output, error, duration = run_gradle_test_with_timeout(project_path, test_class, test_method)
            lincheck_output = extract_lincheck_output(error)
            log.write(f"  Test method: {test_method}\n  Testing time: {duration:.2f} seconds\n")
            log.write(f"{lincheck_output}\n\n")
        log.write("\n")
    shutil.copyfile(temp_file, dest_file)

def process_template_task(file_path, test_file, log_file, task_name, test_methods):
    project_path = os.path.join(PROJECTS_PATH, "template")
    src_path = os.path.join(project_path, "src")
    dest_file = os.path.join(src_path, task_name + ".kt")
    temp_file = os.path.join(TEMP_DIR_PATH, "tmp.txt")
    shutil.copyfile(dest_file, temp_file)
    shutil.copyfile(file_path, dest_file)
    test_class = test_file
    with open(log_file, "a") as log:
        log.write(f"Processing file: {os.path.basename(file_path)}\n")
        for test_method in test_methods:
            output, error, duration = run_gradle_test_with_timeout(project_path, test_class, test_method)
            lincheck_output = extract_lincheck_output(error)
            log.write(f"  Test method: {test_method}\n  Testing time: {duration:.2f} seconds\n")
            log.write(f"{lincheck_output}\n\n")
        log.write("\n")
    shutil.copyfile(temp_file, dest_file)

def main():
    # Ensure test results directory exists
    os.makedirs(TEST_RESULTS_PATH, exist_ok=True)
    task_list = args.tasks if "all_tests" not in args.tasks else RUN_FOLDERS
    test_methods = ["modelCheckingTest", "stressTest"] if args.testing_method == "all_methods" else [args.testing_method.replace("lincheck_", "")]
    for root, dirs, _ in os.walk(DATASET_PATH):
        if root == DATASET_PATH:
            dirs[:] = [d for d in dirs if d in task_list]
        if not os.path.exists(TEMP_DIR_PATH):
            os.mkdir(TEMP_DIR_PATH)
        for folder in dirs:
            folder_path = os.path.join(root, folder)
            result_folder = os.path.join(TEST_RESULTS_PATH, folder)
            os.makedirs(result_folder, exist_ok=True)
            log_file = os.path.join(result_folder, "results.txt")
            open(log_file, "w").close()
            start_time = time.time()
            for file in os.listdir(folder_path):
                if file.endswith(".kt"):
                    file_path = os.path.join(folder_path, file)
                    task_name = file.split("-")[0]
                    with open(file_path, "r") as f:
                        content = f.read()
                    if re.search(r"//\s*package day\d+", content):
                        test_file = TEMPLATE_PROJECT_TEST_CLASS.get(task_name)
                        if test_file:
                            process_template_task(file_path, test_file, log_file, task_name, test_methods)
                    else:
                        project_name = SINGLE_TASK_PROJECTS.get(task_name)
                        if project_name:
                            process_single_task(file_path, project_name, log_file, task_name, test_methods)
            end_time = time.time()
            print(f"Processed folder {folder_path} in {end_time - start_time:.2f} seconds.")
        shutil.rmtree(TEMP_DIR_PATH)

if __name__ == "__main__":
    main()
