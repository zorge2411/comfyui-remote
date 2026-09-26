"""Request checks for comfyui_remote_helper. Standard library only, so the tests run without ComfyUI."""

import os
from urllib.parse import urlparse

ALLOWED_HOSTS = {"huggingface.co", "github.com"}
SAFE_EXTS = {".safetensors", ".sft", ".gguf"}
PICKLE_EXTS = {".ckpt", ".pt", ".pth", ".bin"}
BLOCKED_FOLDERS = {"custom_nodes", "configs"}


class RequestError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def validate_url(url):
    if not isinstance(url, str) or not url:
        raise RequestError(400, "Missing url")
    parsed = urlparse(url)
    if parsed.scheme != "https":
        raise RequestError(400, "Only https URLs can be downloaded")
    host = (parsed.hostname or "").lower()
    if host not in ALLOWED_HOSTS:
        raise RequestError(403, f"Downloads are only allowed from {', '.join(sorted(ALLOWED_HOSTS))}, not {host or 'this URL'}")
    return url


def validate_filename(name, allow_pickle):
    if not isinstance(name, str) or not name.strip():
        raise RequestError(400, "Missing filename")
    if any(c in name for c in ("/", "\\", ":")) or ".." in name or name.startswith("."):
        raise RequestError(400, f"Invalid file name: {name}")
    ext = os.path.splitext(name)[1].lower()
    if ext in SAFE_EXTS:
        return name
    if ext in PICKLE_EXTS:
        if allow_pickle:
            return name
        raise RequestError(403, f"{ext} files can run code when loaded; set REMOTE_HELPER_ALLOW_PICKLE=1 on the server to allow them")
    raise RequestError(403, f"Unsupported model file type: {ext or name}")


def resolve_target(directory, filename, folder_map, exists):
    """folder_map: models folder name -> list of paths (from folder_paths.folder_names_and_paths)."""
    if not isinstance(directory, str) or not directory or directory in BLOCKED_FOLDERS:
        raise RequestError(400, f"Unknown models folder: {directory}")
    paths = folder_map.get(directory) or []
    if not paths:
        raise RequestError(400, f"Unknown models folder: {directory}")
    target = os.path.join(paths[0], filename)
    if exists(target):
        raise RequestError(409, f"{filename} is already in {directory}")
    return target


def validate_request(body, folder_map, exists, allow_pickle):
    """Returns (url, directory, filename, target_path) or raises RequestError."""
    if not isinstance(body, dict):
        raise RequestError(400, "Expected a JSON object")
    url = validate_url(body.get("url"))
    filename = validate_filename(body.get("filename"), allow_pickle)
    directory = body.get("directory")
    target = resolve_target(directory, filename, folder_map, exists)
    return url, directory, filename, target
