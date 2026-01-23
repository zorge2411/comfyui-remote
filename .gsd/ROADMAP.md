# ROADMAP.md

> **Current Milestone**: Milestone 2: Gallery
> **Goal**: Show all generated images/videos with a premium experience.

## Must-Haves

- [ ] Image view with zoom
- [ ] Persistent storage for generated media metadata
- [ ] Gallery Grid UI

## Nice-to-Haves

- [ ] Video playback support
- [ ] Multi-select delete

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

**Status**: ✅ Done
**Objective**: Make it a real app.

- **Deliverables**:
  - [x] Local History (Room Database - via Phase 2)
  - [x] Execution Loop Complete (Image Result)
  - [x] Dynamic UI with Generated Image
  - [ ] App Icon (Partial)

### Phase 5: Import & Connection Refinements

**Status**: ✅ Done
**Objective**: Import existing workflows and display them in UI; save latest IP/Port (default 8188).
**Depends on**: Phase 4

**Tasks**:

- [x] Save latest connected IP + Port (Persistence)
- [x] Split IP and Port inputs (Default 8188)
- [x] Refine Workflow Import UI (Robustness Verified)

### Phase 6: Navigation & UX Flow

**Status**: ✅ Done
**Objective**: Implement seamless navigation with Bottom Bar and auto-redirects.
**Depends on**: Phase 5

**Tasks**:

- [x] Implement Bottom Navigation Bar (Home / Workflows)
- [x] Auto-navigate to Workflows on Connect
- [x] Home Button -> Connection Screen
- [x] Workflows Button -> Workflow List Screen

### Phase 7: Advanced Workflow Sync

**Status**: ✅ Done
**Objective**: Enable importing workflows from the server's execution history.
**Depends on**: Phase 6

**Tasks**:

- [x] Implement `GET /history` in API
- [x] Logic to extract node graph from history items
- [x] Add Sync button to Workflow List
- [x] Auto-import of server history entries into local DB
- [x] Auto-import of server history entries into local DB

### Phase 8: Gallery Foundation

**Status**: ✅ Done
**Objective**: Implement database persistence for generated images (metadata and local paths).

### Phase 9: Gallery UI

**Status**: ✅ Done
**Objective**: Create a grid-based gallery screen to browse history.

### Phase 10: Media Detail & Zoom

**Status**: ✅ Done
**Objective**: Full-screen view with pinch-to-zoom capabilities.

### Phase 11: Video Playback Support

**Status**: ✅ Done
**Objective**: Support viewing generated GIFs/MP4s within the gallery.

### Phase 12: Visual Branding (App Icon)

**Status**: ✅ Done
**Objective**: Modernize the app presence with a custom ComfyUI-inspired icon.

### Phase 13: Model Selection

**Status**: ✅ Done
**Objective**: Allow users to select different checkpoint models (safetensors) for image generation.

**Tasks**:

- [ ] Add API endpoint to fetch available models from ComfyUI server
- [ ] Add model selection dropdown to Dynamic Form
- [ ] Update workflow execution to use selected model
- [x] Persist last selected model per workflow

### Phase 14: Expanded API Support

**Status**: ✅ Done
**Objective**: achieve full API parity with standard Python examples, including `object_info` for dynamic node metadata.

**Tasks**:

- [x] Implement `getObjectInfo` endpoint
- [x] Create data models for Object Info
- [x] Verify `client_id` propagation in all requests

### Phase 15: Background Persistence

**Status**: 🚧 Planned
**Objective**: Ensure the application maintains connection and execution state when minimized.

**Tasks**:

- [ ] Create `ComfyApplication` class for app-level dependency management
- [ ] Extract WebSocket logic to `ConnectionRepository`
- [ ] Implement `ExecutionService` (Foreground Service) to keep connection alive
- [ ] Update `MainViewModel` to observe Repository instead of owning WebSocket
- [ ] Add Notification permission and display status notification

### Phase 16: Icon Refinement

**Status**: 🚧 Planned
**Objective**: Create 5 new premium icon options for user selection.

**Tasks**:

- [ ] Generate 5 distinct icon concepts
- [ ] Add chosen icon to project

### Backlog

- [ ] Swipe function in gallery (navigation between detail views)
- [ ] Option to select specific folder for saving generated images
