import os
import shutil
import subprocess
import hashlib
import time

dataset_path = '.'  # Path to the dataset
project_path = '.'  # Path to the project that will be compiled
compilation_failed_file = 'compilationFailed.txt'
duplicates_file = 'duplicates.txt'
compilation_successful_file = 'compilationSuccessful.txt'

directories = [os.path.join(dataset_path, d) for d in os.listdir(dataset_path) if
               os.path.isdir(os.path.join(dataset_path, d))]
bytecode_map = {}


def copy_file_to_package(src_file, package):
    dest_dir = os.path.join(project_path, 'src', package)
    os.makedirs(dest_dir, exist_ok=True)
    dest_file = os.path.join(dest_dir, os.path.basename(src_file))
    shutil.copy(src_file, dest_file)
    return dest_file


def compile_project():
    env = os.environ.copy()
    try:
        result = subprocess.run(['./gradlew', 'clean', 'build', '--info'], cwd=project_path, capture_output=True,
                                text=True, timeout=10, env=env, shell=True)
        return result.returncode == 0, result.stderr
    except subprocess.TimeoutExpired:
        return False, "Build command timed out"


def extract_bytecode(class_file):
    with open(class_file, 'rb') as f:
        return hashlib.md5(f.read()).hexdigest()


def get_file_base_name(file_name):
    # Split the file name by the first "-" sign and return the first part
    return file_name.split("-", 1)[0]


def process_files(directory):
    start_time = time.time()
    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith('.kt'):
                base_name = get_file_base_name(file)
                new_file_name = f"{base_name}.kt"
                file_path = os.path.join(root, file)
                with open(file_path, 'r') as f:
                    content = f.read()
                    if "package day1" in content:
                        package = 'day1'
                    elif "package day2" in content:
                        package = 'day2'
                    elif "package day3" in content:
                        package = 'day3'
                    elif "package day4" in content:
                        package = 'day4'
                    else:
                        package = 'singleTasks'

                dest_dir = os.path.join(project_path, 'src', package)
                os.makedirs(dest_dir, exist_ok=True)
                dest_file = os.path.join(dest_dir, new_file_name)

                shutil.copy(file_path, dest_file)
                compiled, error = compile_project()
                if compiled:
                    with open(compilation_successful_file, 'a') as csf:
                        csf.write(f'{file_path}\n')
                else:
                    with open(compilation_failed_file, 'a') as cf:
                        cf.write(f'{file_path}: {error}\n')
                    os.remove(dest_file)
                    continue

                class_file = dest_file.replace('.kt', '.class').replace('./src/singleTasks',
                                                                        './build/classes/kotlin/main')
                if os.path.exists(class_file):
                    bytecode = extract_bytecode(class_file)
                    if bytecode in bytecode_map:
                        with open(duplicates_file, 'a') as df:
                            df.write(f'{bytecode_map[bytecode]} and {file_path}\n')
                    else:
                        bytecode_map[bytecode] = file_path
                os.remove(dest_file)

    elapsed_time = time.time() - start_time
    print(f'folder {os.path.basename(directory)} finished in {elapsed_time:.2f} seconds')


def main():
    for directory in directories:
        process_files(directory)


if __name__ == '__main__':
    main()
