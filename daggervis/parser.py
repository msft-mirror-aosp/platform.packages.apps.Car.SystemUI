#!/usr/bin/python3
"""A script for parsing and filtering component dot file.
Adapted from vendor/google_clockwork/packages/SystemUI/daggervis/parser.py

Input: input_dot_file output_dot_file [beginning_nodes_filter]
Output: create a new dot file with styles applied. The output dot file will only contain nodes
reachable from the beginning_nodes_filter if it's specified.
"""
import sys
import os
try:
  import pydot
except ImportError as e:
  print("Error: python3-pydot is not installed. Please run \"sudo apt install python3-pydot\" first.", file=sys.stderr)
  sys.exit(1)

def main():
  # Parse args
  if len(sys.argv) < 2:
    print("Error: please specify an input dot file", file=sys.stderr)
    sys.exit(1)
  if len(sys.argv) < 3:
    print("Error: please specify an output dot file", file=sys.stderr)
    sys.exit(1)
  input_path = sys.argv[1]
  output_path = sys.argv[2]
  if len(sys.argv) > 3:
    beginning_nodes_filter= sys.argv[3]
  else:
    beginning_nodes_filter= None

  # Load graph
  try:
    graph = pydot.graph_from_dot_file(input_path)[0]
  except Exception as e:
    print("Error: unable to load dot file \"" + input_path + "\"", file=sys.stderr)
    sys.exit(1)
  print("Loaded dot file from " + input_path)

  # Trim graph
  if beginning_nodes_filter!= None:
    trim_graph(graph, beginning_nodes_filter)

  # Add styles
  style_graph(graph)

  with open(output_path, "w") as f:
    f.write(str(graph))
    print("Saved output dot file " + output_path)

"""
Trim a graph by only keeping nodes/edges reachable from beginning nodes.
"""
def trim_graph(graph, beginning_nodes_filter):
  beginning_node_names = set()
  all_nodes = graph.get_nodes()
  for n in all_nodes:
    if beginning_nodes_filter in get_label(n):
      beginning_node_names.add(n.get_name())
  if len(beginning_node_names) == 0:
    print("Error: unable to find nodes matching \"" + beginning_nodes_filter + "\"", file=sys.stderr)
    sys.exit(1)
  filtered_node_names = set()
  all_edges = graph.get_edges()
  for node_name in beginning_node_names:
    dfs(all_edges, node_name, filtered_node_names)
  cnt_trimmed_nodes = 0
  for node in all_nodes:
    if not node.get_name() in filtered_node_names:
      graph.del_node(node.get_name())
      cnt_trimmed_nodes += 1
  cnt_trimmed_edges = 0
  for edge in all_edges:
    if not edge.get_source() in filtered_node_names:
      graph.del_edge(edge.get_source(), edge.get_destination())
      cnt_trimmed_edges += 1
  print("Trimed " + str(cnt_trimmed_nodes) + " nodes and " + str(cnt_trimmed_edges) + " edges")

def dfs(all_edges, node_name, filtered_node_names):
  if node_name in filtered_node_names:
    return
  filtered_node_names.add(node_name)
  for edge in all_edges:
    if edge.get_source() == node_name:
      dfs(all_edges, edge.get_destination(), filtered_node_names)

"""
Apply styles to the dot graph.
"""
def style_graph(graph):
  for n in graph.get_nodes():
    label = get_label(n)
    # Style SystemUI nodes
    if "com.android.systemui" in label:
      n.obj_dict["attributes"]["color"] = "burlywood"
      n.obj_dict["attributes"]["shape"] = "box"
      n.add_style("filled")
    # Style CarSystemUI nodes
    elif ("car" in label):
      n.obj_dict["attributes"]["color"] = "darkolivegreen1"
      n.add_style("filled")

    # Trim common labels
    trim_replacements = [("java.util.", ""), ("javax.inject.", "") , ("com.", "c."),
                         ("google.", "g."), ("android.", "a."), ("car.", "c."),
                         ("java.lang.", ""), ("dagger.Lazy", "Lazy"), ("java.util.function.", "")]
    for (before, after) in trim_replacements:
      if before in label:
         n.obj_dict["attributes"]["label"] = label = label.replace(before, after)

def get_label(node):
  try:
    return node.obj_dict["attributes"]["label"]
  except Exception:
    return ""

if __name__ == "__main__":
    main()