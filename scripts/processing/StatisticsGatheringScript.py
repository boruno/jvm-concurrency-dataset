import os
import csv

# -------------------------
# Configuration
# -------------------------
# Update this to your directory that contains the 30 folders.
parent_dir = 'options_testing'

# Output CSV file names
output_files = {
    'all': 'results_all.csv',
    'mc': 'results_mc.csv',
    'stress': 'results_stress.csv'
}

# The 7 metric names to extract (in order)
metric_names = [
    "Testing errors",
    "Correct",
    "Incorrect",
    "Incorrectness",
    "Lincheck bugs",
    "Timeouts",
    "Total execution time"
]

# -------------------------
# Functions
# -------------------------
def parse_stats(file_path):
    """
    Given a path to a statistics txt file, extract the metrics from the block starting at line 449.
    Assumes line 449 is "Overall Statistics", then the following 8 lines contain:
      - Total number of files processed (to ignore)
      - And then 7 metrics.
    """
    try:
        with open(file_path, 'r') as f:
            lines = f.readlines()
    except Exception as e:
        print(f"Error reading {file_path}: {e}")
        return None

    # Check if file has enough lines.
    # We need at least 457 lines since we want lines 449 to 457 (inclusive) where line 449 is index 448.
    if len(lines) < 457:
        print(f"File {file_path} does not contain enough lines (found {len(lines)} lines).")
        return None

    # Extract the block from line 449 to 457 (inclusive)
    # Python indexing: line 449 is index 448.
    stats_block = lines[448:457]

    # stats_block structure:
    # [0] Overall Statistics
    # [1] Total number of files processed: 242   <-- ignore this line
    # [2] Testing errors: 0
    # [3] Correct: 69
    # [4] Incorrect: 173
    # [5] Incorrectness: 71.49%
    # [6] Lincheck bugs: 0
    # [7] Timeouts: 0
    # [8] Total execution time: 6024.48 seconds
    # We want indices 2 to 8 (7 lines)
    metrics_data = {}
    for i, line in enumerate(stats_block[2:]):  # indices 2 to 8 => 7 lines
        # Split on the first colon only.
        parts = line.split(":", 1)
        if len(parts) == 2:
            key = parts[0].strip()
            value = parts[1].strip()
            # Optional: remove extra text from value if needed (like "seconds") or "%" signs.
            metrics_data[key] = value
        else:
            print(f"Could not parse line in {file_path}: {line.strip()}")

    return metrics_data

def write_csv(file_path, data, header):
    """
    Write data to CSV file.
    - data: dictionary with key = setting name, value = metrics dictionary.
    - header: list of metric names, in desired order.
    The CSV first column is the setting name.
    """
    with open(file_path, 'w', newline='') as csvfile:
        writer = csv.writer(csvfile)
        # Write header: first cell can be "Setting" then metric names.
        writer.writerow(["Setting"] + header)
        # Write each row: folder name and then the metrics in the specified header order.
        for setting, metrics in sorted(data.items()):
            # Create a row with each metric value; if missing, leave blank.
            row = [metrics.get(metric, "") for metric in header]
            writer.writerow([setting] + row)

# -------------------------
# Main Processing
# -------------------------
# Dictionaries to store the parsed metrics for each testing method
results = {
    'all': {},
    'mc': {},
    'stress': {}
}

# Iterate over all items in the parent directory.
for folder in os.listdir(parent_dir):
    folder_path = os.path.join(parent_dir, folder)
    if os.path.isdir(folder_path):
        # Prepare file names based on the folder name and the expected format.
        file_names = {
            'all': f"testing_statistics-{folder}-all.txt",
            'mc': f"testing_statistics-{folder}-mc.txt",
            'stress': f"testing_statistics-{folder}-stress.txt"
        }
        for method, file_name in file_names.items():
            file_path = os.path.join(folder_path, file_name)
            if os.path.exists(file_path):
                stats = parse_stats(file_path)
                if stats:
                    results[method][folder] = stats
            else:
                print(f"File not found: {file_path}")

# Write out results to CSV files for each testing method.
for method, output_csv in output_files.items():
    write_csv(output_csv, results[method], metric_names)
    print(f"Wrote {len(results[method])} entries to {output_csv}")
