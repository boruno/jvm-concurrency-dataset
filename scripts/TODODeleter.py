import os


def find_files_with_todo(directory):
    # Matches a string that contains 'TODO' but not surrounded by '/*' and '*/' or following '//'
    files_with_todo = []
    multiline_comment = False

    for root, _, files in os.walk(directory):
        for file in files:
            file_path = os.path.join(root, file)
            try:
                with open(file_path, 'r', encoding="utf-8", errors="ignore") as f:
                    for line in f:
                        if '/*' in line:
                            multiline_comment = True
                        if '*/' in line:
                            multiline_comment = False
                            continue
                        if not multiline_comment and 'TODO' in line and not line.lstrip().startswith('//'):
                            files_with_todo.append(file_path)
                            break  # break after finding the first TODO in a file
            except Exception as e:
                print(f"Failed to process file {file_path} due to {str(e)}")

    return files_with_todo


files_with_todo = find_files_with_todo('data/studentSolutions')

for file in files_with_todo:
    try:
        os.remove(file)
    except Exception as e:
        print(f"Failed to delete {file} due to {str(e)}")
