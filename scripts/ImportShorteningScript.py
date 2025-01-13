import os
import re

def shorten_imports_in_kotlin_files(directory):
    """
    Recursively shortens import statements in Kotlin files within the given directory.

    Removes the 'dayX' substring from import statements (where X is between 1 and 4).

    Args:
        directory (str): Path to the root directory.
    """
    # Regular expression to match import statements with 'dayX'
    import_pattern = re.compile(r'(import\s+)(day[1-4]\.)?(\*|.*)')

    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(".kt"):
                file_path = os.path.join(root, file)
                with open(file_path, "r", encoding="utf-8") as kotlin_file:
                    lines = kotlin_file.readlines()

                updated_lines = []
                modified = False

                for line in lines:
                    match = import_pattern.match(line)
                    if match:
                        if match.group(3) and match.group(3) != "*":
                            # Rewrite the import statement without 'dayX', if something meaningful remains
                            updated_line = f"{match.group(1)}{match.group(3)}\n"
                            updated_lines.append(updated_line)
                            modified = True
                        else:
                            # Skip the line if it becomes empty or is just 'import *'
                            modified = True
                    else:
                        updated_lines.append(line)

                if modified:
                    # Write updated content only if changes were made
                    with open(file_path, "w", encoding="utf-8") as kotlin_file:
                        kotlin_file.writelines(updated_lines)

if __name__ == "__main__":
    # Define the directory path as a variable
    directory = "/Users/Dmitrii.Kotov/repos/concurrency-tools/jvm-concurrency-dataset/data"
    if os.path.isdir(directory):
        shorten_imports_in_kotlin_files(directory)
        print("Import statements processed successfully.")
    else:
        print("The specified directory does not exist.")
