import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
import re
import time

# Paths
DATASET_PATH = "data/clusteredStudentSolutions/HDBSCAN/results"
TARGET_TEMPLATE_PATH = "template-project"
TEST_RESULTS_PATH = "./test-results"
JAVA_HOME = "/Users/Dmitrii.Kotov/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home"

# Ensure test results directory exists
os.makedirs(TEST_RESULTS_PATH, exist_ok=True)

# Mapping of source file name to test file name
TEST_MAPPING = {
    "AtomicArray": "AtomicArrayTest.kt",
    "AtomicArrayNoAba": "AtomicArrayNoAbaTest.kt",
    "AtomicArrayWithCAS2": "AtomicArrayTests.kt",
    "AtomicArrayWithCAS2AndImplementedDCSS": "AtomicArrayTests.kt",
    "AtomicArrayWithCAS2Simplified": "AtomicArrayTests.kt",
    "AtomicArrayWithCAS2SingleWriter": "AtomicArrayTests.kt",
    "AtomicArrayWithDCSS": "AtomicArrayTests.kt",
    "AtomicArrayWithSingleWriterCas2AndCasSimplified": None,
    "AtomicCounterArray": "AtomicCounterArrayTest.kt",
    "BoundedQueue": None,
    "CoarseGrainedBank": "BankTests.kt",
    "ConcurrentHashTable": "HashMapTests.kt",
    "ConcurrentHashTableWithoutResize": "HashMapTests.kt",
    "DynamicArray": "DynamicArrayTest.kt",
    "DynamicArraySimplified": "DynamicArraySimplifiedTest.kt",
    "FAABasedQueue": "QueueTests.kt",
    "FAABasedQueueSimplified": "QueueTests.kt",
    "FAAQueue": "FAAQueueTest.kt",
    "FCPriorityQueue": "FCPriorityQueueTest.kt",
    "FineGrainedBank": "BankTests.kt",
    "FlatCombiningQueue": "QueueTests.kt",
    "IntIntHashMap": "IntIntHashMapTest.kt",
    "LinkedListSet": "LinkedListSetTest.kt",
    "MichaelScottQueueWithConstantTimeRemove": None,
    "MichaelScottQueueWithLinearTimeRemove": None,
    "MSQueue": "MSQueueTest.kt",
    "MSQueueWithConstantTimeRemove": "QueueTests.kt",
    "MSQueueWithLinearTimeNonParallelRemove": "QueueTests.kt",
    "MSQueueWithLinearTimeRemove": "QueueTests.kt",
    "MSQueueWithOnlyLogicalRemove": "QueueTests.kt",
    "ShardedCounter": "ShardedCounterTest.kt",
    "SingleWriterHashTable": "HashMapTests.kt",
    "SkipList": "SkipListTest.kt",
    "SynchronousQueue": "SynchronousQueueTest.kt",
    "TreiberStack": None,  # Depends on package
    "TreiberStackWithElimination": "StackTests.kt"
}

# Function to determine the corresponding target folder for each file
def determine_target_folder(task_name, package_name):
    if package_name:
        return os.path.join(TARGET_TEMPLATE_PATH, "src", package_name)
    else:
        return os.path.join(TARGET_TEMPLATE_PATH, "src", "singleTasks")

# Function to determine the test file name
def determine_test_file(task_name, package_name):
    if task_name == "TreiberStack":
        return "TreiberStackTest.kt" if package_name == "singleTasks" else "StackTests.kt"
    return TEST_MAPPING.get(task_name, None)

# Function to scan the file for the package statement
def get_package_name(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
        match = re.search(r'package (day\d)', content)
        if match:
            return match.group(1)
    return "singleTasks"

# Function to copy solution file to the target directory
def copy_solution_file(solution_file, target_folder):
    os.makedirs(target_folder, exist_ok=True)
    dest_file = os.path.join(target_folder, os.path.basename(solution_file))
    shutil.copy(solution_file, dest_file)
    return dest_file

# Function to run gradle build and test, and save results to an XML file
def run_gradle_test(task_name, solution_file):
    # Determine the package name and target folder
    package_name = get_package_name(solution_file)
    target_folder = determine_target_folder(task_name, package_name)

    # Copy solution file to the target directory
    dummy_file = os.path.join(target_folder, f"{task_name}.kt")
    copied_file = copy_solution_file(solution_file, target_folder)

    # Backup the dummy file and replace it temporarily
    if os.path.exists(dummy_file):
        shutil.move(dummy_file, f"{dummy_file}.bak")
    shutil.move(copied_file, dummy_file)

    # Run Gradle build and test
    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME

    try:
        result = subprocess.run(["./gradlew", "clean", "test"], cwd=TARGET_TEMPLATE_PATH, capture_output=True, text=True, timeout=300, env=env)
        test_output = result.stdout + "\n" + result.stderr
    except subprocess.TimeoutExpired:
        test_output = "Test execution timed out."
    except Exception as e:
        test_output = f"An error occurred: {str(e)}"

    # Restore the original dummy file and remove the copied file
    if os.path.exists(dummy_file):
        os.remove(dummy_file)
    if os.path.exists(f"{dummy_file}.bak"):
        shutil.move(f"{dummy_file}.bak", dummy_file)

    # Parse the test output using LincheckReport and save results to an XML file
    result_file_path = os.path.join(TEST_RESULTS_PATH, f"{task_name}_results.xml")
    root = ET.Element("testResults")
    ET.SubElement(root, "taskName").text = task_name
    ET.SubElement(root, "solutionFile").text = os.path.basename(solution_file)
    if TEST_MAPPING[task_name] is None:
        ET.SubElement(root, "output").text = "No corresponding test found for this file."
    else:
        failure_info = {
            'class_name': task_name,
            'testcase_name': os.path.basename(solution_file),
            'exception_type': 'Exception',  # Assuming a generic exception type for now
            'terminal_message': test_output
        }
        lincheck_report = LincheckReport(failure_info)
        ET.SubElement(root, "output").text = lincheck_report.terminal_message  # Using the parsed terminal message
    tree = ET.ElementTree(root)
    tree.write(result_file_path)

# Start time
overall_start_time = time.time()

# Process each folder and file in the dataset
for task_folder in os.listdir(DATASET_PATH):
    task_path = os.path.join(DATASET_PATH, task_folder)
    if os.path.isdir(task_path):
        for solution_file in os.listdir(task_path):
            if solution_file.endswith(".kt"):
                solution_path = os.path.join(task_path, solution_file)
                run_gradle_test(task_folder, solution_path)
        print(f"Finished processing folder: {task_folder}")

# End time
overall_end_time = time.time()

# Calculate and print the total time taken
total_time = overall_end_time - overall_start_time
print(f"Total time taken to process all folders: {total_time:.2f} seconds")
