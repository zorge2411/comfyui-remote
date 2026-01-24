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
