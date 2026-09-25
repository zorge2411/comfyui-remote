#!/usr/bin/env python3
"""Survey ComfyUI graph workflows for the workflow compatibility corpus (Phase 89).

Prints one line per graph-format workflow: node count, size, feature flags and,
with --object-info, node types missing from that object_info. Ends with a
per-feature count so coverage targets can be checked at a glance.

Source templates: pip download --no-deps comfyui-workflow-templates-json==<version>
then unzip the wheel; templates live in comfyui_workflow_templates_json/templates/.
"""
import argparse
import collections
import glob
import json
import os
import sys

# Node types that exist only in the ComfyUI frontend; they never reach /prompt.
FRONTEND_ONLY = {"Reroute", "PrimitiveNode", "Note", "MarkdownNote", "SetNode", "GetNode"}

FEATURES = ["subgraph", "linked_widget", "autogrow", "bypass", "mute",
            "reroute", "primitive_node", "note", "load_image", "video_out", "api_node"]


def all_nodes(graph):
    nodes = list(graph.get("nodes", []))
    for sub in graph.get("definitions", {}).get("subgraphs", []):
        nodes += sub.get("nodes", [])
    return nodes


def subgraph_ids(graph):
    return {sub.get("id") for sub in graph.get("definitions", {}).get("subgraphs", [])}


def backend_types(graph):
    """Node types that must exist on the server for this workflow to convert."""
    subs = subgraph_ids(graph)
    return {n.get("type") for n in all_nodes(graph)
            if n.get("type") and n.get("type") not in FRONTEND_ONLY and n.get("type") not in subs}


def features(graph):
    found = set()
    if graph.get("definitions", {}).get("subgraphs"):
        found.add("subgraph")
    for n in all_nodes(graph):
        t = n.get("type") or ""
        mode = n.get("mode", 0)
        if mode == 4:
            found.add("bypass")
        if mode == 2:
            found.add("mute")
        if t == "Reroute":
            found.add("reroute")
        if t == "PrimitiveNode":
            found.add("primitive_node")
        if t in ("Note", "MarkdownNote"):
            found.add("note")
        if t == "LoadImage":
            found.add("load_image")
        if t in ("SaveVideo", "CreateVideo", "SaveAnimatedWEBP", "VHS_VideoCombine"):
            found.add("video_out")
        for inp in n.get("inputs") or []:
            if "." in (inp.get("name") or ""):
                found.add("autogrow")
            if inp.get("widget") and inp.get("link") is not None:
                found.add("linked_widget")
    return found


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("templates_dir", help="directory of workflow JSON files")
    ap.add_argument("--object-info", help="object_info.json to check node types against")
    args = ap.parse_args()

    object_info = None
    api_types = set()
    if args.object_info:
        with open(args.object_info, encoding="utf-8") as f:
            object_info = json.load(f)
        api_types = {k for k, v in object_info.items() if isinstance(v, dict) and v.get("api_node")}

    totals = collections.Counter()
    total_size = 0
    count = 0
    missing_any = 0
    for path in sorted(glob.glob(os.path.join(args.templates_dir, "*.json"))):
        try:
            with open(path, encoding="utf-8") as f:
                graph = json.load(f)
        except (OSError, ValueError) as e:
            print(f"SKIP {os.path.basename(path)}: {e}", file=sys.stderr)
            continue
        if not isinstance(graph, dict) or "nodes" not in graph:
            continue
        feats = features(graph)
        types = backend_types(graph)
        # object_info marks paid API nodes with api_node=true; without it, fall back to the template naming.
        if types & api_types or (object_info is None and os.path.basename(path).startswith("api_")):
            feats.add("api_node")
        size = os.path.getsize(path)
        total_size += size
        count += 1
        totals.update(feats)
        line = f"{os.path.basename(path)}\t{len(all_nodes(graph))} nodes\t{size // 1024} KB\t{','.join(sorted(feats))}"
        if object_info is not None:
            missing = sorted(types - object_info.keys())
            if missing:
                missing_any += 1
                line += f"\tMISSING: {','.join(missing)}"
        print(line)

    print(f"\n{count} graph workflows, {total_size / 1024 / 1024:.2f} MB")
    for feat in FEATURES:
        print(f"  {feat}: {totals[feat]}")
    if object_info is not None:
        print(f"  with missing node types: {missing_any}")


if __name__ == "__main__":
    main()
