# ComfyUI Android Remote

A native Android "Remote Control" for [ComfyUI](https://github.com/comfyanonymous/ComfyUI). This app allows you to trigger and manage image generations from your mobile device using a simplified interface, without the complexity of a full node editor.

## ✨ Features

- **Seamless Connectivity**: Quickly connect to your local or remote ComfyUI backend via LAN or VPN.
- **Workflow Library**: Browse, import, and manage your ComfyUI workflows.
- **Dynamic Forms**: Automatically generates simplified input forms (Prompts, Seed, Steps, CFG, etc.) based on the workflow structure.
- **Real-time Progress**: Track generation progress with step counters and live previews via WebSockets.
- **Premium Gallery**: A polished gallery experience with zoomable previews and historical sync with the server.
- **Background Support**: Stable connection management for ongoing generations.

## 🚀 Getting Started

### Prerequisites

1. A running instance of [ComfyUI](https://github.com/comfyanonymous/ComfyUI).
2. Enable the [API](https://github.com/comfyanonymous/ComfyUI/blob/master/script_examples/comfyui_api_tutorial.py) functionality (standard in modern versions).

### Connection

1. Ensure your Android device is on the same network as your ComfyUI host.
2. Enter the IP address and Port of your ComfyUI server in the app's settings.

## 🧪 Building and Testing

Requires JDK 17 and the Android SDK (platform 35). Android Studio sets both up.

```bash
./gradlew testDebugUnitTest      # unit tests (Windows: gradlew.bat)
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest --tests "*WorkflowCorpusTest*"   # one class
```

Test reports are written to `app/build/reports/tests/testDebugUnitTest/index.html`.

**Workflow corpus.** `app/src/test/resources/workflow-corpus/` holds official ComfyUI template workflows and a snapshot of a stock server's `/object_info` (see `MANIFEST.md` there). `WorkflowCorpusTest` converts every workflow and checks the result against the snapshot. `known-failures.json` lists workflows that are allowed to fail; it is `{}` and should stay that way: fix a new failure, or record it there with a reason.

**CI.** GitHub Actions (`.github/workflows/android.yml`) runs the unit tests and builds the debug APK on every push and pull request. The APK is attached to the run as the `app-debug` artifact for 7 days.

## 🛠 Tech Stack

- **Language**: [Kotlin 2.1.0](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Networking**: [Retrofit 2.11.0](https://square.github.io/retrofit/), [OkHttp 4.12.0](https://square.github.io/okhttp/)
- **Persistence**: [Room 2.6.1](https://developer.android.com/training/data-storage/room), [DataStore 1.1.1](https://developer.android.com/topic/libraries/architecture/datastore)
- **Image Loading**: [Coil 2.7.0](https://coil-kt.github.io/coil/)
- **Architecture**: Modern Android Architecture Components (ViewModel, Repository, DI)

## 🏗 Project Structure

- `app/`: Main Android application module.
- `app/src/main/java/`: Kotlin source code.
  - `data/`: DAO, Entities, API services, and Repositories.
  - `domain/`: Business logic and models.
  - `ui/`: Compose screens, components, and view models.
- `.gsd/`: Get Shit Done (GSD) project documentation and state.

## 📜 License

This project is licensed under the MIT License.
