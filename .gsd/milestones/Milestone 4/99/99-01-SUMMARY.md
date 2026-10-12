# Plan 99.1 Summary: Extension v2 Queue

**Completed:** 2026-09-26

## Delivered

**Ordered queue in `downloader.py`** (`b2482e5`)
- `asyncio.Queue` is replaced by an ordered `_order` list plus a wake `Event`. Downloads still run one at a time.
- New methods:
  - `move(id, position)`: queued jobs only; the 1-based position is clamped;
  - `retry(id, exists)`: failed or cancelled jobs only; the job is re-queued at the end as a new job; 409 if the file now exists or is already pending;
  - `clear_finished()`.
- Cancelling a queued job takes it out of the order.
- `jobs()` lists the downloading job first, then queued jobs with a `position`, then finished jobs, newest first.
- Every change broadcasts `remote_helper.queue` with `{"jobs": [...]}`.

**Routes and version 2** (`ed95436`)
- `POST …/{id}/move {position}` (404/409/400), `POST …/{id}/retry` and `POST …/downloads/clear`.
- `info` returns version `"2"`.

**Docs and message** (`ad8ea22`)
- README: the new routes, the job fields, the websocket events table, and that the queue lives in memory.
- A 401 or 403 now reads "Gated or unavailable model…". The smoke test showed that Hugging Face also answers 401 for repos that don't exist.

## Verification

- `python -m unittest discover -s server/comfyui_remote_helper -p "test_*.py"`: 16 OK (9 validation, 7 new queue tests).
- Smoke test with the stub harness and real Hugging Face downloads:
  - info reports version 2;
  - `tiny-random-t5` moved to position 1 and ran second, after the job already downloading;
  - cancelling queued `gpt2` skipped it;
  - retry re-queued a failed job, and retrying a done job gave 409;
  - moving an unknown id gave 404;
  - clear removed the finished jobs;
  - 16 queue events were sent.
