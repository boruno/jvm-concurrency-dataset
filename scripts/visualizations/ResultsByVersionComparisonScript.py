import re
import numpy as np
import pandas as pd
import plotly.graph_objects as go

def parse_overall_statistics(file_path):
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
            if not line:
                break
            if ':' in line:
                parts = line.split(':', 1)
                key = parts[0].strip()
                value_str = parts[1].strip()
                if key == "Total number of files processed":
                    continue
                if key == "Total execution time":
                    value_str = value_str.split()[0]
                    try:
                        value = float(value_str)
                    except ValueError:
                        value = 0.0
                elif key == "Incorrectness":
                    value_str = value_str.replace("%", "")
                    try:
                        value = float(value_str)
                    except ValueError:
                        value = 0.0
                else:
                    try:
                        value = int(value_str)
                    except ValueError:
                        value = 0
                overall_stats[key] = value
    return overall_stats

file_paths = [
    "../processing/testing_statistics-2.34.txt",
    "../processing/testing_statistics-2.35.txt",
    "../processing/testing_statistics-2.36.txt"
]

versions = []
overall_data = []

# Parse statistics
for fp in file_paths:
    stats = parse_overall_statistics(fp)
    overall_data.append(stats)
    version_match = re.search(r'-(\d+\.\d+)', fp)
    version = version_match.group(1) if version_match else fp
    versions.append(version)

# Categories excluding execution time
categories_no_time = [
    "Incorrect",
    "Correct",
    "Non-executable test",
    "Lincheck bugs",
    "Timeouts"
]

# Create data matrix
data_matrix = []
for stats in overall_data:
    row = [
        stats.get("Incorrect", 0),
        stats.get("Correct", 0),
        stats.get("Testing errors", 0),
        stats.get("Lincheck bugs", 0),
        stats.get("Timeouts", 0),
    ]
    data_matrix.append(row)

data_matrix = np.array(data_matrix)

# Create DataFrame
data = {
    "version": [f"Version {v}" for v in versions],
    "Incorrect": data_matrix[:, 0],
    "Correct": data_matrix[:, 1],
    "Non-executable test": data_matrix[:, 2],
    "Lincheck bugs": data_matrix[:, 3],
    "Timeouts": data_matrix[:, 4]
}

df = pd.DataFrame(data)

# Define colors based on the corrected mapping
metrics = ["Incorrect", "Correct", "Non-executable test", "Lincheck bugs", "Timeouts"]
colors = ["#E69F00", "#009E73", "#999999", "#D55E00", "#CC79A7"]
hatch_patterns = ["/", "x", ".", "-", "+"]

# --- Stacked Horizontal Bar Chart (Excluding Time) ---
fig1 = go.Figure()

for metric, color, hatch in zip(metrics, colors, hatch_patterns):
    fig1.add_trace(go.Bar(
        y=df["version"],
        x=df[metric],
        name=metric,
        orientation="h",
        marker=dict(color=color, pattern_shape=hatch),  # Apply hatching
        text=df[metric],
        textposition="inside"
    ))

fig1.update_layout(
    barmode="stack",
    title="Testing results by version",
    xaxis_title="Count",
    yaxis_title="Version",
    legend_title="Metrics",
    width=1000,  # Wider layout
    height=400   # Shorter height
)

# Save plot as image
fig1.write_image("version_comparison_excluding_time.png")

# --- Bar Chart for Total Execution Time ---
execution_times = [stats.get("Total execution time", 0) for stats in overall_data]

fig2 = go.Figure()
fig2.add_trace(go.Bar(
    y=[f"Version {v}" for v in versions],
    x=execution_times,
    name="Execution Time",
    orientation="h",
    marker=dict(color="#1F78B4", pattern_shape="x"),  # Different hatching
    text=execution_times,
    textposition="inside"
))

fig2.update_layout(
    title="Comparison of Total Execution Time",
    xaxis_title="Seconds",
    yaxis_title="Version",
    legend_title="Metrics"
)

# Save execution time plot
fig2.write_image("version_comparison_total_execution_time.png")
