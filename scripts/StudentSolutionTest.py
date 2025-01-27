import os
import shutil
import subprocess
import re
import time

# Paths
DATASET_PATH = "../data/clusteredStudentSolutions/HDBSCAN/results"
PROJECTS_PATH = "../template-projects"
TEMP_DIR_PATH = os.path.join(PROJECTS_PATH, "temp")
TEST_RESULTS_PATH = "./test-results"
JAVA_HOME = ""

# Ensure test results directory exists
os.makedirs(TEST_RESULTS_PATH, exist_ok=True)

# Folders to skip
SKIP_FOLDERS = [
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
    # "TreiberStack",
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

# Mapping of template-based file names to test files
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
    if output is None:
        return "No errors or Lincheck-specific results found."

    if "Timeout:" in output:
        return "Execution ended on timeout."

    lincheck_error_messages = [
        "= The execution failed with an unexpected exception =",
        "= The execution has hung =",
        "= The execution has hung, see the thread dump =",
        "= Invalid execution results =",
        "= Validation function",
        "The algorithm should be non-blocking",
        "Wow! You've caught a bug in Lincheck.",
    ]

    # Find the start of the relevant output
    lincheck_start = -1
    for message in lincheck_error_messages:
        lincheck_start = output.find(message)
        if lincheck_start != -1:
            break

    if lincheck_start == -1:
        return "No Lincheck results found."

    # Find the end of the relevant output
    lincheck_end = output.find("Finished generating", lincheck_start)
    if lincheck_end == -1:
        lincheck_end = len(output)  # If "Finished generating" is not found, take everything until the end

    # Return the trimmed output
    return output[lincheck_start:lincheck_end].strip()


def process_single_task(file_path, project_name, log_file, task_name):
    """Process a single task by running both modelCheckingTest and stressTest, logging results in a .txt file."""
    project_path = os.path.join(PROJECTS_PATH, project_name)
    src_path = os.path.join(project_path, "src")

    dest_file = os.path.join(src_path, task_name + ".kt")
    temp_file = os.path.join(TEMP_DIR_PATH, "tmp.txt")

    # Save the original dummy file content
    shutil.copyfile(dest_file, temp_file)

    # Copy the file to be tested into the destination
    with open(file_path, "r") as test_file:
        test_content = test_file.read()

    # Modify the test content to uncomment package declaration if needed
    with open(temp_file, "r") as dummy_file:
        dummy_content = dummy_file.read()
        package_match = re.search(r"^package\s+\S+", dummy_content, re.MULTILINE)
        if package_match:
            test_content = re.sub(r"^//\s*(package\s+\S+)", r"\1", test_content, flags=re.MULTILINE)

    with open(dest_file, "w") as dest:
        dest.write(test_content)

    # Run tests: modelCheckingTest and stressTest
    test_class = SINGLE_TASK_TEST_CLASS[task_name]

    with open(log_file, "a") as log:
        log.write(f"Processing file: {os.path.basename(file_path)}\n")

        for test_method in ["modelCheckingTest", "stressTest"]:
            output, error = run_gradle_test_with_timeout(project_path, test_class, test_method)
            lincheck_output = extract_lincheck_output(error)

            log.write(f"  Test method: {test_method}\n")
            log.write(f"{lincheck_output}\n\n")

        log.write("\n")  # Add spacing between files

    # Restore the original dummy file
    shutil.copyfile(temp_file, dest_file)


def process_template_task(file_path, test_file, log_file, task_name):
    """Process a template task, running both modelCheckingTest and stressTest, logging results in a .txt file."""
    project_path = os.path.join(PROJECTS_PATH, "template")
    src_path = os.path.join(project_path, "src")

    dest_file = os.path.join(src_path, task_name + ".kt")
    temp_file = os.path.join(TEMP_DIR_PATH, "tmp.txt")

    shutil.copyfile(dest_file, temp_file)
    shutil.copyfile(file_path, dest_file)

    test_class = test_file  # For template tasks, the test class corresponds to test_file

    with open(log_file, "a") as log:
        log.write(f"Processing file: {os.path.basename(file_path)}\n")

        for test_method in ["modelCheckingTest", "stressTest"]:
            output, error = run_gradle_test_with_timeout(project_path, test_class, test_method)

            lincheck_output = extract_lincheck_output(error)

            log.write(f"  Test method: {test_method}\n")
            log.write(f"{lincheck_output}\n\n")

        log.write("\n")  # Add spacing between files

    # Restore the dummy file
    shutil.copyfile(temp_file, dest_file)


def main():
    """Main function to process the dataset."""
    for root, dirs, _ in os.walk(DATASET_PATH):
        if root == DATASET_PATH:
            dirs[:] = [d for d in dirs if d not in SKIP_FOLDERS and d != os.path.basename(
                PROJECTS_PATH)]  # Exclude "template-projects" and skipped folders

        if not os.path.exists(TEMP_DIR_PATH):
            os.mkdir(TEMP_DIR_PATH)
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
                        test_file = TEMPLATE_PROJECT_TEST_CLASS.get(task_name)
                        if test_file:
                            process_template_task(file_path, test_file, log_file, task_name)
                    else:
                        # Single task
                        project_name = SINGLE_TASK_PROJECTS.get(task_name)
                        if project_name:
                            process_single_task(file_path, project_name, log_file, task_name)

            end_time = time.time()
            print(f"Processed folder {folder_path} in {end_time - start_time:.2f} seconds.")
        shutil.rmtree(TEMP_DIR_PATH)


if __name__ == "__main__":
    main()
