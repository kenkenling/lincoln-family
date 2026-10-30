# Practice Partner for Android

Standalone Android app package for a reusable music-practice planner. It is intentionally separate from the Lincoln Family web portal and does not contain Lincoln's encrypted family library, passphrase, OAuth client ID, or private account data.

## Current build

- Three screens: Today, Assign, and Summary.
- Create date-scoped assignments from YouTube links or device video URIs, with optional clips, coaching notes, sheet music, explanations, and repetition targets.
- Track daily repetitions, completed assignments, elapsed active-practice time, and a dated event log.
- Practice in Listen, Listen & Play, or Play Yourself mode with the teacher reference and camera on one guided screen.
- Start every recording explicitly after a three-second countdown. Takes remain in app-specific storage until they are watched, shared, or deleted.
- Import legacy five-column CSV files or the full dated CSV format, with quote-safe round trips for titles and notes.
- Migrate existing version-one tasks, completion state, timer data, and event history on first launch.

This is a local-first, single-device app. The reference stays in its player and is not copied into the learner's recording. The Android share sheet can pass a selected take to compatible apps, but recordings are never uploaded automatically. Direct YouTube API upload, Google Sheets API access, XLSX parsing, multi-device sync, parent/learner accounts, and cloud backups are not included.

## Product and policy gates

Before releasing YouTube playback/upload broadly, decide and document the app's target audience and complete the relevant Google Play and YouTube API compliance review. YouTube reference media stays in YouTube's player and is not copied into the learner's recording. Direct API upload/playlist operations remain out of this starter build until eligibility is confirmed.

## Build

Use JDK 21 and install Android SDK Platform 37.2. Open this directory in Android Studio or run:

```bash
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

The Android application ID is `com.practicepartner.music`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
