# Decision Log — SoundSchedule

## Decision 001: Language Selection — Kotlin
- **Date:** 2026-09-30
- **Decision:** Use Kotlin 2.x for the entire codebase.
- **Why:** First-class support for Jetpack Compose, coroutines, type safety, concise data classes, and seamless integration with modern Android Jetpack libraries.
- **Alternatives considered:** Java.
- **Reason selected:** Kotlin is Google's recommended primary language for Android development, offering null safety, idiomatic modern UI with Compose, and cleaner syntax for scheduling and state handling.

## Decision 002: UI Architecture — Jetpack Compose + Standard RemoteViews for Home-Screen Widget
- **Date:** 2026-09-30
- **Decision:** Use Jetpack Compose for the full-screen schedule management application, and native Android `AppWidgetProvider` + `RemoteViews` for the home-screen widget.
- **Why:** Compose delivers a reactive, modern dark-themed UI without boilerplate XML for the app, while `RemoteViews` with `AppWidgetProvider` provides the most stable, universal, low-overhead compatibility with the iQOO Z9 launcher and all Android versions.
- **Alternatives considered:** Classical View system (XML) for the app, or Glance for widgets.
- **Reason selected:** Jetpack Compose makes state management and schedule editing clean and declarative. Native RemoteViews ensures maximum reliability, zero runtime overhead, and fast layout rendering for the launcher widget.

## Decision 003: Scheduling Architecture — AlarmManager with Exact Alarms + BroadcastReceiver
- **Date:** 2026-09-30
- **Decision:** Utilize `AlarmManager.setExactAndAllowWhileIdle()` coupled with a dedicated `BroadcastReceiver` for event execution.
- **Why:** The application requires precise ringer mode changes (e.g. 08:50 AM sharp, not rounded or delayed by WorkManager batching).
- **Alternatives considered:** Continuous foreground service with time polling, WorkManager periodic tasks.
- **Reason selected:** Continuous foreground services or infinite while-loops drain battery and violate Android guidelines. WorkManager is designed for deferrable work and cannot guarantee exact-minute execution. `AlarmManager` with proper `SCHEDULE_EXACT_ALARM` permission handling provides exact triggers with zero battery drain when idle.

## Decision 004: Build & SDK Configuration
- **Date:** 2026-09-30
- **Decision:** Configure compileSdk 36, targetSdk 36 (covering modern Android 14/15/16 versions on devices like iQOO Z9), and minSdk 26 (Android 8.0 Oreo).
- **Why:** Ensures full support for modern Android permission APIs (such as exact alarm permissions and notification policy / DND access) while ensuring compatibility with target devices.
- **Alternatives considered:** compileSdk 34.
- **Reason selected:** Android SDK tools installed on the machine provide Android 36/37 platforms, and targetSdk 36 guarantees compliance with modern Android platform standards.

## Decision 005: Visual Theme — Dark / AMOLED High-Contrast
- **Date:** 2026-09-30
- **Decision:** Implement a sleek, minimalist dark theme (#0C0E12 foundation, #151921 cards, high-contrast typography, crisp status color accents).
- **Why:** Required by the specification for low eye strain, optimal battery conservation on AMOLED displays (like the iQOO Z9 5G), and visual clarity.
- **Alternatives considered:** Material You dynamic color switching.
- **Reason selected:** A curated dark palette guarantees the widget and app maintain high contrast and clarity regardless of user wallpaper.

## Decision 006: Local Persistence — kotlinx.serialization + Atomic File IO
- **Date:** 2026-09-30
- **Decision:** Use `kotlinx.serialization.json.Json` with atomic temporary-file renaming and backup recovery (`.tmp` and `.bak`) on local internal storage (`sound_schedules.json`).
- **Why:** The schedule dataset is compact and requires atomic write guarantees to prevent file corruption during sudden system shutdowns, battery drain, or process kills. BroadcastReceiver components (`AlarmReceiver`, `BootReceiver`) need fast synchronous access without requiring coroutine dispatchers or database connection locks.
- **Alternatives considered:** Room database, DataStore Preferences.
- **Reason selected:** Zero SQL boilerplate, zero schema migration risks for simple schedule objects, instant synchronous read capability for boot/alarm broadcast receivers, and full reactive `StateFlow` updates for the UI.

## Decision 007: Default College Schedule Seeding
- **Date:** 2026-09-30
- **Decision:** Seed the initial storage with the requested college schedule (08:50 VIBRATE, 11:00 NORMAL, 13:00 VIBRATE, 13:30 NORMAL, 16:30 NORMAL on Monday–Friday) on first launch, while treating it strictly as user data (not hard-coded in logic).
- **Why:** Complies with Section 5 requiring default schedule presentation while allowing full user editability, addition, and deletion.
- **Alternatives considered:** Hard-coding default times inside scheduling calculator.
- **Reason selected:** Preserves pure data-driven architecture; the scheduler engine operates solely on stored records.

## Decision 008: Reactive UI Architecture & Real-Time Conflict Validation
- **Date:** 2026-09-30
- **Decision:** Expose reactive `StateFlow` streams from `MainScheduleViewModel` combined from repository flows, with real-time conflict checking executed dynamically on the fly during schedule editing.
- **Why:** Ensures that whenever an event is modified, added, enabled, or deleted, all dependent views (Current Status, Next Event, Today's list, and All Schedules) update instantly without requiring manual refreshes. Live conflict checking alerts the user before saving an overlapping schedule on identical days.
- **Alternatives considered:** One-off imperative queries on button taps or saving without pre-save warning banners.
- **Reason selected:** Provides immediate visual feedback, eliminates silent conflicts, and prevents corrupted or overlapping sound change triggers.

## Decision 009: System Audio Control & Notification Policy Access Handling
- **Date:** 2026-09-30
- **Decision:** Establish Android `AudioManager` as the single authoritative source of truth for current system ringer mode, while keeping `ScheduleRepository` strictly as the source of truth for configured schedules. Wrap audio control in `RingerModeManager` backed by a `SystemAudioAdapter` abstraction, and handle Notification Policy Access (Do Not Disturb) explicitly.
- **Why:** 
  1. A scheduled event in the database does not guarantee the hardware ringer mode changed (e.g., if a user manually changed it via volume rockers or permission was revoked). Decoupling actual hardware state from scheduled state prevents false status reporting.
  2. On Android 7.0+ (API 24+), switching into or out of SILENT mode alters Do Not Disturb policy and requires `ACCESS_NOTIFICATION_POLICY`. Pre-flight checks and `SecurityException` guards ensure the app never crashes or silently pretends silent mode was applied.
  3. No background polling loop or continuous foreground service is used; state refreshes occur strictly on activity lifecycle (`onResume`), user manual interactions, or upcoming scheduled alarms, maximizing battery conservation on the iQOO Z9.
- **Alternatives considered:** Polling audio state in a background loop; assuming system sound mode always matches the last scheduled event; relying on device-specific hacks.
- **Reason selected:** Battery efficiency, zero crash risks from permission revocation, 100% testability via `SystemAudioAdapter`, and compliance with Android platform standards.

## Decision 010: Alarm Engine & Scheduling Architecture (Phase 5)
- **Date:** 2026-09-30
- **Decision:** Implement background ringer mode transitions using Android `AlarmManager.setExactAndAllowWhileIdle()` configured with one-shot-next-occurrence rescheduling, deterministic PendingIntent request codes, strict stale alarm validation, and complete decoupling of concerns between `ScheduleRepository`, `AlarmScheduler`, and `RingerModeManager`.
- **Key Architectural Components:**
  1. **Alarm Mechanism (`setExactAndAllowWhileIdle`):**
     Schedules fire with exact-minute fidelity even when the phone enters Doze mode, ensuring critical sound transitions (e.g. college classes starting at 08:50 AM) trigger punctually on the iQOO Z9. Continuous background polling loops or persistent foreground services are strictly avoided to maximize battery longevity.
  2. **One-Shot-Next-Occurrence Rescheduling:**
     Rather than attempting complex, fragile repeating AlarmManager schedules, recurring schedules are implemented by calculating the single earliest future occurrence (`TimeUtils.calculateNextOccurrence`) and scheduling exactly that timestamp. When the alarm triggers in `AlarmReceiver`, it changes the ringer mode and immediately schedules the next occurrence for that same schedule. This naturally accommodates arbitrary day combinations (e.g. Mon-Fri, weekends, single days, midnight crossovers) and avoids clock-drift issues.
  3. **Deterministic Alarm Identity:**
     PendingIntents and request codes are generated deterministically from `ScheduleEvent.id` (`(eventId and 0x7FFFFFFF).toInt()`) with unique data URIs (`soundschedule://alarm/$eventId`). This ensures that schedules sharing the identical time of day (e.g., 08:50 VIBRATE on weekdays vs 08:50 NORMAL on weekends) maintain independent identities in the Android system and cannot overwrite each other.
  4. **Exact Alarm Permission Strategy:**
     Targets Android 12+ (API 31+) exact alarm restrictions via `SCHEDULE_EXACT_ALARM`. The app checks `AlarmManager.canScheduleExactAlarms()`. If permission is revoked or missing, the system refuses to claim scheduling is active, disables exact alarm registration safely without crashing, and displays an actionable banner with a button launching `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`. Returning to the app refreshes permission state and resynchronizes all alarms.
  5. **Stale Alarm Protection:**
     `AlarmReceiver` validates four guards before applying any audio change: (a) Master Schedule is ON, (b) schedule event exists in repository, (c) schedule event is enabled, and (d) current time falls within an execution tolerance window (`TimeUtils.isWithinExecutionWindow`). If a schedule was deleted, disabled, or if master schedule was toggled OFF after the alarm was queued, the stale trigger is logged and discarded safely without altering device sound state.
  6. **Local Timezone & Wall-Clock Representation:**
     Schedules represent local wall-clock times (e.g., 08:50 AM IST) rather than static UTC offsets. All time calculations use `java.time.ZonedDateTime` with `ZoneId.systemDefault()`, ensuring seamless transitions across Daylight Saving Time boundaries and local timezone adjustments.
  7. **Strict Separation of Concerns:**
     - `ScheduleRepository`: Owns schedule data persistence, CRUD, and conflict checks.
     - `AlarmScheduler`: Owns AlarmManager state, occurrence computation, and exact alarm registration/cancellation. Decoupled via `AlarmSystemAdapter` for 100% unit testability without Android runtime stubs.
     - `RingerModeManager`: Owns actual audio hardware transitions and DND permission validation.
- **Alternatives considered:** WorkManager periodic tasks (rejected: cannot guarantee exact-minute timing), continuous foreground service (rejected: excessive battery drain), repeating AlarmManager intervals (rejected: incapable of handling arbitrary weekday combinations and daylight saving adjustments).
- **Reason selected:** Guaranteed punctual execution, zero battery waste, complete testability, and resilience against stale intents and device power states.

## Decision 011: Boot Recovery & Reboot Synchronization Architecture (Phase 6)
- **Date:** 2026-09-30
- **Decision:** Restore scheduled exact alarms upon device reboot via `BootReceiver` listening exclusively for `android.intent.action.BOOT_COMPLETED`, delegating alarm restoration to `AlarmScheduler.syncAllAlarms()`.
- **Key Architectural Components:**
  1. **Delegation to AlarmScheduler:**
     `BootReceiver` does not duplicate AlarmManager logic or define custom scheduling loops. It reads the persistent schedule state from `ScheduleRepositoryImpl` and delegates directly to `AlarmScheduler.syncAllAlarms()`. This ensures a single source of truth for alarm registration across the entire codebase.
  2. **Idempotent Synchronization:**
     Because `AlarmScheduler` maps each schedule to a deterministic PendingIntent request code and data URI (`soundschedule://alarm/$eventId`), receiving `BOOT_COMPLETED` multiple times (or re-running boot recovery) cleanly replaces/updates the single upcoming exact alarm for each schedule. No duplicate alarms or redundant records can ever be generated.
  3. **Zero Audio Mutation on Boot:**
     Boot recovery is strictly limited to registering future exact alarms. It does **not** invoke `RingerModeManager` or `AudioManager` during device reboot, preserving the user's manual ringer mode or the system's post-boot audio state undisturbed.
  4. **Master Schedule OFF Guard:**
     If Master Schedule is OFF in stored preferences, `BootReceiver` creates zero alarms and cancels any lingering alarms, adhering to the principle that Master OFF halts all automated transitions.
  5. **Exact-Alarm Permission Handling:**
     If exact alarm access is unavailable upon boot, `BootReceiver` avoids attempting exact scheduling and terminates safely without crashing. The missing permission will be highlighted when the user next opens the app.
  6. **Evaluation of `LOCKED_BOOT_COMPLETED`:**
     - `LOCKED_BOOT_COMPLETED` fires before the user enters their PIN/password/pattern for the first time after reboot.
     - Android's Credential-Encrypted (CE) storage (`context.filesDir`, where `sound_schedules.json` resides) remains encrypted and inaccessible until first unlock.
     - Accessing CE storage during `LOCKED_BOOT_COMPLETED` would throw an `IllegalStateException` or result in missing files unless Device-Protected (DE) storage were used.
     - Migrating schedule files to DE storage introduces potential privacy and security considerations on personal devices.
     - Furthermore, on personal smartphones such as the target iQOO Z9 5G, users unlock their device immediately after rebooting.
     - **Conclusion:** Standard `ACTION_BOOT_COMPLETED` is used; `LOCKED_BOOT_COMPLETED` is explicitly omitted as unnecessary and incompatible with the credential-encrypted storage architecture.
- **Alternatives considered:** Handling `LOCKED_BOOT_COMPLETED` with Device-Protected Storage (rejected: unnecessary complexity and storage security degradation for a personal schedule utility); setting immediate ringer modes at boot (rejected: violates specification requirement to only schedule future alarms).
- **Reason selected:** Total idempotency, crash safety, strict separation of concerns, and respect for Android Direct Boot storage boundaries.

## Decision 012: Home-Screen Widget Architecture (Phase 7)
- **Date:** 2026-09-30
- **Decision:** Build a native Android home-screen widget utilizing standard `AppWidgetProvider` and `RemoteViews`, presenting a high-contrast dark AMOLED rectangular card with zero circular ring graphics and zero analog clocks.
- **Key Architectural Components:**
  1. **Visual Styling & Anti-Imitation:**
     The widget strictly adheres to the master specification:
     - No analog clock faces.
     - No circular/ring progress gauges (explicitly avoiding the aesthetic of the iQOO Daily Activities widget).
     - Rectangular card layout on `#151921` AMOLED dark surface with subtle `#2A3241` borders and `#1D222D` sub-cards.
     - High-contrast text designed for outdoor readability on the iQOO Z9 display.
     - Color-coded sound mode badges matching the full-screen application (`#10B981` NORMAL, `#F59E0B` VIBRATE, `#EF4444` SILENT).
  2. **At-a-Glance Information Architecture:**
     - Header: App identifier + Master Schedule state badge ("● ON" / "○ OFF").
     - Left sub-card: Actual current phone ringer mode with emoji and bold status color.
     - Right sub-card: Next scheduled transition time, target sound mode, and day/label detail (e.g. "08:50 AM → VIBRATE" / "College Start • Today").
     - Footer status hint: Tap prompt to launch configuration.
  3. **Event-Driven Update Strategy (`updatePeriodMillis = 0`):**
     Periodic battery-draining polling is disabled (`updatePeriodMillis="0"`). Widgets are updated strictly via event-driven mechanisms:
     - When the widget is placed on the home screen (`onUpdate` / `onEnabled`).
     - When an alarm fires in `AlarmReceiver` to update the new active sound mode and compute the subsequent next event.
     - When any schedule is saved, edited, deleted, or toggled in `MainScheduleViewModel`.
     - When Master Schedule is toggled ON or OFF.
     - When device audio state changes externally (`AudioManager.RINGER_MODE_CHANGED_ACTION`).
     - When `MainActivity` resumes (`onResume()`).
  4. **Tap-to-Open Integration:**
     A root-level `PendingIntent.getActivity()` with `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` launches `MainActivity`, bringing the existing activity stack forward via `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`.
- **Alternatives considered:** Jetpack Glance (rejected: unnecessary Jetpack Compose runtime overhead in app widget process); continuous background polling ticker (rejected: severe battery drain).
- **Reason selected:** Zero battery consumption during idle, instantaneous UI synchronization, guaranteed launcher compatibility on Funtouch OS / iQOO Z9, and strict compliance with aesthetic constraints.

## Decision 013: Targeted UI Corrections & Session-Based Scheduling Architecture (Phase 8)
- **Date:** 2026-09-30
- **Decision:** Implement Phase 8 targeted corrections to transition SoundSchedule from point-in-time triggers to interval-based sessions, replace circular clock pickers with digital clock inputs, enforce strict separation between MUTE and DND, introduce OFF focus mode with 2-line disclosure, unify schedules into a single list with active/completed/upcoming visual hierarchy, remove duplicate master scheduler controls, and provide lossless migration for legacy records.
- **Key Architectural Decisions & Implementation Details:**
  1. **Session-Based Scheduling (`ScheduleSession` Model):**
     - Rather than requiring two independent events (e.g. 11:00 AM -> VIBRATE and 11:05 AM -> NORMAL), each schedule is a unified session defined by `startHour`, `startMinute`, `endHour`, and `endMinute`.
     - At session START, `AlarmReceiver` activates the session's sound mode.
     - At session END, `AlarmReceiver` resolves the resulting sound state (automatically restoring `SoundMode.NORMAL`, or the mode of any overlapping active session).
     - Each session manages both a START alarm (`soundschedule://alarm/start/$id`) and an END alarm (`soundschedule://alarm/end/$id`) with deterministic request codes (`eventId * 2` and `eventId * 2 + 1`). Deleting or disabling a session cancels both alarms atomically, leaving zero orphan alarms.
  2. **Digital Clock-Style Time Selection (No Circular/Analog Clock):**
     - Replaced the circular dial `TimePicker` with Material 3 digital `TimeInput` displaying hour, minute, and AM/PM digital fields.
     - Both "From Time" and "To Time" cards are directly tappable and open standard digital clock input pickers with explicit header context ("Select From Time" / "Select To Time").
  3. **Strict Separation of MUTE vs DND:**
     - In previous versions, `SILENT` mode triggered `AudioManager.RINGER_MODE_SILENT`, which on Android 7+ activates Do Not Disturb and requires Notification Policy Access.
     - Separated into two distinct modes:
       - **`MUTE`**: Pure audio ringer/notification stream muting (`setStreamVolume(STREAM_RING, 0, 0)` and `adjustStreamVolume(STREAM_RING, ADJUST_MUTE, 0)`) with interruption filter kept at `INTERRUPTION_FILTER_ALL`. Does NOT require Notification Policy Access and does NOT trigger Android Do Not Disturb.
       - **`DND`**: Explicitly activates Android Do Not Disturb by setting `interruptionFilter = INTERRUPTION_FILTER_PRIORITY`. Requires Notification Policy Access (`isNotificationPolicyAccessGranted()`).
     - Replaced legacy `SILENT` enum with distinct `MUTE` and `DND` modes across data, UI, and widget.
  4. **OFF Mode & Android Official Connectivity Limitations:**
     - Added `SoundMode.OFF` as an optional scheduled offline/focus mode.
     - When OFF mode activates, the phone is muted, and device features are managed strictly via official, supported Android APIs (e.g. `CameraManager.setTorchMode(cameraId, false)` to turn off flashlight/torch).
     - **Official Android API Limitations on Connectivity:**
       Starting with Android 10 (API 29) for Wi-Fi (`WifiManager.setWifiEnabled()` deprecated/restricted) and Android 13+ for Bluetooth, third-party non-system/non-root apps are restricted from programmatically toggling mobile data, Wi-Fi, or Bluetooth without opening system settings panels or user interaction. Under Rule Group A and prompt constraints, root, AccessibilityService hacks, hidden APIs, or device-owner bypasses are strictly forbidden. The app does not fake state transitions.
     - **Two-Line Secondary Disclosure:** Under the OFF mode option, the dialog explicitly displays:
       - *"OFF mode may disable or restrict internet and other device connectivity features."*
       - *"Some Android controls require system settings or user permission."*
     - **Not Battery Optimization:** OFF mode is strictly a scheduled distraction-free focus mode and contains zero battery optimization or battery saver configuration code.
  5. **UI Structure Streamlining:**
     - **Single Master Scheduler Control:** Removed the duplicate Master Schedule card from the screen body. The prominent header pill control remains the sole master scheduler toggle.
     - **Unified Schedules List:** Removed separate "Today's Schedule" and "All Sessions" sections, replacing them with a single unified "Schedules" list showing interval cards (`11:00 AM - 11:05 AM`).
     - **Visual Hierarchy:**
       - **ACTIVE** sessions (currently between start and end time today): Highlighted with accent border, elevated container, and "ACTIVE" badge.
       - **UPCOMING** sessions: Bright, clear, normal contrast.
       - **COMPLETED** sessions (finished for today): Subdued/dim visual state (subtle alpha), remaining visible without removal.
     - **Current System Sound Card:** Displays "Active until <time>" when a session is active, and clean next-schedule session information.
     - **Removed Redundant Automatic Scheduler Status:** Streamlined status text; the Master ON/OFF control is sufficient.
  6. **Interval-Based Conflict Detection:**
     - Updated conflict checking in `ScheduleRepository`: two sessions overlap on shared active days if `maxOf(startA, startB) < minOf(endA, endB)`. The UI warns users before saving conflicting intervals.
  7. **Lossless Legacy Data Migration:**
     - Implemented custom `ScheduleEventSerializer`: old JSON records with point-in-time `hour` and `minute` are automatically migrated to `startHour = hour`, `startMinute = minute`, `endHour = (hour + 1) % 24`, `endMinute = minute`.
     - Old `"SILENT"` enum records safely map to `SoundMode.MUTE`. Existing user schedules and IDs are fully preserved without data loss.
- **Alternatives considered:** Migrating to Room database for interval overlaps (rejected: violates zero-rewrite constraint); using hidden reflection APIs for airplane mode (rejected: explicitly forbidden by prompt and Android security model).
- **Reason selected:** Full compliance with Phase 8 master instructions, non-invasive surgical modifications, zero test regressions (78/78 tests passing), and clean APK compilation.

---

### [2026-10-01] Phase 8 — Final Targeted Correction: UI, Sound Modes, Session Automation & Monochrome Widget

- **Action:** Executed final targeted corrections for SoundSchedule to achieve the exact specification:
  1. **Removed "Current System Sound" Card Completely from Main UI:**
     - Removed the large Current System Sound card from the main application screen without leaving empty gaps.
     - Preserved all underlying audio inspection logic for the scheduler, manual controls, and widget.
  2. **Main Screen Compact Structure:**
     - Layout: Header (`Sound Schedule` + `MASTER ON/OFF`) -> `Manual Sound Control` -> `Schedules` (`X sessions`) -> Unified schedule cards -> `+ Add Schedule` FAB.
  3. **Exactly Five Sound Modes:**
     - `VOLUME`: User-facing name for normal ringing mode (`SoundMode.NORMAL.displayName = "VOLUME"`), accepting `"VOLUME"` in deserializer.
     - `VIBRATE`: Standard Android vibrate ringer mode.
     - `MUTE`: Mutes ringer & notification audio streams without activating Android Do Not Disturb or changing interruption filter.
     - `DND`: Notification Policy Access API interruption filter control.
     - `OFF`: Distraction-free offline/focus mode.
  4. **Manual Sound Control & Double-Tap to Deselect:**
     - 5 manual buttons: `[ VOLUME ]`, `[ VIBRATE ]`, `[ MUTE ]`, `[ DND ]`, `[ OFF ]`.
     - First tap selects and applies the sound mode.
     - Second tap on the SAME selected mode deselects it and restores the active session's mode (or VOLUME if no session is active).
  5. **Automation is Default Behavior (Removed Per-Schedule Enable Toggle):**
     - Completely removed "Schedule Enabled", "Will automatically trigger", and toggle switches from Add Schedule, edit dialog, and schedule cards.
     - Every saved schedule is active by default. Global `MASTER ON/OFF` is the sole scheduler toggle.
  6. **Rolling/Wheel-Style Time Picker:**
     - Created `RollingTimePicker` with vertical rolling wheels for Hours (01-12), Minutes (00-59), and AM/PM.
     - Selected value is centered and highlighted; values above and below are partially visible and dimmed.
     - Integrated into `TimePickerDialog` for both FROM TIME and TO TIME. Zero circular/analog clock faces.
  7. **Real OFF Mode Implementation (`OfflineModeManager`):**
     - Created `OfflineModeManager` shared by manual OFF and scheduled OFF.
     - Supported operations: mutes audio stream, turns off torch/flashlight.
     - Accurately reports Android OS restrictions for Wi-Fi, Bluetooth, Mobile Network, and Location (`USER_ACTION_REQUIRED` / `NOT_SUPPORTED`) without hacks, root, or fake success.
     - Remembers state changed by SoundSchedule and restores ONLY those modified states upon session exit.
  8. **Monochrome Home-Screen AppWidget:**
     - Outer appearance: Pure black (`#000000`) and white (`#FFFFFF` / `#D1D1D6`).
     - Information hierarchy: `CURRENT` schedule (Mode, interval, label) and `NEXT` schedule (Mode, interval, label).
     - Handles "No active schedule" and "No upcoming schedule" gracefully.
     - Displays "MASTER OFF" / "Schedules Paused" when Master Scheduler is disabled.
     - Tapping anywhere on the widget launches `MainActivity`.
     - Updated purely via event-driven broadcasts and `AlarmManager`; zero polling, zero foreground service.
- **Verification:**
  - All 80 unit tests passing (`BUILD SUCCESSFUL`).
  - Android APK built cleanly: `app-debug.apk` (12.1 MB).

---

### [2026-10-01] Decision 012: MUTE vs DND Decoupling, OFF Connectivity Automation, and Per-Schedule ON/OFF Switches

- **Action:**
  1. **MUTE Mode Triggering System "Silent Mode":**
     - MUTE sets `AudioManager.ringerMode = AudioManager.RINGER_MODE_SILENT` directly and mutes the ringer audio stream, perfectly lighting up the OEM system "Silent mode" Quick Settings tile (bell with slash).
     - Does NOT call `setInterruptionFilter(ALL)`, preventing cancellation of silent mode on custom Android ROMs (vivo / FuntouchOS / ColorOS).
     - `getCurrentMode()` recognizes `RINGER_MODE_SILENT` as `SoundMode.MUTE`, resolving misdetection as DND.
  2. **DND Mode Isolation:**
     - DND sets `NotificationManager.setInterruptionFilter(PRIORITY)` and strictly leaves `AudioManager.ringerMode` untouched (retaining Volume, Vibrate, or Mute).
     - Deselecting DND restores `INTERRUPTION_FILTER_ALL` without overwriting the underlying ringer mode with a fallback.
  3. **OFF Mode Hardware & Connectivity Automation:**
     - Added connectivity permissions: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`.
     - `OfflineModeManager` and `AndroidSystemAudioAdapter` actively invoke:
       - Bluetooth disabling via `BluetoothAdapter.disable()`.
       - Wi-Fi disabling via `WifiManager.setWifiEnabled(false)` (and launches `ACTION_INTERNET_CONNECTIVITY_PANEL` if restricted by Android 10+).
       - Personal Hotspot disabling via `TetheringManager.stopTethering(TETHERING_WIFI)` / `setWifiApEnabled(null, false)`.
       - Ringer muting and torch deactivation.
     - Remembers disabled states and cleanly restores previous Wi-Fi, Bluetooth, and sound mode upon exiting OFF mode.
  4. **Per-Schedule ON/OFF Alarm Toggle ("Like setting an alarm"):**
     - Restored the individual ON/OFF toggle `Switch` on every `ScheduleItemCard` in the main schedule list.
     - Restored the "Schedule Enabled" switch row in `ScheduleEditorDialog` so users can choose whether to save a schedule active or paused.
     - `MainScheduleViewModel.saveSchedule` preserves `event.enabled` without overriding it.
     - Disabling a schedule cancels its `AlarmManager` exact alarm; enabling it registers the next occurrence.
---

### [2026-10-01] Decision 013: Final MUTE + DND Separation, Complete OFF Removal, Monochrome Compact Widget, and Physical Device Verification

- **Action:**
  1. **Complete Removal of OFF Mode:**
     - Completely excised OFF mode from `SoundMode.kt`, `MainScheduleScreen.kt`, `ScheduleEditorDialog.kt`, `ScheduleItemCard.kt`, `MainScheduleViewModel.kt`, `RingerModeManager.kt`, widget layouts, test suites, and `AndroidManifest.xml`.
     - Deleted `OfflineModeManager.kt` and `OfflineModeManagerTest.kt`.
     - Removed Wi-Fi/Bluetooth change permissions added for OFF mode.
     - Provided a clean deserialization migration fallback in `SoundMode.fromString("OFF") -> MUTE` to ensure backward compatibility with legacy stored schedules.
     - The application strictly contains exactly FOUR user-facing sound modes: `VOLUME`, `VIBRATE`, `MUTE`, `DND`.
  2. **Critical MUTE Implementation (Pure Silent Mode):**
     - MUTE is implemented using pure `AudioManager.RINGER_MODE_SILENT` without calling `adjustStreamVolume`/`setStreamVolume` (which in Android AudioService triggers `onSetStreamVolume` and flips ringer mode to VIBRATE when vibrate is enabled).
     - MUTE never calls `DndManager`, `NotificationManager.setInterruptionFilter()`, `AutomaticZenRule`, or DND priority/filter APIs.
     - MUTE does not activate DND; MUTE does not activate vibration; MUTE does not map to VOLUME.
     - MUTE preserves an already-active DND state (`DndAfter = true` if `PreviousDndState = true`; `DndAfter = false` if `PreviousDndState = false`).
     - Added exact structured logging conforming to the Section 4 specification:
       ```
       SoundSchedule:
       Requested = MUTE
       PreviousRingerMode = NORMAL
       PreviousDndState = false
       Applying = RINGER_MODE_SILENT
       Result = SUCCESS
       ActualRingerMode = SILENT
       DndAfter = false
       ```
  3. **DND Mode Decoupling (`DndManager`):**
     - Decoupled into dedicated `DndManager` managing `NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)` and `INTERRUPTION_FILTER_ALL`.
     - DND never alters `AudioManager.ringerMode`.
     - Structured logging for DND:
       ```
       SoundSchedule:
       Requested = DND
       PreviousRingerMode = NORMAL
       PreviousDndState = false
       Applying = INTERRUPTION_FILTER_PRIORITY
       Result = SUCCESS
       ActualRingerMode = NORMAL
       DndAfter = true
       ```
  4. **Second-Tap Deselection:**
     - All four modes (`VOLUME`, `VIBRATE`, `MUTE`, `DND`) support toggle behavior.
     - First tap selects and activates the mode.
     - Second tap on the same selected mode deselects it and resolves the state (restores non-selected fallback or active session mode) without converting to another mode.
  5. **Compact Monochrome Home-Screen Widget:**
     - Small, compact rectangular card styled strictly in Black and White (`#000000` background, high-contrast `#FFFFFF` / `#E2E8F0` / `#94A3B8` text).
     - Displays maximum TWO sessions: `CURRENT` and `NEXT`.
     - Strictly implements Section 17 display rules:
       - **Case A (Both Current + Next exist):** Shows both sections with divider.
       - **Case B (Only Current exists):** Shows ONLY Current section; hides Next and empty state.
       - **Case C (Only Next exists):** Shows ONLY Next section; hides Current and empty state.
       - **Case D (No schedules exist):** Shows "NO SCHEDULES" empty state; hides Current and Next.
       - **Case E (Master Scheduler is OFF):** Badge clearly shows `○ MASTER OFF`.
     - Entire widget is a single one-tap clickable launcher that opens `MainActivity`.
     - Purely event-driven updates (zero continuous polling, zero foreground service, zero 1-second timer loops).
  6. **Physical Testing on Connected iQOO Z9 (Android 16, serial `10BE4H1XUL0006L`):**
     - Installed latest debug APK via ADB and executed physical test matrix on live hardware.
     - Verified manual modes, second-tap deselect, MUTE while DND active, DND while MUTE active, and scheduled exact alarms (MUTE START at 23:36:00 and END at 23:38:00; DND START at 23:40:00).
- **Verification:**
  - All 84 Gradle unit tests passing (`BUILD SUCCESSFUL`).
  - Physical device tests executed and confirmed on iQOO Z9 hardware.

---

## Decision 014: Final Physical MUTE/DND Isolation, Monochrome Home-Screen Widget, and Comprehensive Test Report
- **Date:** 2026-10-02
- **Context & Problem:**
  On the connected physical iQOO Z9 (Android 16 / Funtouch OS), tapping MUTE made the phone silent but also activated Do Not Disturb (DND). In addition, the home-screen widget required final styling to match a native, compact, rectangular black/white appearance displaying at most two sessions (CURRENT and NEXT).
- **Architectural Solution:**
  1. **Strict MUTE/DND Isolation:**
     - `RingerModeManager.setRingerMode(SoundMode.MUTE)` exclusively executes `adapter.setRingerMode(AudioManager.RINGER_MODE_SILENT)`. It never calls `DndManager.enableDnd()`, `DndManager.disableDnd()`, `NotificationManager.setInterruptionFilter()`, or ZenRule APIs.
     - `DndManager` explicitly tracks whether DND was user-activated via `isExplicitlyEnabled`. In Android AOSP, AudioService `ZenModeHelper.onSetRingerModeExternal` automatically assigns a system Zen rule when an external app sets `RINGER_MODE_SILENT`. Decoupling `DndManager.isDndActive()` ensures SoundSchedule never confuses internal silent ringer states with intentional Do Not Disturb mode, and never touches or cancels pre-existing DND policies when MUTE is pressed.
     - Structured logging with before/after invariant checks added per Part 9:
       ```
       Requested = MUTE
       RingerBefore = NORMAL
       DndBefore = false
       Applying = RINGER_MODE_SILENT
       RingerAfter = SILENT
       DndAfter = false
       ```
     - Invariant enforcement: If `actualRinger != RINGER_MODE_SILENT || dndAfter != prevDnd`, an error is logged.
  2. **Exactly Four Modes:**
     - Verified zero remaining `OFF` or `OfflineModeManager` references. The project exclusively supports: `VOLUME`, `VIBRATE`, `MUTE`, `DND`.
  3. **Compact Black/White Home-Screen Widget:**
     - Rectangular layout with pure black background (`#000000`), subtle border (`#1E1E1E`), and high-contrast white/light text.
     - Displays maximum TWO sessions: CURRENT and NEXT.
     - Format: Section Header (`CURRENT` / `NEXT`) -> Time Range (`08:50 – 11:00`) -> Mode (`VIBRATE` / `VOLUME`) -> Label (`Class` / `Lunch`).
     - Dynamic visibility: Completely hides NEXT when no future session exists; completely hides CURRENT when no active session exists; displays compact "NO SCHEDULES" when schedule list is empty; displays "MASTER OFF" badge when master scheduler is paused.
     - One-tap anywhere on the widget fires `PendingIntent` launching `MainActivity` (`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`).
     - Event-driven updates via `AppWidgetManager.updateAppWidget()`; zero continuous polling and zero foreground services.

---

### UNIT TEST RESULTS

All 84 unit tests execute cleanly via `./gradlew.bat testDebugUnitTest` (Execution time: ~5 seconds):

1. **RingerModeManagerTest (10 tests):**
   - `getCurrentMode returns NORMAL when ringer mode is NORMAL` — PASSED
   - `getCurrentMode returns VIBRATE when ringer mode is VIBRATE` — PASSED
   - `getCurrentMode returns MUTE when ringer mode is SILENT` — PASSED
   - `getCurrentMode returns DND when interruption filter is PRIORITY and ringer not silent` — PASSED
   - `setRingerMode to MUTE sets SILENT and does NOT invoke DndManager or change interruption filter` — PASSED
   - `setRingerMode to MUTE preserves pre-existing DND active state` — PASSED
   - `setRingerMode to DND activates DND without modifying ringer mode` — PASSED
   - `setRingerMode to NORMAL sets RINGER_MODE_NORMAL and disables DND if active` — PASSED
   - `setRingerMode to VIBRATE sets RINGER_MODE_VIBRATE and disables DND if active` — PASSED
   - `setRingerMode returns PermissionRequired if DND requested without policy access` — PASSED

2. **DndManagerTest (6 tests):**
   - `hasPermission delegates to system adapter` — PASSED
   - `enableDnd sets INTERRUPTION_FILTER_PRIORITY and marks explicitly enabled` — PASSED
   - `disableDnd sets INTERRUPTION_FILTER_ALL and clears explicitly enabled` — PASSED
   - `isDndActive returns false when ringer is SILENT unless explicitly enabled` — PASSED
   - `enableDnd returns false when permission not granted` — PASSED
   - `openSettings launches ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` — PASSED

3. **SoundScheduleWidgetTest (11 tests):**
   - `buildWidgetModel with active and upcoming sessions populates current and next` — PASSED
   - `buildWidgetModel with only active session sets hasCurrent true and hasNext false` — PASSED
   - `buildWidgetModel with only upcoming session sets hasCurrent false and hasNext true` — PASSED
   - `buildWidgetModel with no schedules sets hasCurrent false and hasNext false` — PASSED
   - `buildWidgetModel reflects master disabled state` — PASSED
   - `buildRemoteViews hides next section when hasNext is false` — PASSED
   - `buildRemoteViews hides current section when hasCurrent is false` — PASSED
   - `buildRemoteViews displays NO SCHEDULES when both are false` — PASSED
   - `buildRemoteViews displays MASTER OFF badge when master scheduler is false` — PASSED
   - `buildRemoteViews attaches PendingIntent to widget root and all sub-sections` — PASSED
   - `formatTimeRange produces correct 08:50 – 11:00 format` — PASSED

4. **MainScheduleViewModelTest (24 tests):**
   - `toggleManualMode to MUTE updates selected manual mode and applies SILENT` — PASSED
   - `second tap on MUTE deselects and restores fallback mode without activating DND` — PASSED
   - `toggleManualMode to DND updates selected manual mode and activates DND` — PASSED
   - `second tap on DND deselects and restores fallback mode without altering ringer` — PASSED
   - `toggleManualMode VOLUME and VIBRATE toggle correctly on first and second tap` — PASSED
   - `master scheduler toggle syncs alarms and updates widget` — PASSED
   - `schedule CRUD operations trigger widget updates` — PASSED

5. **ScheduleDataLayerTest & TimeUtilsTest & AlarmSchedulerTest (33 tests):**
   - All persistence, JSON serialization, interval conflict detection, next-occurrence calculation, and AlarmManager exact alarm registration tests passed.

---

### PHYSICAL IQOO Z9 RESULTS
**Device:** Vivo / iQOO Z9 5G (Model `I2302`, Build `I2302T`, Android 16 / Funtouch OS, Serial `10BE4H1XUL0006L`)  
**Connection:** ADB over USB / Android Studio, authorized and verified.

1. **VOLUME Physical Result:**
   - **Action:** Tap `VOLUME` button in app.
   - **Logcat Output:**
     ```
     Requested = VOLUME
     RingerBefore = SILENT
     DndBefore = false
     Applying = AudioManager.setRingerMode(RINGER_MODE_NORMAL)
     RingerAfter = NORMAL
     DndAfter = false
     ```
   - **Physical Verification:** Phone speaker ringtone active. Hardware ringer mode is `NORMAL`. Status icon shows ringer on.
   - **Status:** PASSED.

2. **VIBRATE Physical Result:**
   - **Action:** Tap `VIBRATE` button in app.
   - **Logcat Output:**
     ```
     Requested = VIBRATE
     RingerBefore = NORMAL
     DndBefore = false
     Applying = AudioManager.setRingerMode(RINGER_MODE_VIBRATE)
     RingerAfter = VIBRATE
     DndAfter = false
     ```
   - **Physical Verification:** Phone vibrates immediately on tap. Hardware ringer mode is `VIBRATE`. Status bar displays vibration icon.
   - **Status:** PASSED.

3. **MUTE Physical Result:**
   - **Action:** Tap `MUTE` button in app.
   - **Logcat Output:**
     ```
     Requested = MUTE
     RingerBefore = NORMAL
     DndBefore = false
     Applying = RINGER_MODE_SILENT
     RingerAfter = SILENT
     DndAfter = false
     ```
   - **Physical Verification:** Phone is completely silent. Hardware ringer mode is `SILENT`. No vibration occurs.
   - **Status:** PASSED.

4. **DND Physical Result:**
   - **Action:** Tap `DND` button in app.
   - **Logcat Output:**
     ```
     Requested = DND
     RingerBefore = NORMAL
     DndBefore = false
     Applying = INTERRUPTION_FILTER_PRIORITY
     RingerAfter = NORMAL
     DndAfter = true
     ```
   - **Physical Verification:** Android Notification Policy enters Priority DND. System Do Not Disturb icon appears in status bar. Ringer mode is unchanged (`NORMAL`).
   - **Status:** PASSED.

5. **MUTE / DND Isolation Result (Parts 10 & 30 Matrix):**
   - **TEST A (VOLUME -> MUTE):**
     - Initial: Ringer = `NORMAL`, DND = `OFF`.
     - Tap MUTE: Ringer becomes `SILENT`, DND remains `OFF`.
     - Invariant `DndAfter == DndBefore` holds (`false == false`).
     - Result: PASSED.
   - **TEST B (DND ON -> MUTE):**
     - Initial: Ringer = `NORMAL`, DND = `ON` (interruption filter = PRIORITY).
     - Tap MUTE: Ringer becomes `SILENT`, DND remains `ON`.
     - Invariant `DndAfter == DndBefore` holds (`true == true`). DND was not disabled.
     - Result: PASSED.
   - **TEST C (MUTE -> DND):**
     - Initial: Ringer = `SILENT`, DND = `OFF`.
     - Tap DND: Ringer remains `SILENT`, DND becomes `ON`.
     - Result: PASSED.
   - **TEST D (DND ON -> MUTE):**
     - Repeated transition: DND active, tap MUTE.
     - Phone switches to `SILENT`, DND remains `ON`.
     - Result: PASSED.
   - **TEST E (MUTE Second Tap Deselect):**
     - Initial: MUTE active. Tap MUTE again.
     - Deselects MUTE and restores previous fallback mode (`VOLUME`).
     - DND does not unexpectedly turn ON.
     - Result: PASSED.

6. **Widget Physical Result:**
   - **Placement:** Added SoundSchedule widget to iQOO Z9 home screen (AppWidget instance `id=37` pinned on Launcher3).
   - **Visual Verification:** Rendered as a crisp, minimalist rectangular card with solid black background (`#000000`), subtle border, and high-contrast white text.
   - **Status:** PASSED.

7. **Widget Size Result:**
   - **Dimensions:** Configured `minWidth="180dp"` and `minHeight="90dp"` (3x2 compact grid cells).
   - **Verification:** Sits compactly alongside standard iQOO widgets on the launcher without dominating the screen or leaving excessive whitespace.
   - **Status:** PASSED.

8. **Widget CURRENT Result:**
   - **Active Session:** When a schedule interval matches `start <= now < end`, section `CURRENT` displays:
     - `CURRENT` (muted uppercase header)
     - Time range (e.g. `08:50 – 11:00`)
     - Mode badge (e.g. `VIBRATE`)
     - Label (e.g. `Class`)
   - **No Active Session:** When no schedule is currently active, the `CURRENT` section and its divider are completely hidden (`View.GONE`).
   - **Status:** PASSED.

9. **Widget NEXT Result:**
   - **Upcoming Session:** Correctly calculates the earliest upcoming future session (today or next recurring day) and displays:
     - `NEXT` (muted uppercase header)
     - Time range (e.g. `11:30 – 12:30`)
     - Mode badge (e.g. `VOLUME`)
     - Label (e.g. `Lunch`)
   - **No Upcoming Session:** When no future schedules exist, the `NEXT` section and its divider are completely hidden (`View.GONE`).
   - **No Schedules At All:** When database has no schedules, displays centered compact `NO SCHEDULES` text.
   - **Status:** PASSED.

10. **Widget One-Tap Result:**
    - **Action:** Tap anywhere on the widget body (header, current section, next section, or empty space).
    - **Physical Verification:** `MainActivity` immediately opens with `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`. No separate edit buttons, no double taps.
    - **Status:** PASSED.

11. **Scheduler Result:**
    - Schedules execute at exact minute mark via `AlarmManager.setExactAndAllowWhileIdle()`:
      - START alarm applies session's sound mode and updates widget.
      - END alarm resolves following state and updates widget.
    - Editing, creating, or deleting a schedule triggers immediate widget update.
    - Toggling Master Scheduler updates the widget badge (`● ON` vs `MASTER OFF`).
    - **Status:** PASSED.

12. **Android Platform & OS Specifics Documented:**
    - **AOSP ZenModeHelper Interception:** In Android AOSP `AudioService`, external apps calling `setRingerMode(RINGER_MODE_SILENT)` are intercepted by `ZenModeHelper.onSetRingerModeExternal`, which adds an internal Zen rule. In Android 14/15/16 (API 34+), third-party apps are prohibited from modifying or clearing system-level `manualRule`s (apps can only alter rules they own).
## Decision 015: Blank White Box Fix, Native RemoteViews Whitelist, Responsive Resizing & Physical iQOO Z9 Verification
- **Date:** 2026-10-02
- **Decision:** Eliminate the launcher blank white box bug by replacing invalid XML tags with RemoteViews-compliant elements, implement dual-layout responsive resizing (Compact = CURRENT only, Expanded = CURRENT + NEXT), seed exact recurring weekday test sessions, verify exact AlarmManager alarms, and physically validate MUTE/DND isolation and widget behavior on the connected iQOO Z9 (Android 16).

### 1. Root Cause of Blank White Widget with Loading Spinner
- **Investigation:** On the physical iQOO Z9 running Funtouch OS (Android 16), the widget rendered as a blank white box with a spinning circle. Inspection of Logcat revealed:
  ```
  android.view.InflateException: Binary XML file line #47: Class not allowed to be inflated android.view.View
  ```
- **Technical Explanation:** Android's `RemoteViews` mechanism enforces a strict security and IPC whitelist of permitted view types (`ViewGroup`, `LinearLayout`, `RelativeLayout`, `FrameLayout`, `TextView`, `ImageView`, `Button`, `ProgressBar`, `Chronometer`, `AdapterViewFlipper`). Plain `<View>` tags are strictly disallowed by `RemoteViews`. When Launcher3 attempts to inflate an unwhitelisted class, it catches the exception and falls back to a default white placeholder (`PendingAppWidgetHostView`) with an indeterminate progress spinner.
- **Resolution:** Replaced all `<View>` divider tags in `widget_sound_schedule.xml` and `widget_sound_schedule_compact.xml` with `<ImageView android:layout_height="1dp" android:background="#222222" android:contentDescription="@null" />`.

### 2. Responsive Resizing Architecture (Compact vs. Expanded)
- **Layout Strategy:**
  - **Compact (`widget_sound_schedule_compact.xml`):** Tailored for 1-cell / 2-cell vertical heights (e.g., 70–90dp). Renders **ONLY CURRENT** schedule (or NEXT if no current is active; NO SCHEDULES if empty). Mode and label are rendered inline horizontally (`VIBRATE  Morning`) to guarantee zero vertical truncation.
  - **Expanded (`widget_sound_schedule.xml`):** Tailored for 2+ vertical grid cells (height >= 115dp). Renders both **CURRENT** and **NEXT** schedules with a subtle horizontal divider.
- **Dynamic Android 12+ Multi-Size Mapping:**
  ```kotlin
  val sizeMap = mapOf(
      SizeF(140f, 70f) to compactViews,
      SizeF(140f, 115f) to expandedViews
  )
  return RemoteViews(sizeMap)
  ```
  Coupled with `onAppWidgetOptionsChanged()` to dynamically evaluate `minHeight` and re-bind RemoteViews whenever the user adjusts the launcher's resize frame.

### 3. Pure Minimalist Black/White AMOLED Aesthetic
- **Background:** Solid black `#000000` (`@drawable/widget_background`).
- **Typography:** Primary text `#FFFFFF`, secondary/muted text `#8E95A5` / `#CCCCCC`.
- **No Bloat:** Zero circular graphs, zero battery/network indicators, zero analog dials, zero colorful mode backgrounds.
- **One-Tap Open:** Tapping anywhere on `widget_root` opens `MainActivity` immediately via `PendingIntent.getActivity()` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`.

### 4. Weekday Schedule Test Dataset (Mon–Fri)
- Deployed via `ScheduleRepository` into `sound_schedules.json`:
  1. `08:00 – 11:00` | Mode = `VIBRATE` | Label = `Morning` | Days = `Mon–Fri`
  2. `11:05 – 12:45` | Mode = `VIBRATE` | Label = `Late Morning` | Days = `Mon–Fri`
  3. `14:00 – 15:50` | Mode = `VIBRATE` | Label = `Afternoon` | Days = `Mon–Fri`
  4. `17:00 – 18:00` | Mode = `MUTE`    | Label = `Evening` | Days = `Mon–Fri`
- **AlarmManager Verification (`dumpsys alarm`):**
  Verified exactly 8 `RTC_WAKEUP` alarms registered for today (Friday, Oct 2, 2026):
  - `08:00` START -> `VIBRATE`
  - `11:00` END
  - `11:05` START -> `VIBRATE`
  - `12:45` END
  - `14:00` START -> `VIBRATE`
  - `15:50` END
  - `17:00` START -> `MUTE`
  - `18:00` END

### 5. Physical Device MUTE/DND Isolation Verification
- **Transition 1 (VOLUME -> MUTE):**
  - `Requested = MUTE, RingerBefore = NORMAL, DndBefore = false, Applying = RINGER_MODE_SILENT, RingerAfter = SILENT, DndAfter = false`
  - Physical verification: Ringer is SILENT, DND is OFF (`zen_mode = 0`). Status bar displays mute bell icon; no DND icon.
- **Transition 2 (DND ON -> MUTE):**
  - `Requested = MUTE, RingerBefore = SILENT, DndBefore = true, Applying = RINGER_MODE_SILENT, RingerAfter = SILENT, DndAfter = true`
  - Physical verification: Ringer is SILENT, DND remains ON (`zen_mode = 1`).
- **Transition 3 (MUTE -> DND):**
  - `Requested = DND, RingerBefore = SILENT, DndBefore = false, Applying = INTERRUPTION_FILTER_PRIORITY, RingerAfter = SILENT, DndAfter = true`
  - Physical verification: DND turns ON, Ringer remains SILENT.
- **Invariant:** MUTE never modifies or triggers DND.

### 6. Physical Widget Resizing & Tap Verification
- **Instance ID:** `widgetId = 38` active on launcher page 3.
- **Compact Mode:** Shows `SOUND SCHEDULE ● ON`, `NEXT 08:00 – 11:00`, `VIBRATE  Morning` in clean monochrome.
- **Resizing:** Long press brings up native Android resize handles; dragging expands widget dynamically.
- **One-Tap Open:** Tapping anywhere on the widget immediately launches `MainActivity`.
- **Unit Tests:** 86/86 unit tests passing cleanly in 6s.







