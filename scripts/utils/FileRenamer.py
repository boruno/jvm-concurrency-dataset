import os
import re

path = '..'  # Change this to the path of the directory containing the folders

for folder_name in os.listdir(path):
    folder_path = os.path.join(path, folder_name)
    if os.path.isdir(folder_path):
        for file_name in os.listdir(folder_path):
            if file_name.endswith('.kt'):
                new_file_name = re.sub(r'-(stress|model-checking)-', '-', file_name)
                old_file_path = os.path.join(folder_path, file_name)
                new_file_path = os.path.join(folder_path, new_file_name)

                # Handle potential file name conflicts
                counter = 1
                while os.path.exists(new_file_path):
                    print(f'Conflict: {new_file_name} already exists in {folder_name}')
                    base, ext = os.path.splitext(new_file_name)
                    new_file_name = f"{base}-{counter}{ext}"
                    new_file_path = os.path.join(folder_path, new_file_name)
                    counter += 1

                os.rename(old_file_path, new_file_path)
