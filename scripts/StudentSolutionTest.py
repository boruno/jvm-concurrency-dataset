import os
import shutil
import subprocess
import re
import time

# Paths
DATASET_PATH = "../data/clusteredStudentSolutions/HDBSCAN/results"
PROJECTS_PATH = "../template-projects"
TEST_RESULTS_PATH = "./test-results"
JAVA_HOME = ""

# Ensure test results directory exists
os.makedirs(TEST_RESULTS_PATH, exist_ok=True)

# Folders to skip
SKIP_FOLDERS = [
                # "MSQueueWithConstantTimeRemove",
                # "FCPriorityQueue",
                # "FAABasedQueue",
                # "FlatCombiningQueue",
                # "FineGrainedBank",
                # "MSQueueWithOnlyLogicalRemove",
                # "ConcurrentHashTableWithoutResize",
                # "AtomicArrayNoAba",
                # "AtomicArrayWithCAS2",
                # "AtomicArrayWithCAS2Simplified",
                # "TreiberStack",
                # "MSQueue",
                # "FAABasedQueueSimplified",
                # "IntIntHashMap",
                # "AtomicCounterArray",
                # "AtomicArrayWithCAS2SingleWriter",
                # "MSQueueWithLinearTimeNonParallelRemove",
                # "SkipList",
                # "AtomicArray",
                # "AtomicArrayWithCAS2AndImplementedDCSS",
                # "SynchronousQueue",
]

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
    "SynchronousQueue": "synchronous-queue",
    "TreiberStack": "treiber-stack",
    "TreiberStackWithElimination": "stack-elimination"
}

# Mapping of template-based file names to test files
TEMPLATE_PROJECT = {
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

def run_gradle_test_with_timeout(project_path, test_class, test_method, timeout=60):
    """Run a specific test method within a test class using Gradle, with a timeout."""
    try:
        # Build the Gradle command for the specific test method
        gradle_command = [
            "./gradlew", "test",
            f"--tests={test_class}.{test_method}",
            "--info", "--warning-mode", "none"
        ]

        # Run the command with a timeout
        result = subprocess.run(
            gradle_command,
            cwd=project_path,
            capture_output=True,
            text=True,
            check=True,
            timeout=timeout
        )
        return result.stdout, None  # Return test output on success

    except subprocess.TimeoutExpired:
        # Kill only the specific Gradle task if timeout occurs
        subprocess.run(
            ["pkill", "-f", f"./gradlew test --tests={test_class}.{test_method}"],
            cwd=project_path
        )
        return None, f"Timeout: The test '{test_class}.{test_method}' exceeded {timeout} seconds and was terminated."

    except subprocess.CalledProcessError as e:
        # Capture error if the Gradle task fails
        return None, e.stdout + e.stderr



def extract_lincheck_output(output):
    """Extract Lincheck-specific test results from the output."""
    lincheck_error_messages = [
        "= The execution failed with an unexpected exception =",
        "= The execution has hung =",
        "= The execution has hung, see the thread dump =",
        "= Invalid execution results =",
        "= Validation function",
        "The algorithm should be non-blocking"
    ]

    for message in lincheck_error_messages:
        lincheck_start = output.find(message)
        if lincheck_start != -1:
            return output[lincheck_start:].strip()
    return "No Lincheck results found."


def process_single_task(file_path, project_name, log_file):
    """Process a single task by running both modelCheckingTest and stressTest, logging results in a .txt file."""
    project_path = os.path.join(PROJECTS_PATH, project_name)
    src_path = os.path.join(project_path, "src")

    dummy_file = next(iter(os.listdir(src_path)))  # Get the single dummy file
    shutil.copy(file_path, os.path.join(src_path, dummy_file))

    # Run tests: modelCheckingTest and stressTest
    test_class = os.path.splitext(os.path.basename(file_path))[
        0]  # Assume test class matches the file name without extension

    with open(log_file, "a") as log:
        log.write(f"Processing file: {os.path.basename(file_path)}\n")

        for test_method in ["modelCheckingTest", "stressTest"]:
            output, error = run_gradle_test_with_timeout(project_path, test_class, test_method)
            lincheck_output = None
            if error:
                lincheck_output = extract_lincheck_output(error)
            elif output:
                lincheck_output = extract_lincheck_output(output)

            if lincheck_output and lincheck_output != "No Lincheck results found.":
                log.write(f"  Test method: {test_class}.{test_method}\n")
                log.write(f"{lincheck_output}\n")

        log.write("\n")  # Add spacing between files

    # Restore the dummy file
    shutil.copy(os.path.join(src_path, dummy_file), file_path)


def process_template_task(file_path, test_file, log_file):
    """Process a template task, running both modelCheckingTest and stressTest, logging results in a .txt file."""
    project_path = os.path.join(PROJECTS_PATH, "template")
    src_path = os.path.join(project_path, "src")

    shutil.copy(file_path, src_path)

    test_class = test_file  # For template tasks, the test class corresponds to test_file

    with open(log_file, "a") as log:
        log.write(f"Processing file: {os.path.basename(file_path)}\n")

        for test_method in ["modelCheckingTest", "stressTest"]:
            output, error = run_gradle_test_with_timeout(project_path, test_class, test_method)

            log.write(f"  Test method: {test_class}.{test_method}\n")
            lincheck_output = None
            if error:
                lincheck_output = extract_lincheck_output(error)
            elif output:
                lincheck_output = extract_lincheck_output(output)

            if lincheck_output and lincheck_output != "No Lincheck results found.":
                log.write(f"  Test method: {test_class}.{test_method}\n")
                log.write(f"    Lincheck Output:\n{lincheck_output}\n")

        log.write("\n")  # Add spacing between files

    # Restore the dummy file
    os.remove(os.path.join(src_path, os.path.basename(file_path)))


def main():
    """Main function to process the dataset."""
    for root, dirs, _ in os.walk(DATASET_PATH):
        if root == DATASET_PATH:
            dirs[:] = [d for d in dirs if d not in SKIP_FOLDERS and d != os.path.basename(
                PROJECTS_PATH)]  # Exclude "template-projects" and skipped folders

        for folder in dirs:
            folder_path = os.path.join(root, folder)
            result_folder = os.path.join(TEST_RESULTS_PATH, folder)
            os.makedirs(result_folder, exist_ok=True)
            log_file = os.path.join(result_folder, "results.txt")

            # Clear the log file for a new iteration
            open(log_file, "w").close()

            start_time = time.time()
            for file in os.listdir(folder_path):
                if file.endswith(".kt"):
                    file_path = os.path.join(folder_path, file)
                    task_name = file.split("-")[0]  # Extract task name before the first hyphen

                    # Determine task type
                    with open(file_path, "r") as f:
                        content = f.read()

                    if re.search(r"//\s*package day\d+", content):
                        # Template-based task
                        test_file = TEMPLATE_PROJECT.get(task_name)
                        if test_file:
                            process_template_task(file_path, test_file, log_file)
                    else:
                        # Single task
                        project_name = SINGLE_TASK_PROJECTS.get(task_name)
                        if project_name:
                            process_single_task(file_path, project_name, log_file)

            end_time = time.time()
            print(f"Processed folder {folder_path} in {end_time - start_time:.2f} seconds.")

if __name__ == "__main__":
    main()
