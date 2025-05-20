import os
import re

def update_lincheck_version(directory: str, new_version: str):
    pattern = re.compile(r'(testImplementation\("org\.jetbrains\.kotlinx:lincheck:)([\d\.]+(?:-SNAPSHOT)?)("\))')

    for root, _, files in os.walk(directory):
        for file in files:
            if file == "build.gradle.kts":
                file_path = os.path.join(root, file)

                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()

                updated_content, count = pattern.subn(rf'\g<1>{new_version}\g<3>', content)

                if count > 0:
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(updated_content)
                    print(f"Updated {count} occurrences in {file_path}")
                else:
                    print(f"No changes made in {file_path}")

# Set directory and desired version
directory_to_search = "../../template-projects/"
new_lincheck_version = "2.39"

update_lincheck_version(directory_to_search, new_lincheck_version)
