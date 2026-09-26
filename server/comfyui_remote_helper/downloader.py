"""Model download queue for comfyui_remote_helper. Runs on ComfyUI's asyncio loop, one download at a time."""

import asyncio
import logging
import os
import shutil
import time
import uuid
from urllib.parse import urlparse

import aiohttp

try:
    from .validation import RequestError
except ImportError:  # imported as a top-level module by the tests
    from validation import RequestError

EVENT = "remote_helper.download"
QUEUE_EVENT = "remote_helper.queue"
FINISHED = ("done", "error", "cancelled")
CHUNK = 1024 * 1024
SPARE_BYTES = 1024 ** 3
# Hugging Face also answers 401 for repos that don't exist, so this can't say "gated" for sure
GATED_MESSAGE = ("Gated or unavailable model: if it is gated, accept its licence on huggingface.co "
                 "and set HF_TOKEN on the server")

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
    """Queued model downloads, run one at a time in an order clients can change."""

    def __init__(self, send):
        self._send = send  # send(event, data): broadcasts over ComfyUI's websocket
        self._jobs = {}
        self._order = []  # ids of queued jobs, next first
        self._wake = None
        self._worker = None

    def jobs(self):
        """The downloading job, then queued jobs in order (with a 1-based position), then finished jobs, newest first."""
        active = [j for j in self._jobs.values() if j["status"] == "downloading"]
        queued = [self._jobs[i] for i in self._order]
        finished = sorted((j for j in self._jobs.values() if j["status"] in FINISHED),
                          key=lambda j: j["finished_at"], reverse=True)
        result = [self._public(j) for j in active]
        result += [dict(self._public(j), position=n + 1) for n, j in enumerate(queued)]
        result += [self._public(j) for j in finished]
        return result

    def is_pending(self, target):
        return any(j["target"] == target and j["status"] in ("queued", "downloading") for j in self._jobs.values())

    def enqueue(self, url, directory, filename, target):
        job = {
            "id": uuid.uuid4().hex[:12], "url": url, "directory": directory, "filename": filename,
            "target": target, "total": None, "done": 0, "status": "queued", "error": None, "cancel": False,
            "finished_at": None,
        }
        self._jobs[job["id"]] = job
        self._order.append(job["id"])
        self._start_worker()
        self._emit(job)
        self._emit_queue()
        log.info("[remote_helper] queued %s -> %s", url, target)
        return self._public(job)

    def cancel(self, job_id):
        job = self._jobs.get(job_id)
        if job is None:
            return None
        if job["status"] == "queued":
            self._order.remove(job_id)
            self._finish(job, "cancelled")
        elif job["status"] == "downloading":
            job["cancel"] = True
        return self._public(job)

    def move(self, job_id, position):
        """Moves a queued job to a 1-based position in the queue (clamped). None when the job isn't queued."""
        if job_id not in self._order:
            return None
        self._order.remove(job_id)
        index = min(max(int(position), 1), len(self._order) + 1) - 1
        self._order.insert(index, job_id)
        self._emit_queue()
        return self._public(self._jobs[job_id])

    def retry(self, job_id, exists):
        """Queues a failed or cancelled job again at the end, as a new job."""
        job = self._jobs.get(job_id)
        if job is None:
            return None
        if job["status"] not in ("error", "cancelled"):
            raise RequestError(409, f"{job['filename']} is {job['status']}, not failed or cancelled")
        if exists(job["target"]):
            raise RequestError(409, f"{job['filename']} is already in {job['directory']}")
        if self.is_pending(job["target"]):
            raise RequestError(409, f"{job['filename']} is already downloading")
        del self._jobs[job_id]
        return self.enqueue(job["url"], job["directory"], job["filename"], job["target"])

    def clear_finished(self):
        for job_id in [i for i, j in self._jobs.items() if j["status"] in FINISHED]:
            del self._jobs[job_id]
        self._emit_queue()
        return self.jobs()

    def _start_worker(self):
        if self._wake is None:
            self._wake = asyncio.Event()
        if self._worker is None or self._worker.done():
            self._worker = asyncio.get_running_loop().create_task(self._run())
        self._wake.set()

    async def _run(self):
        while True:
            if not self._order:
                self._wake.clear()
                await self._wake.wait()
                continue
            job = self._jobs[self._order.pop(0)]
            try:
                await self._download(job)
            except asyncio.CancelledError:
                raise
            except Exception as e:  # report every failure to the app instead of killing the worker
                log.exception("[remote_helper] download failed: %s", job["url"])
                self._fail(job, f"{type(e).__name__}: {e}")

    async def _download(self, job):
        part = job["target"] + ".part"
        self._set_status(job, "downloading")
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
        job["finished_at"] = time.time()
        self._set_status(job, status)

    def _set_status(self, job, status):
        job["status"] = status
        self._emit(job)
        self._emit_queue()

    def _emit(self, job):
        try:
            self._send(EVENT, self._public(job))
        except Exception:
            log.exception("[remote_helper] could not send progress")

    def _emit_queue(self):
        try:
            self._send(QUEUE_EVENT, {"jobs": self.jobs()})
        except Exception:
            log.exception("[remote_helper] could not send the queue")

    @staticmethod
    def _public(job):
        public = {k: job[k] for k in ("id", "filename", "directory", "total", "done", "status", "error")}
        public["position"] = None
        return public


def _remove(path):
    try:
        os.remove(path)
    except FileNotFoundError:
        pass
    except OSError:
        log.warning("[remote_helper] could not remove %s", path)
