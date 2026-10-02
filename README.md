# SoundSchedule — Custom Android Sound/Ringer Schedule & Widget

Native Android application built specifically for the **iQOO Z9 5G** (and modern Android devices) to automatically change the device's ringer mode (NORMAL, VIBRATE, SILENT) at user-configured scheduled times, paired with a minimalist dark home-screen widget.

---

## 1. Project Purpose
Users frequently need to switch phone ringer modes during recurring daily events (e.g. college classes, study sessions, office hours, sleep) and restore sound afterwards. Relying on manual switching leads to forgotten silences or embarrassing loud interruptions. SoundSchedule automates this process using native Android alarms with zero continuous battery drain and presents upcoming transitions directly on the home screen via an information card widget.

---

## 2. Key Features
- **Precise Scheduled Transitions**: Automatic switching between **NORMAL**, **VIBRATE**, and **SILENT** ringer modes at exact times.
- **Day-of-Week Granularity**: Individual day selection (Monday–Friday, custom days, weekend exclusions).
- **Home-Screen Information Widget**: A clean dark rectangular information card showing:
  - Current phone sound mode
  - Next upcoming Unmute time
  - Next upcoming Mute time
  - Master schedule status
  - Tapping opens the main schedule manager.
  - **No analog clocks, no circular progress rings, no imitation graphics**.
- **Master Toggle**: Global ON/OFF switch to pause all scheduled triggers without deleting user data.
- **Conflict Handling**: Detects and highlights overlapping scheduled events at identical times.
- **Reboot Resilience**: Automatically recalculates and registers upcoming alarms after device restarts (`BOOT_COMPLETED`).
- **Offline & Private**: Fully local operation with zero network dependencies and zero location/GPS permissions.

---

## 3. Project Architecture

The codebase follows clean separation of concerns:

```
com.soundschedule/
├── MainActivity.kt               # Single entry-point Activity hosting Compose
├── ui/
│   ├── MainScheduleScreen.kt     # Full-screen schedule manager UI
│   ├── ScheduleEditorScreen.kt   # Add/Edit schedule dialog & form (Phase 3)
│   └── components/               # Status cards, time pickers, day chips
├── theme/
│   ├── Color.kt                  # AMOLED dark palette & state accent tokens
│   ├── Theme.kt                  # Material 3 dark color scheme definition
│   └── Type.kt                   # Typography definitions
├── data/                         # Persistence layer (Phase 2)
│   ├── ScheduleEvent.kt          # Event model (time, mode, days, state)
│   ├── ScheduleRepository.kt     # Repository interface
│   └── ScheduleStorage.kt        # Local persistent storage engine
├── audio/                        # System sound controller (Phase 4)
│   └── RingerModeManager.kt      # AudioManager & DND policy access wrapper
├── scheduler/                    # Scheduling engine (Phase 5 & 6)
│   ├── ScheduleManager.kt        # Next event calculator & orchestrator
│   ├── AlarmScheduler.kt         # AlarmManager exact alarm registration
│   ├── AlarmReceiver.kt          # BroadcastReceiver for fired alarms
│   └── BootReceiver.kt           # BOOT_COMPLETED receiver
├── widget/                       # Home-screen widget (Phase 7)
│   ├── SoundScheduleWidget.kt    # AppWidgetProvider implementation
│   └── WidgetUpdater.kt          # RemoteViews builder & broadcast notifier
└── utils/
    ├── TimeUtils.kt              # Day-of-week & local timezone calculations
    └── PermissionUtils.kt        # Exact alarm & DND permission checks
```

---

## 4. Setup & Build Instructions

### Prerequisites
- **Android Studio**: Android Studio Ladybug / Meerkat (or newer).
- **JDK**: Java 17+ (or bundled Android Studio JBR: Java 21 / 25).
- **Android SDK**: Android SDK API 36 / 37 installed.

### Command-Line Build (Windows PowerShell)
To build the debug APK:
```powershell
.\gradlew.bat assembleDebug
```
The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Running Unit Tests
```powershell
.\gradlew.bat testDebugUnitTest
```

---

## 5. Permission & System Requirements
- `android.permission.SCHEDULE_EXACT_ALARM`: Required for exact-minute ringer mode transitions.
- `android.permission.RECEIVE_BOOT_COMPLETED`: Required to reschedule alarms following a device reboot.
- `android.permission.ACCESS_NOTIFICATION_POLICY`: Required if switching into silent / Do-Not-Disturb modes on Android 7+.
- **Zero GPS / Location Permissions**: No `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`.
- **Zero Network Permissions**: No internet access requested or needed.

---

## 6. Device Testing Guide (iQOO Z9 5G)
1. Enable **Developer Options** and **USB Debugging** on the iQOO Z9 5G.
2. Connect phone via USB or Wireless ADB:
   ```powershell
   adb devices
   ```
3. Install the debug APK:
   ```powershell
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Verify on device:
   - Launch "Sound Schedule".
   - Confirm dark theme appearance.
   - Long-press home screen to add the Sound Schedule widget.
