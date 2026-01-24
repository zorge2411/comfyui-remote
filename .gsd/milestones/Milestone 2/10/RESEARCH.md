# Phase 10 Research: Media Detail & Zoom

## Objective

Implement a full-screen viewer for generated media with pinch-to-zoom and pan capabilities.

## Considerations

### 1. Zoom Implementation

- **Compose Transformation**: `Modifier.pointerInput` with `detectTransformGestures` is the standard way to handle pinch-to-zoom and panning.
- **State Management**: We need to track `scale`, `offset`, and `rotation` (though rotation might be optional).
- **Library Alternative**: `ZoomableImage` components from community libraries (like `ZoomableImage` in Accompanist or dedicated ones) can simplify this, but a custom implementation gives more control over the "premium" feel.

### 2. Navigation

- **Argument Passing**: Passing the entire `GeneratedMediaEntity` or just its `id`. Passing the `id` and fetching from the repository is safer for large objects, but since our entity is small, we could pass it as a JSON string or just pass the ID.
- **Route**: `media_detail/{mediaId}`.

### 3. UI Design

- **Immersive View**: Dark background, hide top/bottom bars when possible.
- **Actions**: "Save to Gallery" and "Delete" should be reachable.
- **Metadata**: Show workflow name and date in an optional overlay.

## Proposed Logic (Custom Zoom)

```kotlin
var scale by remember { mutableStateOf(1f) }
var offset by remember { mutableStateOf(Offset.Zero) }

Box(
    modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale *= zoom
                offset += pan
            }
        }
) {
    AsyncImage(
        model = url,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
    )
}
```

## Decisions

- Pass `mediaId` in navigation.
- Use a custom zoomable implementation for maximum flexibility.
- Dark theme for the detail view.
