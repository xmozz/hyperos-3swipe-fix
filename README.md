# Home Fix

A minimal Android **AccessibilityService** that fixes a Xiaomi **HyperOS** gesture-navigation bug:
swiping up from an app to go home sometimes lands you in the **app drawer**, the **app-drawer search**
(with a stale query), or the **home-screen search** (`App Finder`) instead of the home screen.

## What it does

When the system lands in one of those "search / drawer" screens after a home gesture, the service
fires `GLOBAL_ACTION_HOME` (once, or twice with a configurable gap) so you end up on the home
screen. It does not touch normal home navigation and has guards against loops.

## How the detection works

The service listens to `TYPE_WINDOW_STATE_CHANGED` and inspects the active window **content**
(class-name based detection does not work on HyperOS — the drawer and the home screen live in the
same `com.miui.home` window):

| Screen | Detected by |
| --- | --- |
| App drawer / drawer search | `com.miui.home` window containing `id/all_apps_setting_button` or category tabs (`Tab N of M`) |
| Home screen | `com.miui.home` window containing `id/workspace` / `id/widget_container` |
| Home-screen search (App Finder) | package `com.mi.appfinder` |
| Any other app | anything else |

It only acts when it has seen a **real app** (e.g. Instagram) shortly before landing in the
drawer/search — so intentionally opening the drawer from the home screen is ignored. The window
between "left an app" and "drawer opened" is configurable.

## Features

- **Enable/disable** toggle.
- **Auto-home window** slider — how long after leaving an app the fix still triggers
  (lower = fewer false triggers).
- **Reaction delay** slider — delay before the first `HOME` action (lower = faster).
- **2nd swipe delay** slider — gap between the first and second `HOME` action, used when the
  drawer search sits on top of the drawer (search → drawer → home).
- **Debug mode** switch — optional verbose log + in-app log viewer (off by default).
- Persistent low-priority foreground-service notification (so HyperOS does not kill the service).
- Boot receiver to restore the notification after reboot.
- No ads, no network, no analytics.

## Requirements

- Android Studio with JDK 17+ and Android SDK 35.
- Android 9+ (minSdk 28), target Android 15 (API 35).
- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9.

## Build

```bash
./gradlew assembleDebug        # or: gradlew.bat assembleDebug
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`
(or `.../release/app-release.apk`).

> The `release` build is signed with a local keystore configured through
> `keystore.properties` (see `app/build.gradle.kts`). If that file is absent, the release build
> falls back to the debug signing config so it still builds.

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Setup (do all three)

1. **Enable the toggle** in the app.
2. **Turn on the accessibility service** — tap *Open accessibility settings* and enable
   **Home Fix**.
3. **Disable battery optimization** — tap *Disable battery optimization* and allow the exemption,
   otherwise HyperOS kills the service.

A persistent **"Home Fix — Auto-home gesture fix active"** notification is shown while the service
runs.

## Troubleshooting

If the fix does not trigger:

1. **Accessibility enabled** — the status line in the app must read *enabled*.
2. **Battery optimization disabled** — allow the exemption, and in
   *Settings → Battery → App battery saver*, set **Home Fix** to *No restrictions*.
3. **"Allow restricted settings"** — some HyperOS ROMs block accessibility services installed from
   ADB/third-party stores. Open *Settings → Apps → Manage apps → Home Fix → ⋮ → Allow restricted
   settings*, then retry.
4. **Autostart** — enable *Settings → Apps → Manage apps → Home Fix → Autostart* so the boot
   receiver works.
5. **Read the log** — enable *Debug mode* in the app, reproduce the bug, then use *Share log* or:

   ```bash
   adb logcat -s HomeFix
   ```

   You should see `fix    : started (came from ...)`, `FIX    : [drawer] -> GLOBAL_ACTION_HOME`,
   and `home   : stable home, clearing context`.

### Tuning

- **Lands in the drawer and stays there** after opening an app via the drawer *search* → raise the
  **2nd swipe delay**.
- **Drawer flashes for a moment** before going home → lower the **reaction delay**.
- **Fires when you intentionally open the drawer** shortly after closing an app → lower the
  **auto-home window**.

## Permissions

Only what is needed:

- `BIND_ACCESSIBILITY_SERVICE` (on the service) — to observe window transitions.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — the persistent notification.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — the battery-exemption toggle.
- `RECEIVE_BOOT_COMPLETED` — restore the notification after reboot.

## License

MIT
