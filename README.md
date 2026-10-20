# Open Run

[![Latest release](https://img.shields.io/github/v/release/ClaudioBzrr/open-run?label=download)](https://github.com/ClaudioBzrr/open-run/releases/latest)
[![Sponsor](https://img.shields.io/badge/Sponsor-%E2%9D%A4-ea4aaa?logo=github&logoColor=white)](https://github.com/sponsors/ClaudioBzrr)

A local-first Android app for recording walks and runs. Java + XML Views, no
Compose, no backend and no account.

## Status

This is the first working slice (`0.5.1`):

- Start a run or a walk
- Foreground location service with a 1 Hz high-accuracy request
- Live moving time, distance, current/average pace, accuracy and GPS-loss state
- Pause / resume / finish
- Room persistence for sessions and track points
- History list on the home screen with a delete button and swipe-to-delete
- Activity detail screen with a rounded route map, start/end times and total duration
- Pace and elevation performance charts
- Delete activity action with confirmation
- Save or share a PNG activity card with the route and stats
- In-app language selector (English / Portuguese)
- English (default) and Portuguese (`values-pt`) resources
- Gradient home screen, redesigned live-recording screen and sneaker icon
- No account or cloud sync; Android backups disabled

New in 0.5.1:

- Rounded route-map card with a loading state, adaptive height and a
  recenter/fit-route control
- Thicker route line with a light casing, rounded caps and circular start/end
  markers; improved readability for long, zoomed-out routes
- Delete button on every history row and swipe-left-to-delete, both with a
  destructive-color confirmation dialog
- Improved share-card route rendering for long routes and a cleaner fallback
  map background
- OpenStreetMap user agent with project contact information

New in 0.5.0:

- Pace chart with a rolling 30-second window and downsampling
- Elevation chart from barometer/GPS altitude samples
- Custom dependency-free chart view
- Delete activity from the detail toolbar; Room cascades the track points
- GPS precision pass: fix timestamps use `elapsedRealtimeNanos`, vertical
  accuracy is filtered, run accuracy thresholds are tighter, and the timeout
  path no longer accepts a deliberately inaccurate first fix
- Minimum accepted movement segment increased to reduce stationary jitter

New in 0.4.0:

- Shareable 1080x1350 PNG card with the route on an OpenStreetMap background
- Start/end markers, distance, duration, pace, start/end time and elevation
- **Save image** via the system file picker and **Share** via the Android share sheet
- Share-card tile cache in app-private cache storage
- Graceful fallback to a clean map-style background when tiles cannot be loaded

New in 0.3.0:

- Route map after finishing (osmdroid + OpenStreetMap tiles)
- Start/end markers and automatic zoom to the recorded route
- Start time, end time, total duration and moving time on the detail screen
- Tapping a history item opens the same detail screen
- Language chip in the home header with English, Portuguese and system default
- `INTERNET` is requested only to load map tiles

Fixed in 0.2.0:

- Added the missing `WAKE_LOCK` permission, which caused a `SecurityException`
  and closed the app as soon as recording started
- Foreground service start is deferred until the activity is resumed and is
  protected against platform/API failures
- FusedLocation initialisation and update failures are handled without
  crashing

Not implemented yet:

- GPX export and encrypted `.runbackup` import/export
- Splits list UI (pace and elevation charts are available)
- Crash recovery of an interrupted session (interrupted rows are preserved but
  not shown yet)
- Auto-pause UI toggle, cadence, heart rate
- Battery-optimization onboarding for aggressive OEMs

## Requirements

- JDK 17
- Android SDK with platform 35 and build-tools 35.0.0
- Google Play Services on the device (FusedLocationProvider)
- Internet access for map tiles; tiles are cached by osmdroid
- Gradle is provided by the wrapper

This repository was built with:

- JDK 17.0.20.1 (Temurin)
- Android SDK 35
- Gradle 8.10.2
- Android Gradle Plugin 8.7.3

On this machine the tools live outside the repository. Set them for the
current PowerShell session:

```powershell
$env:JAVA_HOME = "C:\dev\tools\jdk17\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "C:\dev\tools\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

Create `local.properties` with the SDK path:

```properties
sdk.dir=C:/dev/tools/android-sdk
```

`local.properties` is intentionally ignored by Git.

`targetSdk` is 34 in this first slice. Moving to 35 requires handling
edge-to-edge window insets in all activities; that is deliberately left for the
next UI pass.

## Build and test

```powershell
# Unit tests for the pure-Java core (distance, filters, state machine)
.\gradlew.bat :core:test

# Debug APK
.\gradlew.bat :app:assembleDebug

# Lint report
.\gradlew.bat :app:lintDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Download the latest published APK from
[GitHub Releases](https://github.com/ClaudioBzrr/open-run/releases/latest).

If you are updating from `0.1.0`, the first launch recreates the local
development database because the Room schema changed in `0.2.0`. Updates from
`0.2.0` keep the existing activities.

Install on a connected device:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

The project is designed to be built from the command line. It does not rely on
Android Studio previews.

## Manual test checklist

There is no emulator image in this workspace; use a physical device with Google
Play Services.

1. Install the debug APK and open the app.
2. Tap **Start a walk**. Grant precise location and notifications.
3. Wait for the status to change from **Finding GPS…** to **Recording**.
4. Walk at least 100 m and confirm that distance and pace update.
5. Turn the screen off for two minutes. Confirm that the notification remains
   and that distance continues to increase.
6. Pause and resume from the notification. Confirm that paused time does not
   increase the moving time.
7. Finish from the app. The detail screen must open with the map, route line,
   start/end markers, start/end times and total duration.
8. Confirm that the pace and elevation charts are drawn below the details.
9. Go back home and tap the saved activity. The same detail screen must open.
10. Change the app language using the **EN/PT** chip in the header. The UI must
    switch immediately and survive an app restart.
11. Open the activity detail and use **Share**. The generated card must show the
    route, start/end markers, start/end times, duration and stats.
12. Use **Save image** and choose a destination in the system file picker.
13. Use the trash icon in the detail toolbar to delete an activity. Confirm that
    it disappears from the history after returning home.
14. Force-stop the app during a session and reopen it. It must not crash; the
    interrupted row is preserved in the database (not shown in history yet).

## Project layout

```text
core/                       Pure Java domain module, no Android imports
  model/                    ActivityType, RawFix, TrackPoint, Split,
                            TrackingSnapshot, SessionSummary
  stats/                    GeoMath, TrackFilter, TrackingEngine

app/
  data/                     Room entities, DAO, database, repository
  tracking/                 Foreground service, FusedLocation, state holder
  ui/                       MainActivity, RecordActivity,
                            SessionDetailActivity, PerformanceChartView,
                            history adapter
  util/                     Formatting, in-app language selection,
                            PerformanceSeriesBuilder, ShareCardGenerator
  res/values/               English strings and colors
  res/values-pt/            Portuguese strings
  res/values-night/         Dark palette
  res/xml/locales_config.xml  Android 13 per-app language metadata
  res/xml/file_paths.xml    FileProvider path for generated share cards
```

The route map and the share-card background use
[osmdroid](https://github.com/osmdroid/osmdroid) with OpenStreetMap tiles. No
Google Maps API key is required. Tile requests use the app package name as the
HTTP user agent and include the required OpenStreetMap attribution in the map
and on the generated card. For a public release with significant traffic, switch
to a tile provider whose usage policy covers your distribution.

### Why the core is separate

`core` is a `java-library` module and must not import `android.*`. The tracking
state machine and all math can be tested on the JVM by replaying synthetic
location fixes. This is also the part most likely to be reused if the app ever
gets an iOS sibling.

## How recording works

1. `RecordActivity` requests fine location (and notifications) and starts
   `TrackingService` as a foreground service.
2. The service creates a session row, starts a 1 Hz
   `PRIORITY_HIGH_ACCURACY` request and feeds fixes to `TrackingEngine`.
3. `TrackFilter` rejects bad accuracy, duplicate timestamps, impossible jumps
   and sub-0.8 m jitter.
4. Accepted points are written to Room in order. Summaries are persisted every
   five seconds and on state changes.
5. Finishing computes the final summary, marks the session `FINISHED`, updates
   the Room row and posts a one-shot event to the UI.
6. `RecordActivity` opens `SessionDetailActivity`, which loads the accepted
   points and draws the route with osmdroid.
7. The notification actions pause, resume and finish without opening the app.

The service holds a partial wake lock while recording and releases it on
stop/destroy.

### GPS and movement accuracy

- Sampling uses `PRIORITY_HIGH_ACCURACY` at 1 Hz while a foreground service
  keeps the process alive with the screen off.
- Point timing uses the fix's `elapsedRealtimeNanos`, not the time at which the
  callback happened to run. Batched or delayed fixes therefore keep their real
  spacing.
- Runs accept horizontal accuracy up to 25 m and wait for 15 m before starting;
  walks use 30 m/20 m. Points with altitude vertical accuracy worse than 30 m
  are stored without altitude instead of polluting elevation data.
- The session stays in **Finding GPS** until a fix within the accuracy
  threshold arrives. It does not start the stopwatch with a known-bad fix.
- Distance uses Haversine between accepted points and rejects implied speeds
  above the activity limit. Segments shorter than 0.8 m are ignored to reduce
  stationary jitter.
- Pausing resets the segment anchor so movement while paused is not added to
  the route.

The performance charts are derived from the accepted points: pace uses a
rolling 30-second window scaled to moving time, and elevation uses a smoothed
altitude series. Both are downsampled for drawing and do not require a network
connection.

## Localization

All user-facing text lives in resources:

- `app/src/main/res/values/strings.xml` — English (default)
- `app/src/main/res/values-pt/strings.xml` — Portuguese

Language can be changed inside the app using the **EN/PT** chip in the home
header. The dialog offers system default, English and Portuguese. On Android
13+ the choice is persisted by the platform per-app language mechanism; on
older versions the choice is stored in app preferences and applied on startup.

To add another language, copy `values/strings.xml` to
`values-<language>/strings.xml`, translate the values and add the locale to
`res/xml/locales_config.xml`. Units are metric (km and min/km). Numeric
formatting uses the device or in-app locale, so Portuguese shows `5,00 km` and
`6:30 /km`.

## Privacy notes

- The app has no account and no cloud sync. Activity data stays in the
  app-private Room database.
- `INTERNET` is requested only for OpenStreetMap map tiles in the detail screen
  and for the optional share-card background. Loading or panning the map sends
  the viewed tile coordinates to the tile server; the recorded track is not
  uploaded as a file.
- `android:allowBackup="false"` plus backup exclusion rules keep the database
  out of Google backups.
- Location samples never leave the device in this version.
- The next milestone is the encrypted export.

## Support

If Open Run is useful to you, you can support development via
[GitHub Sponsors](https://github.com/sponsors/ClaudioBzrr).

## Next milestone

The highest-value next slice is data ownership:

1. GPX export for a single activity (interoperable with Strava/Garmin).
2. Encrypted `.runbackup` with PBKDF2/AES-GCM and SAF file picker.
3. Import/merge by session UUID.
4. A "last backup" reminder.

After that: splits chart, interrupted-session recovery and auto-pause controls.
