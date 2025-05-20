import csv
import os
import re
import plotly.graph_objects as go

# --------------------------------------------------------------------------------------
# 1. CONFIGURATION
# --------------------------------------------------------------------------------------
# Name of the CSV files for each testing scenario:
CSV_FILES = {
    'all':    '../processing/results_all.csv',
    'mc':     '../processing/results_mc.csv',
    'stress': '../processing/results_stress.csv'
}

# Default folder names for each category
DEFAULTS = {
    'actors':      'actors_0-0',         # Default for actors
    'invocations': 'invocations_1000',   # Default for invocations
    'iterations':  'iterations_50',      # Default for iterations
    'threads':     'threads_3-3'         # Default for threads
}

# Which categories we want to process
CATEGORIES = ['actors', 'invocations', 'iterations', 'threads']

# Two metrics of interest:
METRIC_INCORRECT       = 'Incorrect'
METRIC_EXECUTION_TIME  = 'Total execution time'

# Configuration for categories that require special grouping
# 'items_per_group': How many bars in each visual group.
# 'group_gap': The size of the gap between groups, in units of bar positions (e.g., 1.0 means a gap the width of one bar position).
# 'bar_width_abs': Absolute width of bars when using a numeric x-axis (e.g., 0.8 for 80% of the unit space).
GROUPING_CONFIGURATION = {
    'threads': {'items_per_group': 4, 'group_gap': 1.0, 'bar_width_abs': 0.8}
    # Add other categories here if they need similar grouping and have a fixed number of items per group.
    # For example:
    # 'another_category_type': {'items_per_group': 3, 'group_gap': 0.5, 'bar_width_abs': 0.7}
}

# --------------------------------------------------------------------------------------
# 2. HELPER FUNCTIONS
# --------------------------------------------------------------------------------------
def parse_csv_to_dict(csv_file):
    """
    Reads the CSV file and returns a dictionary:
        data[folder_name] = {
            "Incorrect": <float or int>,
            "Total execution time": <float>
        }
    """
    data = {}
    if not os.path.exists(csv_file):
        print(f"CSV file does not exist: {csv_file}")
        return data

    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            folder = row['Setting'].strip()
            # Parse "Incorrect"
            incorrect_raw = row.get(METRIC_INCORRECT, "")
            try:
                incorrect_val = float(incorrect_raw)
            except ValueError:
                incorrect_val = 0

            # Parse "Total execution time"; remove potential trailing text
            time_str = row.get(METRIC_EXECUTION_TIME, "")
            match_time = re.search(r"([\d\.]+)", time_str)
            time_val = float(match_time.group(1)) if match_time else 0

            data[folder] = {
                METRIC_INCORRECT:      incorrect_val,
                METRIC_EXECUTION_TIME: time_val
            }
    return data

def identify_category_and_value(folder_name):
    """
    Given a folder name (e.g. "invocations_2500" or "threads_3-3"), identify:
      - The category (one of the CATEGORIES) and
      - The numeric value (or tuple for ranges) for sorting.

    Examples:
      "invocations_2500" -> ("invocations", 2500.0)
      "threads_3-5"      -> ("threads", (3.0, 5.0))
      "actors_0-0"       -> ("actors", (0.0, 0.0))
    """
    for cat in CATEGORIES:
        prefix = cat + "_"
        if folder_name.startswith(prefix):
            val_str = folder_name[len(prefix):]  # everything after the prefix
            if "-" in val_str:
                parts = val_str.split("-")
                try:
                    numeric_tuple = tuple(float(p) for p in parts)
                except ValueError:
                    numeric_tuple = (9999999,) # Should ideally be a very large number for sorting
                return (cat, numeric_tuple)
            else:
                try:
                    num_val = float(val_str)
                except ValueError:
                    num_val = 9999999 # Should ideally be a very large number for sorting
                return (cat, num_val)
    return (None, None) # Should not happen if folder names are consistent

def custom_sort_key(value):
    """
    Returns a sort key that can handle either a float or a tuple of floats.
    """
    if isinstance(value, tuple):
        return value  # Tuples sort naturally based on their elements
    return (value,) # Convert single float to tuple for consistent sorting

def make_plotly_figure(
        scenario,       # 'all', 'mc', or 'stress'
        category,       # 'actors', 'invocations', 'iterations', 'threads'
        x_labels_original, # list of x-axis labels (strings)
        incorrect_vals, # list of floats for the "Incorrect" metric
        time_vals       # list of floats for "Total execution time"
):
    fig = go.Figure()

    group_config = GROUPING_CONFIGURATION.get(category)
    apply_grouping = False
    items_per_group = 0
    num_groups = 0
    group_gap = 0.0
    bar_width_for_numeric_axis = 0.8 # Default bar width if using numeric axis due to grouping

    if group_config and len(x_labels_original) > 0:
        items_per_group = group_config['items_per_group']
        if len(x_labels_original) % items_per_group == 0:
            apply_grouping = True
            num_groups = len(x_labels_original) // items_per_group
            group_gap = group_config.get('group_gap', 1.0) # Default gap if not specified
            bar_width_for_numeric_axis = group_config.get('bar_width_abs', 0.8)
        else:
            print(f"Warning: For category '{category}', total items {len(x_labels_original)} "
                  f"is not divisible by 'items_per_group' ({items_per_group}). Grouping disabled.")

    # Determine x-coordinates for plotting and x-axis configuration
    plot_x_coords = list(x_labels_original) # Default: use original labels for a categorical axis
    xaxis_config = dict(title=f"{category.capitalize()} setting", type='category') # Default axis type

    if apply_grouping:
        xaxis_config['type'] = 'linear' # Switch to linear axis for numeric control of positions

        numeric_coords_for_plot_data = []
        tick_positions_for_xaxis_ticks = []
        current_x_pos = 0.0

        for i in range(num_groups):
            for j in range(items_per_group):
                numeric_coords_for_plot_data.append(current_x_pos)
                tick_positions_for_xaxis_ticks.append(current_x_pos)
                current_x_pos += 1.0 # Each bar position is 1 unit apart within a group
            if i < num_groups - 1: # Add gap after the group, unless it's the last group
                current_x_pos += group_gap

        plot_x_coords = numeric_coords_for_plot_data # These are the numeric x-values for data points
        xaxis_config.update(dict(
            tickmode='array',
            tickvals=tick_positions_for_xaxis_ticks, # Numeric positions where ticks should appear
            ticktext=list(x_labels_original) # Original string labels for those ticks
        ))

    # --- Add Bar Trace for "Incorrect" ---
    # Determine bar width: specific for 'actors' if not grouped, or from config if grouped, else auto.
    bar_plot_width = None
    if apply_grouping:
        bar_plot_width = bar_width_for_numeric_axis
    elif category == 'actors':
        bar_plot_width = 0.33

    fig.add_trace(
        go.Bar(
            x=plot_x_coords, # Use numeric coords if grouped, else original labels
            y=incorrect_vals,
            name=METRIC_INCORRECT,
            yaxis='y1',
            marker_color='orange',
            width=bar_plot_width # Adjusted width
        )
    )

    # --- Add Line Trace(s) for "Total execution time" ---
    if apply_grouping:
        # Create a separate scatter trace for each group segment
        for i in range(num_groups):
            # Indices for data arrays (incorrect_vals, time_vals)
            data_start_idx = i * items_per_group
            data_end_idx = data_start_idx + items_per_group

            # Slice the numeric coordinates for the current group
            # plot_x_coords is already structured with gaps if apply_grouping is true
            current_group_x_values = plot_x_coords[data_start_idx:data_end_idx]
            current_group_time_values = time_vals[data_start_idx:data_end_idx]

            fig.add_trace(
                go.Scatter(
                    x=current_group_x_values,
                    y=current_group_time_values,
                    name=METRIC_EXECUTION_TIME if i == 0 else "", # Show legend only for the first segment
                    yaxis='y2',
                    mode='lines+markers',
                    marker_color='blue',
                    showlegend=(i == 0) # Explicitly control legend entry
                )
            )
    else: # No grouping, single line trace
        fig.add_trace(
            go.Scatter(
                x=plot_x_coords, # Original labels (categorical axis)
                y=time_vals,
                name=METRIC_EXECUTION_TIME,
                yaxis='y2',
                mode='lines+markers',
                marker_color='blue'
            )
        )

    # --- Configure Layout ---
    fig.update_layout(
        title=f"{scenario.upper()} - {category.capitalize()} Comparison",
        xaxis=xaxis_config, # Apply the determined x-axis configuration
        yaxis=dict(
            title=METRIC_INCORRECT,
            side='left',
            automargin=True
        ),
        yaxis2=dict(
            title=METRIC_EXECUTION_TIME + " (seconds)",
            overlaying='y',
            side='right',
            automargin=True
        ),
        legend=dict(x=0.02, y=0.98, bordercolor="gray", borderwidth=1),
        width=900,
        height=600,
        template="plotly_white"
    )

    # --- Add Group Name Annotations (if grouping is applied) ---
    bottom_margin = 60 # Default bottom margin
    if apply_grouping:
        group_text_labels_for_annotations = []
        # Specific logic for 'threads' to get "2", "3", "4", "5"
        if category == 'threads':
            first_number_pattern = re.compile(r"(\d+)-.*") # Extracts the first number from "N-M"
            for i in range(num_groups):
                # Use the first original label of each group to derive the group name
                original_label_at_group_start = x_labels_original[i * items_per_group]
                match = first_number_pattern.match(original_label_at_group_start)
                if match:
                    group_text_labels_for_annotations.append(match.group(1))
                else: # Fallback if pattern doesn't match
                    group_text_labels_for_annotations.append(f"Group {i + 1}")
        else: # Generic group naming for other categories if they are grouped
            for i in range(num_groups):
                group_text_labels_for_annotations.append(f"Group {i + 1}")

        # Add annotations below the x-axis
        current_data_idx_for_annotations = 0
        for i in range(num_groups):
            # Get the numeric x-coordinates for the current group's bars
            group_specific_x_coords = plot_x_coords[current_data_idx_for_annotations : current_data_idx_for_annotations + items_per_group]
            if not group_specific_x_coords: continue # Should not happen if logic is correct

            # Calculate the center x-position for the annotation for this group
            annotation_x_center_pos = (group_specific_x_coords[0] + group_specific_x_coords[-1]) / 2.0

            fig.add_annotation(
                x=annotation_x_center_pos,
                y=0, # Relative to paper coordinates (bottom of the plot area)
                yshift=-50, # Shift further down to clear x-tick labels
                text=f"<b>{group_text_labels_for_annotations[i]}</b>", # Bold text
                showarrow=False,
                xref="x",   # x-coordinate is based on data values on x-axis
                yref="paper",# y-coordinate is relative to plotting area
                font=dict(size=12, color="black"),
                xanchor="center"
            )
            current_data_idx_for_annotations += items_per_group
        bottom_margin = 100 # Increase bottom margin to make space for these annotations

    fig.update_layout(margin=dict(b=bottom_margin, l=70, t=70, r=70)) # Adjust margins

    # --- Add Horizontal Dashed Red Line at y=242 ---
    fig.add_shape(
        type="line",
        xref="paper", # Line spans the full width of the plot
        x0=0, x1=1,
        yref="y1",    # Relative to the first y-axis (Incorrect)
        y0=242, y1=242,
        line=dict(color="red", width=2, dash="dash")
    )
    return fig

# --------------------------------------------------------------------------------------
# 3. MAIN LOGIC
# --------------------------------------------------------------------------------------
if __name__ == "__main__":
    # 3.1 Read each scenario CSV into a dictionary
    scenario_data = {}
    for scenario, csv_file in CSV_FILES.items():
        scenario_data[scenario] = parse_csv_to_dict(csv_file)

    # 3.2 For each scenario, group folder names by category and include "default" properly
    for scenario_name in ['all', 'mc', 'stress']: # Explicitly use scenario_name to avoid conflict
        data_dict = scenario_data[scenario_name]
        if not data_dict:
            print(f"No data found for scenario '{scenario_name}'. Skipping.")
            continue

        categories_map = {cat_name: [] for cat_name in CATEGORIES} # Use cat_name

        for folder_name, metrics in data_dict.items():
            if folder_name.lower() == "default":
                continue
            cat, val = identify_category_and_value(folder_name)
            if cat in CATEGORIES:
                categories_map[cat].append((folder_name, val))

        if "default" in data_dict:
            for cat_name in CATEGORIES: # Use cat_name
                _, numeric_val = identify_category_and_value(DEFAULTS[cat_name])
                categories_map[cat_name].append(("default", numeric_val))

        # 3.3 Build and save the figure for each category
        for cat_name in CATEGORIES: # Use cat_name
            if not categories_map[cat_name]:
                print(f"No data for category '{cat_name}' in scenario '{scenario_name}'. Skipping plot.")
                continue

            sorted_items = sorted(categories_map[cat_name], key=lambda x: custom_sort_key(x[1]))

            final_x_labels = []
            final_incorrect_vals = []
            final_time_vals = []

            for (fname, _) in sorted_items:
                label_str = ""
                inc_val = 0
                tim_val = 0

                current_metrics_source = {}
                if fname.lower() == "default":
                    current_metrics_source = data_dict.get("default", {})
                    default_full_path = DEFAULTS[cat_name] # e.g. "threads_3-3"
                    # Extract the value part for the label, e.g., "3-3" from "threads_3-3"
                    label_str = default_full_path[len(cat_name)+1:] if default_full_path.startswith(cat_name + "_") else default_full_path
                else:
                    current_metrics_source = data_dict.get(fname, {})
                    # Extract the value part for the label, e.g., "3-3" from "threads_3-3"
                    label_str = fname[len(cat_name)+1:] if fname.startswith(cat_name + "_") else fname

                inc_val = current_metrics_source.get(METRIC_INCORRECT, 0)
                tim_val = current_metrics_source.get(METRIC_EXECUTION_TIME, 0)

                final_x_labels.append(label_str)
                final_incorrect_vals.append(inc_val)
                final_time_vals.append(tim_val)

            if not final_x_labels: # Skip if no data ended up being processed for labels
                print(f"No labels generated for category '{cat_name}' in scenario '{scenario_name}'. Skipping plot.")
                continue

            # 3.4 Create the Plotly figure using the modified function
            fig = make_plotly_figure(
                scenario       = scenario_name,
                category       = cat_name,
                x_labels_original = final_x_labels, # Pass the collected labels
                incorrect_vals = final_incorrect_vals,
                time_vals      = final_time_vals
            )

            # 3.5 Save the figure as a PNG file (requires kaleido installed)
            output_filename = f"{scenario_name}_{cat_name}.png" # Use output_filename
            try:
                fig.write_image(output_filename)
                print(f"Saved figure: {output_filename}")
            except Exception as e:
                print(f"Error saving figure {output_filename}: {e}")
                print("Please ensure 'kaleido' is installed (e.g., 'pip install kaleido') for image export.")


    print("Done creating all plots.")