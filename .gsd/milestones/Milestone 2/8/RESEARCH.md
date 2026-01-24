# Phase 8 Research: Gallery Foundation

## Objective

Design the persistence layer for tracking generated media (images/videos) and their associated metadata.

## Considerations

### 1. Data Schema (`StoredResult` Entity)

We need to track:

- `id`: Primary Key (Auto-gen)
- `workflowId`: Foreign Key to `WorkflowEntity` (optional, if workflow deleted)
- `workflowName`: Snapshot of the name at execution time.
- `fileName`: The name returned by ComfyUI (e.g., `ComfyUI_00001_.png`).
- `subfolder`: Server subfolder (if any).
- `serverHost`: Host info (to reconstruct full URL if IP changes).
- `timestamp`: Execution time.
- `promptJobId`: The prompt ID from history.
- `localUri`: Path to the locally cached/saved version (nullable).
- `mediaType`: "IMAGE" or "VIDEO".

### 2. Storage Strategy

- **ComfyUI Server**: We can always fetch the image from `http://{host}:{port}/view?filename={...}&subfolder={...}&type=output`.
- **Local Cache**: Using `Coil` for display handles caching.
- **Permanent Save**: If the user clicks "Save", we copy the image to the device's public media storage (Gallery) and update `localUri`.

### 3. Automatic Tracking

- The `MainViewModel` successfully receives the image data via WebSocket (or fetches it via history).
- **Modification needed**: Upon successful generation completion, the `MainViewModel` (or a dedicated `GalleryRepository`) should insert a record into the database.

## Proposed Entity

```kotlin
@Entity(tableName = "generated_media")
data class GeneratedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workflowName: String,
    val fileName: String,
    val subfolder: String?,
    val serverHost: String,
    val serverPort: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaType: String = "IMAGE"
)
```

## Decisions

- Use Room for metadata.
- Rely on Coil for initial display/caching.
- Phase 8 focused on getting these records into the DB automatically.
