import os

def comment_package_lines(directory_list):
    for directory in directory_list:
        for root, _, files in os.walk(directory):
            for file in files:
                if file.endswith('.kt'):
                    file_path = os.path.join(root, file)
                    process_file(file_path)

def process_file(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as file:
            lines = file.readlines()

        modified = False
        with open(file_path, 'w', encoding='utf-8') as file:
            for line in lines:
                stripped_line = line.lstrip()
                if stripped_line.startswith('package') and not stripped_line.startswith('//package'):
                    file.write('//' + stripped_line)
                    modified = True
                else:
                    file.write(line)

        if modified:
            print(f"Modified: {file_path}")

    except Exception as e:
        print(f"Error processing {file_path}: {e}")

if __name__ == "__main__":
    directories = ["/Users/Dmitrii.Kotov/repos/concurrency-tools/jvm-concurrency-dataset/data/studentSolutions",]
    comment_package_lines(directories)
