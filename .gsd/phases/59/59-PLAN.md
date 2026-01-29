---
phase: 59
plan: 1
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/com/example/comfyui_remote/data/UserPreferencesRepository.kt
  - app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
  - app/src/main/java/com/example/comfyui_remote/MainActivity.kt
autonomous: true
user_setup: []

must_haves:
  truths:
    - "User can select from up to 5 saved server addresses via dropdown"
    - "New servers are automatically saved when connecting"
    - "Most recent server appears at top of dropdown"
    - "Current single-server flow continues to work for new installs"
  artifacts:
    - "ServerProfile data class exists"
    - "Combo box UI replaces plain text field"
    - "DataStore persists JSON list of server profiles"
---

# Plan 59.1: Server IP Address Selection

<objective>
Add a combo box/dropdown to the ConnectionScreen that allows users to select from up to 5 previously used server IP addresses, improving UX for users who connect to multiple ComfyUI servers.

Purpose: Power users often have multiple ComfyUI instances (home desktop, laptop, cloud server). Manually typing IP addresses each time is tedious and error-prone.

Output: ConnectionScreen with dropdown showing saved servers, auto-save on connect, max 5 entries (FIFO).
</objective>

<context>
Load for context:
- .gsd/SPEC.md
- app/src/main/java/com/example/comfyui_remote/data/UserPreferencesRepository.kt
- app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
- app/src/main/java/com/example/comfyui_remote/MainActivity.kt (ConnectionScreen composable)
</context>

<tasks>

<task type="auto">
  <name>Create ServerProfile data class and update UserPreferencesRepository</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/data/UserPreferencesRepository.kt
  </files>
  <action>
    1. Create a `ServerProfile` data class with fields: `host: String`, `port: Int`, `isSecure: Boolean`, `lastUsed: Long` (timestamp)
    2. Add a new preference key `SERVER_PROFILES_KEY` as `stringPreferencesKey("server_profiles")` to store JSON array
    3. Add `val serverProfiles: Flow<List<ServerProfile>>` that reads JSON and deserializes to list (return empty list if null)
    4. Add `suspend fun saveServerProfile(profile: ServerProfile)`:
       - Read existing profiles
       - Remove duplicate (same host+port)
       - Add new profile at start (most recent first)
       - Trim to max 5 entries
       - Serialize and save JSON
    5. Add `suspend fun deleteServerProfile(profile: ServerProfile)` for future use
    6. Use kotlinx.serialization or Gson for JSON (Gson is already in project dependencies)

    AVOID: Removing the existing `saveConnectionDetails` method — keep it for backward compatibility during migration.
  </action>
  <verify>Build compiles: `./gradlew assembleDebug`</verify>
  <done>ServerProfile class exists, repository can read/write list of profiles</done>
</task>

<task type="auto">
  <name>Update MainViewModel with server profile state</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/MainViewModel.kt
  </files>
  <action>
    1. Add `val serverProfiles: StateFlow<List<ServerProfile>>` collected from repository
    2. Add `fun selectServerProfile(profile: ServerProfile)`:
       - Update host, port, isSecure states
       - Call existing connect logic
    3. Modify `saveConnection()` to:
       - Create ServerProfile from current host/port/isSecure with current timestamp
       - Call `userPreferencesRepository.saveServerProfile(profile)`
    4. Add `fun deleteServerProfile(profile: ServerProfile)` for swipe-to-delete (optional enhancement)

    AVOID: Breaking existing connect flow. The current text fields should still work if user types a new address.
  </action>
  <verify>Build compiles: `./gradlew assembleDebug`</verify>
  <done>ViewModel exposes serverProfiles and can select/save profiles</done>
</task>

<task type="auto">
  <name>Update ConnectionScreen UI with dropdown</name>
  <files>
    app/src/main/java/com/example/comfyui_remote/MainActivity.kt
  </files>
  <action>
    1. Add state: `val serverProfiles by viewModel.serverProfiles.collectAsState()`
    2. Replace the Host IP `OutlinedTextField` with an `ExposedDropdownMenuBox`:
       - Text field shows current host value (editable)
       - Dropdown shows saved profiles formatted as "host:port" with "(secure)" suffix if applicable
       - Selecting a profile auto-fills host, port, and isSecure toggle
       - Allow typing new values (not just selection)
    3. Keep Port field separate but auto-fill when profile selected
    4. When "Connect" succeeds, the profile is saved automatically (already in ViewModel logic)

    UI Design:
    - Use Material3 `ExposedDropdownMenuBox` with `ExposedDropdownMenu`
    - Show "No saved servers" if list empty
    - Format dropdown items as: "192.168.1.10:8188" or "192.168.1.10:443 (secure)"
    - Add trailing delete icon on each dropdown item for removal

    AVOID: Breaking keyboard done action. Ensure Enter still triggers connect.
  </action>
  <verify>Build compiles: `./gradlew assembleDebug`</verify>
  <done>ConnectionScreen shows dropdown with saved servers, selection auto-fills fields</done>
</task>

</tasks>

<verification>
After all tasks, verify:
- [ ] Build succeeds: `./gradlew assembleDebug`
- [ ] User can connect to a server and it appears in dropdown on next app launch
- [ ] Selecting a dropdown item fills host, port, and isSecure fields
- [ ] New typed addresses are saved when Connect succeeds
- [ ] Max 5 servers are kept (oldest removed when adding 6th)
- [ ] Existing saved connection migrates (user doesn't lose current server)
</verification>

<success_criteria>

- [ ] All tasks verified with successful build
- [ ] Manual testing confirms dropdown functionality
- [ ] No regression in existing connection flow
</success_criteria>
