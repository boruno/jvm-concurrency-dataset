import math
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
                    'non-executable_tests': 0
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
                        data[current_task]['non-executable_tests'] = value
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

    # Ensure empty tasks are removed
    return {k: v for k, v in data.items() if v['processed'] > 0}


def make_autopct(values):
    """
    Returns a function that formats the pie chart labels to show:
    - Percentage with one decimal
    - Actual count in parentheses
    - Hide the label if the count is zero
    """
    def autopct(pct):
        total = sum(values)
        absolute = int(round(pct * total / 100.0))
        # Only show label if slice value is > 0
        return f'{pct:.1f}% ({absolute})' if absolute > 0 else ''
    return autopct


def plot_statistics(data, version):
    if not data:
        print("No valid data found in the file.")
        return

    tasks = list(data.keys())
    category_names = ['Concurrency Violations', 'Lincheck Bugs', 'Correct', 'Timeouts', 'Non-executable tests']
    colors = ["#E69F00", "#D55E00", "#009E73", "#CC79A7", "#999999"]

    # Determine grid dimensions for subplots (try a square layout)
    num_tasks = len(tasks)
    cols = math.ceil(math.sqrt(num_tasks))
    rows = math.ceil(num_tasks / cols)

    # Reduced figure size and DPI
    fig, axes = plt.subplots(rows, cols, figsize=(3 * cols, 3 * rows))
    fig.suptitle('Task Testing Statistics', fontsize=12)

    # Flatten axes if we have more than one subplot
    if num_tasks == 1:
        axes = [axes]
    else:
        axes = axes.flatten()

    for idx, task in enumerate(tasks):
        stats = data[task]
        slices = [
            stats['concurrency_bugs'],
            stats['lincheck_bugs'],
            stats['correct'],
            stats['timeouts'],
            stats['non-executable_tests']
        ]
        ax = axes[idx]

        total = sum(slices)
        # If everything is zero, show a dummy pie to visualize an empty chart
        if total == 0:
            slices = [1] * len(slices)

        # Count how many slices are non-zero
        nonzero_slices = sum(1 for s in slices if s > 0)

        # If only one non-zero slice, remove any dividing line
        if nonzero_slices == 1:
            wedgeprops = dict(edgecolor='none', linewidth=0)
        else:
            # Default wedgeprops
            wedgeprops = {}

        wedges, texts, autotexts = ax.pie(
            slices,
            colors=colors,
            autopct=make_autopct(slices),
            startangle=90,
            textprops={'color': 'black', 'fontsize': 8},  # Reduced font size
            wedgeprops=wedgeprops
        )

        # Set a black edge for the timeouts wedge (index 3) only if there's more than one non-zero slice
        if nonzero_slices > 1 and len(wedges) >= 4:
            wedges[3].set_edgecolor('black')
            wedges[3].set_linewidth(1)

        # Fix text color for the black wedge (lincheck bugs, index 1) so it's visible
        if len(autotexts) > 1:
            autotexts[1].set_color('white')  # white text on black wedge

        ax.set_title(task, fontsize=8)  # Reduced font size

    # Hide any unused subplots
    for j in range(idx + 1, len(axes)):
        axes[j].axis('off')

    # Create a common legend for all subplots with smaller markers
    legend_handles = [
        plt.Line2D([0], [0],
                   marker='o',
                   color='w',
                   label=label,
                   markerfacecolor=color,
                   markersize=8,  # Reduced marker size
                   markeredgecolor='black' if label == 'Timeouts' else color)
        for label, color in zip(category_names, colors)
    ]
    fig.legend(handles=legend_handles, loc='upper right', fontsize=8)  # Reduced font size

    plt.tight_layout(rect=[0, 0, 0.85, 0.95])
    plot_filename = f"task_statistics_plot_{version}.png"

    # Save with optimized parameters
    plt.savefig(
        plot_filename,
        dpi=100,  # Reduced DPI
        bbox_inches='tight',
        format='png',
        pil_kwargs={'optimize': True, 'compression': 9}  # PIL-specific optimizations
    )

    plt.close()


if __name__ == "__main__":
    file_path = "../processing/testing_statistics.txt"  # Update this if the file is located elsewhere
    version_match = re.search(r'-(\d+\.\d+.*)\.txt$', file_path)
    version = version_match.group(1) if version_match else "unknown"

    stats_data = parse_statistics_file(file_path)

    if stats_data:
        plot_statistics(stats_data, version)
    else:
        print("No valid data available to plot.")
