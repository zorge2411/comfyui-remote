#!/usr/bin/env python3
"""Export a trimmed, sanitized /object_info snapshot for the workflow corpus (Phase 89).

Fetches /object_info from a ComfyUI server, keeps only the node types used by the
fixture workflows, and empties every combo option list that holds file names
(checkpoints, LoRAs, input images, ...) so no local file names get committed.

Example:
  python scripts/corpus/export_object_info.py --url http://127.0.0.1:8188 \\
      --fixtures app/src/test/resources/workflow-corpus/workflows \\
      --out app/src/test/resources/workflow-corpus/object_info.json
"""
import argparse
import glob
import json
import os
import re
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from select_fixtures import backend_types  # noqa: E402

FILE_LIKE = re.compile(r"[\\/]|\.(safetensors|ckpt|pt|pth|bin|gguf|onnx|sft|png|jpe?g|webp|gif|mp4|webm|mov|wav|mp3|flac|json|yaml|glb|obj)$", re.I)


def is_file_list(options):
    return any(isinstance(o, str) and FILE_LIKE.search(o) for o in options)


def sanitize_input(spec):
    """Returns (spec, sanitized?) for one input definition."""
    if not isinstance(spec, list) or not spec:
        return spec, False
    # Legacy combo: [[options...], {config}]
    if isinstance(spec[0], list):
        if is_file_list(spec[0]):
            config = dict(spec[1]) if len(spec) > 1 and isinstance(spec[1], dict) else {}
            config.pop("default", None)
            return [[], config] if len(spec) > 1 else [[]], True
        return spec, False
    # V3 combo: ["COMBO", {"options": [...], ...}]
    if spec[0] == "COMBO" and len(spec) > 1 and isinstance(spec[1], dict):
        options = spec[1].get("options")
        if isinstance(options, list) and is_file_list(options):
            config = dict(spec[1])
            config["options"] = []
            config.pop("default", None)
            return ["COMBO", config], True
    return spec, False


def sanitize_node(node):
    count = 0
    for section in ("required", "optional"):
        inputs = node.get("input", {}).get(section)
        if not isinstance(inputs, dict):
            continue
        for key, spec in list(inputs.items()):
            inputs[key], changed = sanitize_input(spec)
            count += changed
    return count


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--url", default="http://127.0.0.1:8188", help="ComfyUI server base URL")
    ap.add_argument("--input", help="read a saved /object_info JSON file instead of fetching")
    ap.add_argument("--fixtures", required=True, help="directory of fixture workflow JSON files")
    ap.add_argument("--out", required=True, help="output path")
    args = ap.parse_args()

    if args.input:
        with open(args.input, encoding="utf-8") as f:
            info = json.load(f)
    else:
        with urllib.request.urlopen(args.url.rstrip("/") + "/object_info", timeout=120) as resp:
            info = json.load(resp)

    wanted = set()
    for path in glob.glob(os.path.join(args.fixtures, "*.json")):
        with open(path, encoding="utf-8") as f:
            wanted |= backend_types(json.load(f))

    out = {t: info[t] for t in sorted(wanted) if t in info}
    sanitized = sum(sanitize_node(node) for node in out.values())
    missing = sorted(wanted - info.keys())

    with open(args.out, "w", encoding="utf-8", newline="\n") as f:
        # Never sort keys inside a node: GraphToApiConverter maps widgets_values to inputs
        # in /object_info order, so the server's input order must be preserved.
        json.dump(out, f, indent=1, ensure_ascii=False)
        f.write("\n")

    print(f"{len(out)} node types written to {args.out}, {sanitized} file lists emptied")
    if missing:
        print(f"MISSING on server ({len(missing)}): {', '.join(missing)}")
        sys.exit(1)


if __name__ == "__main__":
    main()
