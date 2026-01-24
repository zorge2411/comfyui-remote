# Phase 9 Research: Gallery UI

## Objective

Implement a multi-column grid layout to browse generated media history.

## Considerations

### 1. UI Components

- **`LazyVerticalGrid`**: The standard way to implement grids in Compose.
- **Card Layout**: Each item should show the image (via Coil), the workflow name, and maybe a timestamp.
- **TopAppBar**: Should probably have a title "Gallery" and perhaps a "Clear All" action.

### 2. Integration

- **Navigation**: Needs a new route `gallery` in `MainActivity`.
- **BottomBar**: Needs a 3rd item "Gallery".
- **ViewModel**: Needs to expose `allMedia` from `mediaRepository`.

### 3. Image URLs

The `GeneratedMediaEntity` stores `fileName`, `subfolder`, `serverHost`, and `serverPort`.

- **URL Reconstruction**:
  `http://{host}:{port}/view?filename={fileName}&subfolder={subfolder}&type=output`
- **Host Consistency**: If the user changes their server address in settings, older images might point to an unreachable host. For now, we use the stored host info per image.

### 4. Empty State

Need a clean UI when no images have been generated yet.

## Proposed Layout

- 3 columns.
- Square aspect ratio for images (`Modifier.aspectRatio(1f)`).
- Subtle "Workflow Name" overlay or caption.

## Navigation Logic

- Clicking an image in the grid should eventually navigate to the "Media Detail" (Phase 10).
- For Phase 9, we focus on the browsing experience.
