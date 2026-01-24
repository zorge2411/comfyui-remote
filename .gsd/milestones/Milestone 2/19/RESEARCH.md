# Research: Phase 19 - Custom Storage Folder Selection

Selection of a specific device folder for saving media requires navigating the Android Storage Access Framework (SAF) to request persistent access to a directory tree.

## Key Technologies

- **Storage Access Framework (SAF)**: `Intent.ACTION_OPEN_DOCUMENT_TREE` is the standard way to let users pick a directory.
- **Persistent Permissions**: Use `contentResolver.takePersistableUriPermission(uri, takeFlags)` to ensure the app can write to this folder even after a reboot.
- **DocumentFile**: The `androidx.documentfile:documentfile` library provides a convenient way to interact with SAF URIs (creating files, checking existence).
- **DataStore**: Store the URI string in `UserPreferencesRepository`.

## Implementation Path

### 1. Folder Selection (Settings Screen)

```kotlin
val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
) { uri ->
    uri?.let {
        context.contentResolver.takePersistableUriPermission(
            it,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        viewModel.saveSaveFolderUri(it.toString())
    }
}
```

### 2. Saving the File

```kotlin
val pickedDir = DocumentFile.fromTreeUri(context, treeUri)
val file = pickedDir?.createFile("image/png", fileName)
file?.uri?.let { destUri ->
    context.contentResolver.openOutputStream(destUri)?.use { output ->
        inputStream.copyTo(output)
    }
}
```

### 3. Settings UI

Add a "Settings" tab in the `NavigationBar`.

- Section: Storage
- Entry: "Save Folder" (shows current path/URI snippet)
- Action: "Change Folder" (triggers SAF launcher)

## Risks & Assumptions

- **SAF Performance**: Writing large sets of files via SAF is slower than direct file I/O, but acceptable for gallery saves.
- **Permission Revocation**: User can revoke permission through system settings; app should handle `SecurityException` and prompt for re-selection.
- **Android Version**: SAF is available since API 19, but `OpenDocumentTree` is robust on API 21+. Our min SDK is likely 24+.
