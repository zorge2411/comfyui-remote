# STATE.md

- **Current Phase**: Phase 66 (Complete)
- **Task**: Done
- **Status**: ✅ Phase 66 Completed (Heuristic Link Flattening)

## Achieved in Phase 62

- **Prompt Copy Button**: Added a copy-to-clipboard button to the trailing icons of text prompts in the workflow generator.
>
> ## Achieved in Phase 61
>
> - **Back Button on Workflow Screen**: Implemented a back button on the workflow generation screen for improved navigation.
>
> ## Achieved in Phase 59
>
> - **Multi-Server Persistence**: Implemented `ServerProfile` list storage in `UserPreferencesRepository` using Gson for JSON serialization in DataStore.
> - **Dropdown UI**: Replaced the plain text IP field with an `ExposedDropdownMenuBox` showing recently used servers.
> - **Profile Management**: Added auto-save on successful connection and manual delete (garbage icon) in the dropdown list.
> - **Intelligent Sorting**: New connections are automatically added to the top, maintaining a FIFO limit of 5 server profiles.

>
> ## Achieved in Phase 57
>
> - Implemented input validation for IP and Port fields on the Connection Screen.
> - Added UI error messaging for empty or non-numeric inputs.
> - Prevented malformed WebSocket URL generation that caused app crashes.
>
> ## Achieved in Phase 56
>
> - Automatically dismiss software keyboard on connection trigger.
> - Shared `connectAction` across button and keyboard inputs.
>
> ## Achieved in Phase 55
>
> - Added "Enter" key support to IP and Port fields for faster connection.
> - Forced single-line inputs to prevent newline insertion in connection fields.
>
> ## Achieved in Phase 54
>
> - Implemented Android Adaptive Icon support.
> - Unified Icon Background Color (#05241D).
>
> ## Achieved in Optimization Session (UI Performance)
>
> - **Google Photos Smoothness**: Implemented Scroll-Aware Shared Transitions.
> - **Memory Optimization**: Switched grid thumbnails to `RGB_565`.
>
> ## Achieved in Phase 53
>
> - Enhanced `GraphToApiConverter` to preserve node titles (`_meta`).
>
> ## Achieved in Phase 52
>
> - Implemented `SelectionInput` for generic dropdown support.
>
> ## Achieved in Phase 51
>
> - Refactored Synchronize Logic to use Batch Insterts.
> - Configured Global Coil ImageLoader with Caching & Crossfade.
>
> ## Achieved in Milestone 2
>
> - Full Gallery Implementation (Zoom, Paging, Gestures).
> - Workflow History System.
> - Persistent Background Connection.
>
## Current Position

- **Phase**: Phase 66 (Complete)
- **Task**: Done
- **Status**: ✅ Phase 66 Completed (Heuristic Link Flattening)

## Achieved in Phase 64

- **Img2Img Support**: Enabled using device images as input for workflows.
- **Auto-Upload**: Images are uploaded to the server automatically before execution.
- **Workflow Patching**: Logic to inject uploaded filenames into the execution graph.
- **Handling Missing Metadata**: Fixed issue where `LoadImage` nodes were stripped during import if server metadata was missing.

## Achieved in Phase 65

- **Robust Converter**: Implemented fallback logic in `GraphToApiConverter` to preserve nodes with missing metadata instead of skipping them.
- **Heuristic Mapping**: Added best-effort input mapping for primitive widgets (int, float, string) and images in unknown nodes.
- **UI Feedback**: Confirmed warning display for workflows with missing node definitions.
- **Verification**: Added unit tests to ensure fallback logic produces valid JSON.

## Achieved in Phase 66

- **Link Flattening**: Implemented recursive bypassing of unknown/phantom nodes (e.g. Reroutes) to connect consumers directly to producers.
- **Validation**: Added unit tests covering single-hop, multi-hop, and ambiguous link resolution.
- **Server Compatibility**: Eliminated `HTTP 400` errors caused by sending frontend-only nodes to the backend.

## Next Steps

1. /execute 63

## Achieved in Phase 60

- **Direct Navigation**: Clicking a generated image in the form navigates directly to the full gallery view.
- **Improved UX**: Seamless transition from creation to consumption/management of media.

## Achieved in Task: Add Todo

- Captured feature request: "link from resulting image to gallery" in `.gsd/TODO.md`.
- Captured feature request: "copy all text button in prompt text box" in `.gsd/TODO.md`.
- Captured feature request: "support for image to image workflow nodes" in `.gsd/TODO.md`.
- Captured feature request: "positive prompt text input must be the most top one in workflow" in `.gsd/TODO.md`.
- Captured bug report: "fix revert to old icon caused by pull request" in `.gsd/TODO.md`.
