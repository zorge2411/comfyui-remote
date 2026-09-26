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
| `GET /remote_helper/info` | | `{version, hf_token}` |
| `POST /remote_helper/models/probe` | `{url}` | `{size, gated}` (size in bytes, or null) |
| `POST /remote_helper/models/download` | `{url, directory, filename}` | The job. Errors come back as `{error}` with status 400, 403 or 409. |
| `GET /remote_helper/models/downloads` | | `[{id, filename, directory, total, done, status, error}]` |
| `POST /remote_helper/models/downloads/{id}/cancel` | | The job, or 404 |

`status` is `queued`, `downloading`, `done`, `error` or `cancelled`. Every change is also broadcast on ComfyUI's websocket as `{"type": "remote_helper.download", "data": <job>}`. While a download runs, updates are sent at most once a second.

Downloads run one at a time. A download is written to `<file>.part` and renamed when complete. On cancel or failure the `.part` file is deleted.

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
