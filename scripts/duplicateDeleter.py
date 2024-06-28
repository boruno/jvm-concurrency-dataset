import os
import hashlib


def hash_file(file_path):
    hasher = hashlib.md5()
    with open(file_path, 'rb') as f:
        buf = f.read()
        hasher.update(buf)
    return hasher.hexdigest()


def find_duplicates(directory):
    hashes = {}
    duplicates = []
    for root, _, files in os.walk(directory):
        for file in files:
            file_path = os.path.join(root, file)
            file_hash = hash_file(file_path)
            if file_hash in hashes:
                duplicates.append((file_path, hashes[file_hash]))
            else:
                hashes[file_hash] = file_path
    return duplicates


duplicates = find_duplicates('data/studentSolutions')

for duplicate in duplicates:
    try:
        os.remove(duplicate[0])  # delete duplicate file
    except OSError as e:
        print("Error: %s : %s" % (duplicate[0], e.strerror))
