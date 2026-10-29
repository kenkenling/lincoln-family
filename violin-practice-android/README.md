# Practice Partner for Android

Standalone Android app package for a reusable music-practice planner. It is intentionally separate from the Lincoln Family web portal and does not contain Lincoln's encrypted family library, passphrase, OAuth client ID, or private account data.

## Current starter build

- Three screens: Today, Assign, and Summary.
- Add assignments with YouTube links or phone video URIs, optional start/end seconds, and repetition counts.
- Persist tasks, completed task IDs, elapsed practice time, and an event log on the device.
- Start a practice session with the teacher video and a live front-camera preview on the same screen.
- Record a take with CameraX, save it in Movies / Practice Partner, review it, and share it through the Android share sheet.
- Import/export the simple CSV assignment format (title, source, start_seconds, end_seconds, repetitions), which can be opened in Excel and Google Sheets.

This is an early local-first foundation. The reference stays in its player and is not copied into the recording. The share sheet can pass a saved take to compatible apps; direct YouTube API upload, direct Google Sheets API access, XLSX parsing, multi-device sync, parent/learner accounts, and cloud backups are not yet included.

## Product and policy gates

Before releasing YouTube playback/upload broadly, decide and document the app's target audience and complete the relevant Google Play and YouTube API compliance review. YouTube reference media stays in YouTube's player and is not copied into the learner's recording. Direct API upload/playlist operations remain out of this starter build until eligibility is confirmed.

## Build

Open this directory in Android Studio or run `./gradlew :app:assembleDebug`. The Android application ID is `com.practicepartner.music`.
