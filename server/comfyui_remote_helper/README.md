# comfyui_remote_helper

A small ComfyUI extension for the ComfyUI Remote phone app. When a workflow needs model files the server doesn't have, the app asks this extension to download them. Files go straight from Hugging Face or GitHub into the server's models folders.

Why it's needed:
- Core ComfyUI has no route for downloading a model from a URL.
- ComfyUI-Manager 3.x only installs models in its own catalogue, which leaves out most template models.
- Manager v4 refuses installs when ComfyUI listens on the network, and it has to for the phone.

It adds no nodes.

## Install

1. Copy or symlink this folder to `ComfyUI/custom_nodes/comfyui_remote_helper`.
2. Restart ComfyUI. The log shows `[remote_helper] version 1 loaded`.
3. Check it: `curl http://<server>:8188/remote_helper/info`

It needs nothing beyond what ComfyUI already installs (`aiohttp`).

## Settings (environment variables on the server)

| Variable | Effect |
|---|---|
| `HF_TOKEN` | Hugging Face access token, sent to huggingface.co only. Needed for gated models such as FLUX.1-dev. Accept the model's licence on huggingface.co with the same account first. |
| `REMOTE_HELPER_ALLOW_PICKLE=1` | Also allow `.ckpt`, `.pt`, `.pth` and `.bin` files. These formats can run code when loaded, so they're off by default. |

## Routes

Every route is also served under `/api/...`.

| Route | Body | Returns |
|---|---|---|
| `GET /remote_helper/info` | | `{version, hf_token}` (version `"2"`) |
| `POST /remote_helper/models/probe` | `{url}` | `{size, gated}` (size in bytes, or null) |
| `POST /remote_helper/models/download` | `{url, directory, filename}` | The job. Errors come back as `{error}` with status 400, 403 or 409. |
| `GET /remote_helper/models/downloads` | | The list (see below) |
| `POST /remote_helper/models/downloads/{id}/cancel` | | The job, or 404. A queued job is removed from the queue. |
| `POST /remote_helper/models/downloads/{id}/move` | `{position}` | The job. Moves a queued job to a 1-based position. 404 if unknown, 409 if not queued. (v2) |
| `POST /remote_helper/models/downloads/{id}/retry` | | The new job. Queues a failed or cancelled job again at the end. (v2) |
| `POST /remote_helper/models/downloads/clear` | | The remaining list. Removes done, failed and cancelled jobs. (v2) |

A job is `{id, filename, directory, total, done, status, error, position}`:
- `status` is `queued`, `downloading`, `done`, `error` or `cancelled`;
- `position` is the 1-based place in the queue for queued jobs, and null otherwise.

The list shows the downloading job first, then queued jobs in order, then finished jobs, newest first.

Downloads run one at a time. A download is written to `<file>.part` and renamed when complete. On cancel or failure the `.part` file is deleted.

The queue is kept in memory, so restarting ComfyUI clears it.

## Websocket events

| Event | Data | When |
|---|---|---|
| `remote_helper.download` | One job | On each status change, and at most once a second while downloading |
| `remote_helper.queue` | `{"jobs": [...]}`, the full list | Whenever jobs are added, removed, reordered or change status (v2) |

Version 1 clients ignore `remote_helper.queue` and keep working.

A 401 or 403 from Hugging Face is reported as "Gated or unavailable model": Hugging Face answers 401 for repos that don't exist, as well as for gated ones.

## Security

ComfyUI has no login, so anyone who can reach the server can call these routes. To limit what that allows, a download is accepted only when:
- the URL is https, on `huggingface.co` or `github.com` (redirects to their CDNs are followed);
- the file name is a plain name, with no folders, `..`, `\` or `:`;
- the file type is `.safetensors`, `.sft` or `.gguf` (plus pickle formats, only if you opt in);
- the target is a registered ComfyUI models folder (for example `checkpoints` or `loras`), never `custom_nodes`;
- the file isn't already there, because existing files are never overwritten;
- the disk would still have at least 1 GB free after the download.

## Tests

```
python -m unittest discover -s custom_nodes/comfyui_remote_helper -p "test_*.py"
```
