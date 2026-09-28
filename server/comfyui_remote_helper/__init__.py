"""comfyui_remote_helper: lets the ComfyUI Remote phone app download missing models into this server's models folders.

Routes (also served under /api):
  GET  /remote_helper/info
  POST /remote_helper/models/probe              {url}
  POST /remote_helper/models/download           {url, directory, filename}
  GET  /remote_helper/models/downloads
  POST /remote_helper/models/downloads/{id}/cancel
  POST /remote_helper/models/downloads/{id}/move    {position}   (version 2)
  POST /remote_helper/models/downloads/{id}/retry                (version 2)
  POST /remote_helper/models/downloads/clear                     (version 2)
Version 3 also accepts civitai.com links and sends CIVITAI_TOKEN to civitai.com.
Per-job progress is broadcast as the websocket event "remote_helper.download", and the whole list as
"remote_helper.queue" whenever jobs or their order change. Downloads run one at a time.
"""

import logging
import os

import folder_paths
from aiohttp import web
from server import PromptServer

from .downloader import Downloader, probe
from .validation import RequestError, validate_request, validate_url

VERSION = "3"
NODE_CLASS_MAPPINGS = {}
NODE_DISPLAY_NAME_MAPPINGS = {}

log = logging.getLogger("remote_helper")
routes = PromptServer.instance.routes
downloader = Downloader(lambda event, data: PromptServer.instance.send_sync(event, data))


def _error(e):
    return web.json_response({"error": e.message}, status=e.status)


async def _json(request):
    try:
        return await request.json()
    except Exception:
        raise RequestError(400, "Expected a JSON body")


def _folder_map():
    return {name: list(value[0]) for name, value in folder_paths.folder_names_and_paths.items()}


@routes.get("/remote_helper/info")
async def info(request):
    return web.json_response({"version": VERSION, "hf_token": bool(os.environ.get("HF_TOKEN")),
                              "civitai_token": bool(os.environ.get("CIVITAI_TOKEN"))})


@routes.post("/remote_helper/models/probe")
async def probe_model(request):
    try:
        url = validate_url((await _json(request)).get("url"))
    except RequestError as e:
        return _error(e)
    except AttributeError:
        return _error(RequestError(400, "Expected a JSON object"))
    try:
        return web.json_response(await probe(url))
    except Exception as e:
        log.warning("[remote_helper] probe failed for %s: %s", url, e)
        return web.json_response({"size": None, "gated": False})


@routes.post("/remote_helper/models/download")
async def download_model(request):
    try:
        allow_pickle = os.environ.get("REMOTE_HELPER_ALLOW_PICKLE") == "1"
        url, directory, filename, target = validate_request(await _json(request), _folder_map(), os.path.exists, allow_pickle)
        if downloader.is_pending(target):
            raise RequestError(409, f"{filename} is already downloading")
    except RequestError as e:
        return _error(e)
    return web.json_response(downloader.enqueue(url, directory, filename, target))


@routes.get("/remote_helper/models/downloads")
async def list_downloads(request):
    return web.json_response(downloader.jobs())


@routes.post("/remote_helper/models/downloads/{id}/cancel")
async def cancel_download(request):
    job = downloader.cancel(request.match_info["id"])
    if job is None:
        return web.json_response({"error": "No such download"}, status=404)
    return web.json_response(job)


@routes.post("/remote_helper/models/downloads/{id}/move")
async def move_download(request):
    job_id = request.match_info["id"]
    try:
        position = int((await _json(request)).get("position"))
    except RequestError as e:
        return _error(e)
    except (AttributeError, TypeError, ValueError):
        return _error(RequestError(400, "Expected {\"position\": <1-based number>}"))
    if job_id not in {j["id"] for j in downloader.jobs()}:
        return web.json_response({"error": "No such download"}, status=404)
    job = downloader.move(job_id, position)
    if job is None:
        return web.json_response({"error": "Only queued downloads can be moved"}, status=409)
    return web.json_response(job)


@routes.post("/remote_helper/models/downloads/{id}/retry")
async def retry_download(request):
    try:
        job = downloader.retry(request.match_info["id"], os.path.exists)
    except RequestError as e:
        return _error(e)
    if job is None:
        return web.json_response({"error": "No such download"}, status=404)
    return web.json_response(job)


@routes.post("/remote_helper/models/downloads/clear")
async def clear_downloads(request):
    return web.json_response(downloader.clear_finished())


log.info("[remote_helper] version %s loaded; HF_TOKEN %s", VERSION, "set" if os.environ.get("HF_TOKEN") else "not set")
