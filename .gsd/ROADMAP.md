# ROADMAP.md

> **Current Phase**: Phase 1
> **Milestone**: v1.0 MVP

## Must-Haves (from SPEC)

- [ ] Connect to ComfyUI (HTTP/WS)
- [ ] List Workflows
- [ ] Dynamic Input Form
- [ ] Image Preview & Save

## Phases

### Phase 1: Foundation & Connectivity

**Status**: ✅ Done
**Objective**: Establish the Android project structure and prove communication with ComfyUI.

- **Deliverables**:
  - [x] Android Studio Project (Kotlin/Compose)
  - [x] Network Layer (Retrofit/OkHttp + WebSocket)
  - [x] "Server Status" Indicator (Green light when connected)
  - [x] Basic settings screen for Host IP/Port

### Phase 2: Workflow Parsing & Management

**Status**: ✅ Done
**Objective**: Parse ComfyUI workflows and manage them locally.

- **Deliverables**:
  - [x] Room Database for Workflow Storage
  - [x] Input Parsing (Find Prompt/Seed/Steps)
  - [x] UI: Workflow List & Import (Paste JSON)
  - [x] CRUD Operations for Workflows

### Phase 3: Execution & Preview

**Status**: ✅ Done
**Objective**: The core loop — Generate and View.

- **Deliverables**:
  - [x] Execution Logic (Queue Prompt API)
  - [x] Dynamic Form UI (Inputs for Prompt/Seed/Steps)
  - [x] State Management (Queued/Executing/Idle)
  - [ ] Image Result Fetching & Caching (Coil) (Deferred to Phase 4)

### Phase 4: Polish & Persistence

**Status**: ⬜ Not Started
**Objective**: Make it a real app.

- **Deliverables**:
  - Local History (Room Database)
  - Download/Share intents
  - UI Polish (Animations, Transitions, Haptics)
  - App Icon & Branding
