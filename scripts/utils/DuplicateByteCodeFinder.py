import os
import shutil
import subprocess
import hashlib
import time

'''
This script is used to find duplicate bytecode in the dataset. It compiles the Kotlin files in the dataset and
obfuscates the bytecode of the compiled classes. The script then extracts the bytecode of the compiled classes and
compares them to find duplicates. To run this script properly, you'll need a project, in which files would be copied
and compiled. The project should have a Gradle build script and all interfaces needed in corresponding folders
(day1 to day4), as well as a test directory with all testing members definitions. Also, you'll need a ProGuard tool
installed locally.
'''

dataset_path = ''  # Specify a path to the dataset
project_path = ''  # Specify a path to the project that will be compiled
compilation_failed_file = 'compilationFailed.txt'  # File to store compilation errors for further analysis
duplicates_file = 'duplicates.txt'  # File to store results as pairs of files with duplicate bytecode

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
    proguard_path = ''  # Specify the path to the ProGuard executable script
    # Input and output directories for ProGuard
    input_dir = os.path.join(project_path, 'build', 'classes', 'kotlin', 'main')
    output_jar = os.path.join(project_path, 'build', 'obfuscated.jar')

    compile_result = subprocess.run(['./gradlew', 'clean', 'build'], cwd=project_path, capture_output=True, text=True,
                                    env=env, shell=True)
    if compile_result.returncode != 0:
        return False, compile_result.stderr

    # ProGuard command to obfuscate the bytecode of the compiled project classes
    proguard_command = [
        proguard_path,
        f'-injars {input_dir}',
        f'-outjars {output_jar}',
        f'-libraryjars {java_home}/jmods/java.base.jmod',
        '-optimizationpasses 5',
        '-overloadaggressively',
        '-repackageclasses \'\'',
        '-allowaccessmodification',
        '-dontusemixedcaseclassnames',
        '-keep public class * { public static void main(java.lang.String[]); }',
        '-dontskipnonpubliclibraryclasses',
        '-dontskipnonpubliclibraryclassmembers',
        '-dontnote',
        '-dontwarn'
    ]

    proguard_result = subprocess.run(proguard_command, capture_output=True, text=True, env=env)

    if proguard_result.returncode != 0:
        return False, proguard_result.stderr

    return True, "Compilation and obfuscation successful"


def extract_bytecode(class_file):
    with open(class_file, 'rb') as f:
        return hashlib.md5(f.read()).hexdigest()


def get_file_base_name(file_name):
    # Split the file name by the first "-" sign and return the first part
    return file_name.split("-", 1)[0]


def package_from_content(content):
    if "package day1" in content:
        return 'day1'
    elif "package day2" in content:
        return 'day2'
    elif "package day3" in content:
        return 'day3'
    elif "package day4" in content:
        return 'day4'
    else:
        return 'singleTasks'


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
                    package = package_from_content(content)
                dest_dir = os.path.join(project_path, 'src', package)
                os.makedirs(dest_dir, exist_ok=True)
                dest_file = os.path.join(dest_dir, new_file_name)

                shutil.copy(file_path, dest_file)
                compiled, error = compile_project()
                if not compiled:
                    with open(compilation_failed_file, 'a') as cf:
                        cf.write(f'{file_path}: {error}\n')
                    os.remove(dest_file)
                    continue

                class_file = dest_file.replace('.kt', '.class').replace('./src/', './build/classes/kotlin/main/')
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
