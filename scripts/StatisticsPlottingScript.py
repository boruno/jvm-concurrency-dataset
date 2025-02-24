import matplotlib.pyplot as plt
import numpy as np
import re
import os

def parse_statistics_file(file_path):
    data = {}
    current_task = None

    with open(file_path, 'r') as file:
        for line in file:
            line = line.strip()

            if not line:
                continue

            if ':' not in line:  # More robust task name detection
                current_task = line
                data[current_task] = {
                    'processed': 0,
                    'concurrency_bugs': 0,
                    'lincheck_bugs': 0,
                    'correct': 0,
                    'timeouts': 0,
                    'testing_errors': 0
                }
            else:
                parts = line.split(': ')
                if len(parts) != 2:
                    continue  # Skip malformed lines
                key, value = parts
                try:
                    value = int(value)
                    if 'Number of files processed' in key:
                        data[current_task]['processed'] = value
                    elif 'Testing errors' in key:
                        data[current_task]['testing_errors'] = value
                    elif 'Lincheck bugs' in key:
                        data[current_task]['lincheck_bugs'] = value
                    elif 'Timeouts' in key:
                        data[current_task]['timeouts'] = value
                    elif 'Correct' in key:
                        data[current_task]['correct'] = value
                    elif 'Concurrency bugs' in key:
                        data[current_task]['concurrency_bugs'] = value
                except ValueError:
                    continue  # Ignore non-integer values

    return {k: v for k, v in data.items() if v['processed'] > 0}  # Ensure empty tasks are removed

def plot_statistics(data, version):
    if not data:
        print("No valid data found in the file.")
        return

    tasks = list(data.keys())
    processed = [data[task]['processed'] for task in tasks]
    concurrency_bugs = np.array([data[task]['concurrency_bugs'] for task in tasks])
    lincheck_bugs = np.array([data[task]['lincheck_bugs'] for task in tasks])
    correct = np.array([data[task]['correct'] for task in tasks])
    timeouts = np.array([data[task]['timeouts'] for task in tasks])
    testing_errors = np.array([data[task]['testing_errors'] for task in tasks])

    bar_width = 0.6
    fig, ax = plt.subplots(figsize=(14, 7))

    bottom = np.zeros(len(tasks))
    ax.bar(tasks, concurrency_bugs, color='green', label='Concurrency Violations', bottom=bottom)
    bottom += concurrency_bugs
    ax.bar(tasks, lincheck_bugs, color='black', label='Lincheck Bugs', bottom=bottom)
    bottom += lincheck_bugs
    ax.bar(tasks, correct, color='red', label='Correct', bottom=bottom)
    bottom += correct
    ax.bar(tasks, timeouts, color='white', edgecolor='black', label='Timeouts', bottom=bottom)
    bottom += timeouts
    ax.bar(tasks, testing_errors, color='gray', label='Testing Errors', bottom=bottom)

    ax.set_ylabel('Number of Files Processed')
    ax.set_title('Task Testing Statistics')
    ax.set_xticks(range(len(tasks)))
    ax.set_xticklabels(tasks, rotation=90, fontsize=8)
    ax.legend()

    plt.tight_layout()
    plot_filename = f"task_statistics_plot_{version}.png"
    plt.savefig(plot_filename)  # Save the plot with version number
    plt.show()

if __name__ == "__main__":
    file_path = "testing_statistics-2.35.txt"  # Update this if the file is located elsewhere
    version_match = re.search(r'-(\d+\.\d+)', file_path)
    version = version_match.group(1) if version_match else "unknown"

    stats_data = parse_statistics_file(file_path)

    if stats_data:
        plot_statistics(stats_data, version)
    else:
        print("No valid data available to plot.")
