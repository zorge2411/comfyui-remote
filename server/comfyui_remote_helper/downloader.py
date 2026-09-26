"""Model download queue for comfyui_remote_helper. Runs on ComfyUI's asyncio loop, one download at a time."""

import asyncio
import logging
import os
import shutil
import time
import uuid
from urllib.parse import urlparse

import aiohttp

EVENT = "remote_helper.download"
CHUNK = 1024 * 1024
SPARE_BYTES = 1024 ** 3
GATED_MESSAGE = "Gated model: accept its licence on huggingface.co and set HF_TOKEN on the server"

log = logging.getLogger("remote_helper")


def _auth_headers(url):
    token = os.environ.get("HF_TOKEN")
    if token and (urlparse(url).hostname or "").lower() == "huggingface.co":
        return {"Authorization": f"Bearer {token}"}
    return {}


def _timeout():
    return aiohttp.ClientTimeout(total=None, sock_connect=30, sock_read=60)


async def probe(url):
    """Returns {"size": int | None, "gated": bool} from a HEAD request that follows redirects."""
    async with aiohttp.ClientSession(timeout=_timeout()) as session:
        async with session.head(url, headers=_auth_headers(url), allow_redirects=True) as resp:
            size = None
            for r in (list(resp.history) + [resp]) if resp.status < 400 else []:
                value = r.headers.get("x-linked-size") or (r.headers.get("content-length") if r is resp else None)
                if value and value.isdigit() and int(value) > 0:
                    size = int(value)
                    break
            return {"size": size, "gated": resp.status in (401, 403)}


class Downloader:

    def __init__(self, send):
        self._send = send  # send(event, data): broadcasts over ComfyUI's websocket
        self._jobs = {}
        self._queue = None
        self._worker = None

    def jobs(self):
        return [self._public(j) for j in self._jobs.values()]

    def is_pending(self, target):
        return any(j["target"] == target and j["status"] in ("queued", "downloading") for j in self._jobs.values())

    def enqueue(self, url, directory, filename, target):
        job = {
            "id": uuid.uuid4().hex[:12], "url": url, "directory": directory, "filename": filename,
            "target": target, "total": None, "done": 0, "status": "queued", "error": None, "cancel": False,
        }
        self._jobs[job["id"]] = job
        if self._queue is None:
            self._queue = asyncio.Queue()
        if self._worker is None or self._worker.done():
            self._worker = asyncio.get_running_loop().create_task(self._run())
        self._queue.put_nowait(job["id"])
        self._emit(job)
        log.info("[remote_helper] queued %s -> %s", url, target)
        return self._public(job)

    def cancel(self, job_id):
        job = self._jobs.get(job_id)
        if job is None:
            return None
        if job["status"] == "queued":
            self._finish(job, "cancelled")
        elif job["status"] == "downloading":
            job["cancel"] = True
        return self._public(job)

    async def _run(self):
        while True:
            job = self._jobs.get(await self._queue.get())
            if job is None or job["status"] != "queued":
                continue
            try:
                await self._download(job)
            except asyncio.CancelledError:
                raise
            except Exception as e:  # report every failure to the app instead of killing the worker
                log.exception("[remote_helper] download failed: %s", job["url"])
                self._fail(job, f"{type(e).__name__}: {e}")

    async def _download(self, job):
        part = job["target"] + ".part"
        job["status"] = "downloading"
        self._emit(job)
        async with aiohttp.ClientSession(timeout=_timeout()) as session:
            async with session.get(job["url"], headers=_auth_headers(job["url"]), allow_redirects=True) as resp:
                if resp.status in (401, 403):
                    return self._fail(job, GATED_MESSAGE)
                if resp.status != 200:
                    return self._fail(job, f"Download failed: HTTP {resp.status}")
                job["total"] = resp.content_length
                folder = os.path.dirname(job["target"])
                os.makedirs(folder, exist_ok=True)
                if job["total"]:
                    free = shutil.disk_usage(folder).free
                    if free < job["total"] + SPARE_BYTES:
                        return self._fail(job, f"Not enough disk space on the server: {free / 1024 ** 3:.1f} GB free, "
                                               f"{job['total'] / 1024 ** 3:.1f} GB needed")
                last = 0.0
                try:
                    with open(part, "wb") as f:
                        async for chunk in resp.content.iter_chunked(CHUNK):
                            if job["cancel"]:
                                break
                            f.write(chunk)
                            job["done"] += len(chunk)
                            now = time.monotonic()
                            if now - last >= 1.0:
                                last = now
                                self._emit(job)
                except BaseException:
                    _remove(part)
                    raise
        if job["cancel"]:
            _remove(part)
            return self._finish(job, "cancelled")
        if job["total"] and job["done"] != job["total"]:
            _remove(part)
            return self._fail(job, f"Download incomplete: {job['done']} of {job['total']} bytes")
        os.replace(part, job["target"])
        log.info("[remote_helper] saved %s", job["target"])
        self._finish(job, "done")

    def _fail(self, job, message):
        _remove(job["target"] + ".part")
        job["error"] = message
        self._finish(job, "error")

    def _finish(self, job, status):
        job["status"] = status
        self._emit(job)

    def _emit(self, job):
        try:
            self._send(EVENT, self._public(job))
        except Exception:
            log.exception("[remote_helper] could not send progress")

    @staticmethod
    def _public(job):
        return {k: job[k] for k in ("id", "filename", "directory", "total", "done", "status", "error")}


def _remove(path):
    try:
        os.remove(path)
    except FileNotFoundError:
        pass
    except OSError:
        log.warning("[remote_helper] could not remove %s", path)
