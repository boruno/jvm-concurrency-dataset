import re
import matplotlib.pyplot as plt
import numpy as np

def parse_overall_statistics(file_path):
    """
    Reads the file at file_path and extracts the overall statistics from the
    'Overall Statistics' section. Ignores the 'Total number of files processed' field.
    Returns a dictionary mapping category names to numeric values.
    """
    overall_stats = {}
    with open(file_path, 'r') as f:
        lines = f.readlines()

    found = False
    for line in lines:
        line = line.strip()
        if line == "Overall Statistics":
            found = True
            continue
        if found:
            # Break on an empty line after the section
            if not line:
                break
            if ':' in line:
                # Split only on the first colon
                parts = line.split(':', 1)
                key = parts[0].strip()
                value_str = parts[1].strip()
                # Skip 'Total number of files processed'
                if key == "Total number of files processed":
                    continue
                # For total execution time, remove trailing "seconds"
                if key == "Total execution time":
                    value_str = value_str.split()[0]  # remove "seconds" or any trailing text
                    try:
                        value = float(value_str)
                    except ValueError:
                        value = 0.0
                # For incorrectness, remove '%' sign and parse float
                elif key == "Incorrectness":
                    value_str = value_str.replace("%", "")
                    try:
                        value = float(value_str)
                    except ValueError:
                        value = 0.0
                else:
                    # integer fields
                    try:
                        value = int(value_str)
                    except ValueError:
                        value = 0
                overall_stats[key] = value
    return overall_stats

# Update with the actual file paths you want to compare
file_paths = [
    "testing_statistics-2.34.txt",
    "testing_statistics-2.35.txt",
    "testing_statistics-2.36.txt"
]

versions = []
overall_data = []

# Parse each file's overall statistics
for fp in file_paths:
    stats = parse_overall_statistics(fp)
    overall_data.append(stats)
    # Extract version info from the filename (e.g. "2.34" from "testing_statistics-2.34.txt")
    version_match = re.search(r'-(\d+\.\d+)', fp)
    version = version_match.group(1) if version_match else fp
    versions.append(version)

# Categories for the first bar chart (excluding total execution time and "Incorrectness")
categories_no_time = [
    "Testing errors",
    "Correct",
    "Incorrect",
    "Lincheck bugs",
    "Timeouts"
]

# Prepare a matrix for the first plot
# Rows = each version, columns = categories_no_time
data_matrix = []
for stats in overall_data:
    row = [stats.get(cat, 0) for cat in categories_no_time]
    data_matrix.append(row)

# --- PLOT 1: Grouped Bar Chart (No "Incorrectness") ---

x = np.arange(len(categories_no_time))  # group positions on x-axis
width = 0.25  # width of each individual bar

fig1, ax1 = plt.subplots(figsize=(10, 6))

colors = ['blue', 'orange', 'green']  # you can add more if needed

for i, row in enumerate(data_matrix):
    # Shift each version's bars slightly to avoid overlap
    offset = (i - 1) * width
    ax1.bar(x + offset, row, width, label=f"Version {versions[i]}", color=colors[i])

ax1.set_xticks(x)
ax1.set_xticklabels(categories_no_time, rotation=45, ha='right')
ax1.set_ylabel("Value")
ax1.set_title("Comparison of Overall Testing Statistics (Excluding Time & Incorrectness)")
ax1.legend()

plt.tight_layout()
plt.savefig("version_comparison_excluding_time.png")  # Save the first plot
plt.show()

# --- PLOT 2: Bar Chart for Total Execution Time Only ---

fig2, ax2 = plt.subplots(figsize=(6, 6))

time_values = [stats.get("Total execution time", 0) for stats in overall_data]
x2 = np.arange(len(versions))

ax2.bar(x2, time_values, color=colors[:len(versions)])
ax2.set_xticks(x2)
ax2.set_xticklabels([f"Version {v}" for v in versions], rotation=45, ha='right')
ax2.set_ylabel("Seconds")
ax2.set_title("Comparison of Total Execution Time")

plt.tight_layout()
plt.savefig("version_comparison_total_execution_time.png")  # Save the second plot
plt.show()
