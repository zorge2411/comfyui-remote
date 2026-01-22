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

**Status**: ⬜ Not Started
**Objective**: Establish the Android project structure and prove communication with ComfyUI.

- **Deliverables**:
  - Android Studio Project (Kotlin/Compose)
  - Network Layer (Retrofit/OkHttp + WebSocket)
  - "Server Status" Indicator (Green light when connected)
  - Basic settings screen for Host IP/Port

### Phase 2: Workflow Parsing & Forms

**Status**: ⬜ Not Started
**Objective**: Read workflows and turn them into UI.

- **Deliverables**:
  - Workflow Repository (Fetch list from server)
  - **CRUD Operations**:
    - Delete Workflow
    - Rename Workflow
    - Duplicate/Save As (Parameter Preset)
  - JSON Parsing Logic (Identify KSampler, Prompt nodes)
  - Composable Form Builder (Text inputs, Sliders, Number fields)

### Phase 3: Execution & Preview

**Status**: ⬜ Not Started
**Objective**: The core loop — Generate and View.

- **Deliverables**:
  - Queue Prompt API integration
  - Execution Progress UI (ProgressBar driven by WS)
  - Image Result Fetching & Caching (Coil)
  - Full-screen Image Viewer

### Phase 4: Polish & Persistence

**Status**: ⬜ Not Started
**Objective**: Make it a real app.

- **Deliverables**:
  - Local History (Room Database)
  - Download/Share intents
  - UI Polish (Animations, Transitions, Haptics)
  - App Icon & Branding
