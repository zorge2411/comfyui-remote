# Plan 97.2 Summary: Companion Server Extension

**Completed:** 2026-09-26

## Delivered (`server/comfyui_remote_helper/`)

- `validation.py` plus 9 unittest cases (`c89d792`). A request is accepted only when:
  - the URL is https on huggingface.co or github.com;
  - the file name is a bare name;
  - the type is `.safetensors`, `.sft` or `.gguf`; pickle types need `REMOTE_HELPER_ALLOW_PICKLE=1`;
  - the folder is a registered models folder other than `custom_nodes` or `configs`;
  - the file doesn't exist yet (409 otherwise).
- `__init__.py` and `downloader.py` (`f210aa2`):
  - five routes: info, probe, download, list and cancel;
  - one aiohttp download at a time, written to `.part` and then `os.replace`d into place;
  - progress events `remote_helper.download` at most once a second, plus one on every status change;
  - `HF_TOKEN` sent for huggingface.co only;
  - a 401 or 403 from the server gives the gated-model message;
  - a disk-space check before writing;
  - a length check against Content-Length after downloading.
- `README.md` (`c785814`): installation, environment variables, routes, security.

## Verification

- `python -m unittest discover -s server/comfyui_remote_helper -p "test_*.py"`: 9 OK.
- Smoke test with stub `server` / `folder_paths` modules and aiohttp `TestClient`, downloading from huggingface.co for real:
  - info works;
  - probe of `tiny-random-bert` gives 520212 bytes;
  - a URL on another host gives 403;
  - the download finishes and the file is in the folder, with events queued → downloading → done;
  - a second request while it is downloading, and again once the file exists, gives 409;
  - FLUX.1-dev without a token: probe reports gated with size null (a fix was made here), and the download fails with the gated message;
  - cancelling the 548 MB gpt2 download mid-way sets it to cancelled and leaves no `.part` file.
- Not yet run inside a real ComfyUI. That is the device check in Plan 97.3.
