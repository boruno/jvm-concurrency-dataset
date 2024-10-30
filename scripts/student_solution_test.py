import os
import shutil
import subprocess
import xml.etree.ElementTree as ET
import lincheck_report_parser

# Paths
DATASET_PATH = "/Users/Dmitrii.Kotov/repos/concurrency-tools/lincheck-incorrect-data-structures-dataset/data/clusteredStudentSolutions/HDBSCAN/results"
TARGET_TEMPLATE_PATH = "concurrency-intensive-bremen"
TEST_RESULTS_PATH = "initial_file_run_results"
JAVA_HOME = "/Users/Dmitrii.Kotov/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home"

# Ensure test results directory exists
os.makedirs(TEST_RESULTS_PATH, exist_ok=True)

# Function to determine the corresponding target folder for each file
def determine_target_folder(task_name):
    # Placeholder for folder mapping logic based on the notebook's algorithm
    # For demonstration purposes, assume the target folder is named after the task
    return os.path.join(TARGET_TEMPLATE_PATH, "src", task_name)

# Function to copy solution file to the target directory
def copy_solution_file(solution_file, target_folder):
    os.makedirs(target_folder, exist_ok=True)
    dest_file = os.path.join(target_folder, os.path.basename(solution_file))
    shutil.copy(solution_file, dest_file)
    return dest_file

# Function to run gradle build and test, and save results to an XML file
def run_gradle_test(task_name, solution_file):
    # Copy solution file to the target directory
    target_folder = determine_target_folder(task_name)
    copied_file = copy_solution_file(solution_file, target_folder)

    # Run Gradle build and test
    env = os.environ.copy()
    env["JAVA_HOME"] = JAVA_HOME

    try:
        result = subprocess.run(["./gradlew", "test"], cwd=TARGET_TEMPLATE_PATH, capture_output=True, text=True, timeout=300, env=env)
        test_output = result.stdout + "\n" + result.stderr
    except subprocess.TimeoutExpired:
        test_output = "Test execution timed out."
    except Exception as e:
        test_output = f"An error occurred: {str(e)}"

    # Parse the test output using LincheckReport and save results to an XML file
    failure_info = {
        'class_name': task_name,
        'testcase_name': os.path.basename(solution_file),
        'exception_type': 'Exception',  # Assuming a generic exception type for now
        'terminal_message': test_output
    }
    lincheck_report = lincheck_report_parser.LincheckReport(failure_info)

    result_file_path = os.path.join(TEST_RESULTS_PATH, f"{task_name}_results.xml")
    root = ET.Element("testResults")
    ET.SubElement(root, "taskName").text = task_name
    ET.SubElement(root, "solutionFile").text = os.path.basename(solution_file)
    ET.SubElement(root, "output").text = lincheck_report.terminal_message  # Using the parsed terminal message
    tree = ET.ElementTree(root)
    tree.write(result_file_path)

# Process each folder and file in the dataset
for task_folder in os.listdir(DATASET_PATH):
    task_path = os.path.join(DATASET_PATH, task_folder)
    if os.path.isdir(task_path):
        for solution_file in os.listdir(task_path):
            if solution_file.endswith(".kt"):
                solution_path = os.path.join(task_path, solution_file)
                run_gradle_test(task_folder, solution_path)
