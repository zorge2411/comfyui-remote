## Phase 99 Verification

Checked on 2026-09-27 against the code, the tests and the phone.

### Must-Haves

- [x] **One download at a time, in an order that can be changed.** 7 queue tests, and on the device moving `llama` up made it run next.
- [x] **Remove, retry and clear finished work on the server.** Device-verified from the Queue screen.
- [x] **Download all queues every missing model in one step.** The dialog showed 5 models and 46.6 GB. It was cancelled on the device because of the size; `downloadAll` issues the same per-model requests, which were verified.
- [x] **The Queue screen shows the server's model queue.** Device-verified: positions, progress, actions.
- [x] **Notifications while minimized.** Device-verified: progress id 2, finished summary id 3.
- [x] **One app-wide source of truth.** `ModelDownloadRepository` feeds the card, the Queue screen and `ExecutionService`, and they agreed throughout the check.

### Commands

- `gradlew.bat testDebugUnitTest assembleDebug`: 197 tests, 0 failures.
- `python -m unittest discover -s server/comfyui_remote_helper -p "test_*.py"`: 16 OK.

### Not verified

- Hiding reorder, retry and clear on an extension v1 server.
- The full 46.6 GB Download all run.

### Verdict: PASS
