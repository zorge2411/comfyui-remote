"""comfyui_remote_helper: lets the ComfyUI Remote phone app download missing models into this server's models folders.

Routes (also served under /api):
  GET  /remote_helper/info
  POST /remote_helper/models/probe              {url}
  POST /remote_helper/models/download           {url, directory, filename}
  GET  /remote_helper/models/downloads
  POST /remote_helper/models/downloads/{id}/cancel
Progress is broadcast as the websocket event "remote_helper.download".
"""

import logging
import os

import folder_paths
from aiohttp import web
from server import PromptServer

from .downloader import Downloader, probe
from .validation import RequestError, validate_request, validate_url

VERSION = "1"
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
    return web.json_response({"version": VERSION, "hf_token": bool(os.environ.get("HF_TOKEN"))})


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


log.info("[remote_helper] version %s loaded; HF_TOKEN %s", VERSION, "set" if os.environ.get("HF_TOKEN") else "not set")
