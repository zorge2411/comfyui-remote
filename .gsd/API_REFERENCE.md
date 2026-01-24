# ComfyUI API Reference

> **Status**: Inherited from Python Client Snippets & Existing Implementation.

## Base Configuration

- **Default URL**: `http://127.0.0.1:8188`
- **WebSocket URL**: `ws://{server_address}/ws?clientId={client_id}`
- **Client ID**: UUID v4 (Must be consistent across HTTP and WS calls)

## Endpoints

### 1. System & Metadata

- **Get System Stats**
  - `GET /system_stats`
  - Returns: System status, queue counts.

- **Get Node Metadata**
  - `GET /object_info`
  - Purpose: Fetch definitions for all available nodes (inputs, outputs, widgets).
  - Used for: Dynamic form generation and graph-to-api conversion.

- **Get Models**
  - `GET /models/{category}`
  - Example Categories: `checkpoints`, `loras`, `embeddings`.
  - Returns: JSON List of filenames.

### 2. Execution

- **Queue Prompt**
  - `POST /prompt`
  - Body:

    ```json
    {
      "prompt": { ...api_workflow_json... },
      "client_id": "uuid-string"
    }
    ```

  - Returns: `{"prompt_id": "...", "number": 123}`

- **WebSocket Events**
  - Connect with `clientId`.
  - Listen for text messages.
  - **Execution Start**: `{"type": "execution_start", "data": {"prompt_id": "..."}}`
  - **Node Executing**: `{"type": "executing", "data": {"node": "node_id", "prompt_id": "..."}}`
  - **Workflow Finished**: Message type `executing` where `data.node` is `null`.

### 3. History & Outputs

- **Get History**
  - `GET /history/{prompt_id}`
  - Purpose: Retrieve results after execution finishes.
  - Response Structure:

    ```json
    {
      "prompt_id_xyz": {
        "outputs": {
          "node_id_9": {
            "images": [
              { "filename": "...", "subfolder": "...", "type": "..." }
            ]
          }
        }
      }
    }
    ```

- **Get Image (Binary)**
  - `GET /view`
  - Parameters:
    - `filename`: String
    - `subfolder`: String (optional)
    - `type`: String (e.g., "output", "temp")
  - Returns: Raw image data (bytes).

## Standard Workflow Execution Cycle

1. **Connect WebSocket** with `client_id`.
2. **GET /object_info** (optional, caching recommended) to validate nodes.
3. **POST /prompt** with workflow JSON and `client_id`.
   - Store returned `prompt_id`.
4. **Listen on WebSocket**:
   - Track progress via `executing` events (step/node updates).
   - Wait for `executing` event where `node` is `null` and `prompt_id` matches.
5. **GET /history/{prompt_id}**:
   - Parse `outputs` to find generated images.
6. **GET /view**:
   - Download images using parameters from history.

## Example Graph vs API Format

- **Graph Format**: Used by the UI (nodes with `pos`, `size`, `widgets_values`).
- **API Format**: Used by the Backend (Keyed by Node ID).
  - Structure: `{ "id": { "class_type": "...", "inputs": { ... } } }`
  - Links: Represented as `[ "from_node_id", output_slot_index ]`.
