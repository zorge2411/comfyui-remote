# Phase 97 Research: Download Missing Models to the Server

**Date:** 2026-09-26
**Sources read:** `Comfy-Org/ComfyUI-Manager` `main` at `9c29dc6` (V3.42) and tag `4.3`; `comfyanonymous/ComfyUI` `master` at `79be670`; this repo's workflow corpus.

## Decision

**Build a small companion extension** (`server/comfyui_remote_helper/`, installed into `ComfyUI/custom_nodes`). Don't use ComfyUI-Manager. Chosen by the user on 2026-09-26, based on the findings below.

## Where model links come from

- Graph workflows list their models as `properties.models: [{name, url, directory}]`. The entries appear on nodes, on nodes inside `definitions.subgraphs[]`, and in a top-level `models` list. The ComfyUI frontend's missing-models dialog reads the same data.
- Corpus: 80 entries across 22 of the 32 workflows, 51 distinct `(directory, name)` pairs. Every entry has a `huggingface.co` URL.
  - Directories seen: `checkpoints`, `diffusion_models`, `text_encoders`, `vae`, `loras`, `controlnet`, `upscale_models`, `clip_vision`, `latent_upscale_models`, `background_removal`, `geometry_estimation`.
- The app loses these links today:
  - `MainViewModel.importWorkflowInternal` (`MainViewModel.kt:1098`) converts the graph with `GraphToApiConverter.convert`;
  - `WorkflowNormalizationService.normalize` then keeps only `class_type`, `inputs` and `_meta.title`;
  - the raw graph exists only as the `json` parameter of `importWorkflowInternal`.

## Server options

### ComfyUI core: no download endpoint

- The asset routes in `app/assets/api/routes.py` accept multipart uploads only. `from-hash` only registers files that are already present.
- `app/model_manager.py` has only GET `/experiment/models...` routes.
- `server.py` and `api_server/` contain no outbound fetch.

### ComfyUI-Manager 3.x (`glob/manager_server.py`)

- Install route: `POST /manager/queue/install_model` with body `{name, type, base, save_path, filename, url, ui_id}`. Then `POST /manager/queue/start`, and follow progress with `GET /manager/queue/status` or the `cm-queue-status` websocket event.
- Checks, in order:
  1. `is_allowed_security_level('middle')` (default level `normal`, so this passes);
  2. `check_whitelist_for_model`, which needs an entry in `model-list.json` whose `save_path`, `base` and `filename` all match;
  3. non-`.safetensors` files need their exact URL in the default channel list unless the level is `high`.
- **Only 6 of the 51 corpus models match the catalogue on directory + filename** (25 match on filename alone). Most template models would be rejected.
- No Hugging Face token handling in `manager_downloader.py`.

### ComfyUI-Manager v4 (`--enable-manager`, `/v2/manager/...`)

- Install route: `POST /v2/manager/queue/install_model` with body `{client_id, ui_id, name, type, url, filename, base?, save_path?}`. The new-UI path has no catalogue check.
- The task needs `is_allowed_security_level('middle+')`, which passes only on loopback or with `network_mode = personal_cloud`. A phone reaches the server over a non-loopback `--listen`, so the install **fails at run time** (as a `failed` task result, not an HTTP error) unless the user changes Manager's config.

## Companion extension: design notes

- **Routes:** register on `PromptServer.instance.routes`. Core also serves every route under an `/api` prefix.
- **Progress:** `PromptServer.instance.send_sync(event, data)` broadcasts to all websocket clients. The app's `handleMessage` (`MainViewModel.kt:887`) switches on `type` and ignores unknown events, so it needs a new branch.
- **Target folder:** `folder_paths.get_folder_paths(directory)[0]`. The directory must be a registered folder name.
- **Downloading:** aiohttp is always available in ComfyUI. Stream to `<file>.part`, then `os.replace` it into place.
- **HF gated models:** send `Authorization: Bearer $HF_TOKEN`. A 401 or 403 without a token means a gated model.
- **Security:** ComfyUI has no login, so anyone on the LAN can call these routes. Restrictions:
  - https URLs only, and only from `huggingface.co` and `github.com` (redirects to their CDNs are followed);
  - the filename must be a bare file name;
  - `.safetensors`, `.sft` or `.gguf` only, unless `REMOTE_HELPER_ALLOW_PICKLE=1` is set;
  - registered model folders only;
  - never overwrite an existing file.

## App facts relevant to planning

- Room is at version 11 (`data/AppDatabase.kt:30`) with `exportSchema = false` and `fallbackToDestructiveMigration()`.
  - `workflows.missingNodes` was added in 39d2597 without a migration. The new migration should add it when it's missing.
- `ComfyApiService.getModels(folder)` calls `/models/{folder}`.
- `fetchNodeMetadata()` runs on every change to CONNECTED (`MainViewModel.kt:1718`).
- `PromptValidator.Issue` has no value field. The missing file name appears only in `message`.
- The Missing Nodes banner is at `DynamicFormScreen.kt:152`. `PreflightDialog` is at `:700`.
- Tests are plain-JVM JUnit 4, with no mocking library. Corpus fixtures are loaded as in `domain/corpus/PromotedWidgetCorpusTest.kt:17`.

## Not verified

- Whether every model the templates list is actually used when the workflow runs. `usedBy` filtering against the converted prompt handles this.
- Download throughput and how the aiohttp session behaves under ComfyUI's event loop while a prompt is running. Check on the device.
