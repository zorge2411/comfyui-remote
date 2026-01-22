# SPEC.md — Project Specification

> **Status**: `FINALIZED`

## Vision

A native Android "Remote Control" for ComfyUI that allows users to generate images from their mobile device without the complexity of a node editor. The app connects to a local ComfyUI instance, presents a simplified form for selected workflows, and provides a polished, premium gallery experience for viewing and saving results.

## Goals

1. **Seamless Connectivity**: Auto-discovery or simple IP/Port connection to ComfyUI backend via LAN/VPN.
2. **Workflow library**: Browse, create (import/save-as), update (rename), and delete workflows saved on the host.
3. **Simplified Interaction**: Dynamic form generation for common inputs (Prompt, Negative Prompt, Seed, Steps, CFG, Image Size) based on the workflow.
4. **Instant Feedback**: Real-time progress tracking (step counters, previews) via WebSocket.
5. **Gallery & Management**: Zoomable image preview, save to device, and history view.

## Non-Goals (Out of Scope)

- **Graph Editing**: No node creation, wiring, or complex graph manipulation on the phone.
- **Hardware Inference**: No local image generation on the Android device itself; relies entirely on the host.
- **Plugin Management**: Installing/updating custom nodes is done on the host.

## Users

- **The ComfyUI Power User**: Wants to trigger generations from the couch or away from the desk.
- **The "Consumer"**: Family/friends connecting to a shared hosted instance to play with AI art without learning nodes.

## Constraints

- **Platform**: Native Android (Kotlin, Jetpack Compose).
- **Network**: Local Network (LAN) or VPN access required.
- **API**: Standard ComfyUI API (WebSocket + HTTP).
- **Aesthetics**: "Premium" feel — dark mode, smooth animations, blurred glass effects.

## Success Criteria

- [ ] Connect to a ComfyUI server instance.
- [ ] Parse a standard workflow and render a functional input form.
- [ ] Queue a job and receive the generated image.
- [ ] Application feels responsive and visually polished.
