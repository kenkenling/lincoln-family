# Practice Partner for Android

Practice Partner is a simple parent-to-child violin-practice app. It is separate from the Lincoln Family web portal and is local-first: recordings and device file paths remain private unless a parent explicitly shares or exports something.

## Version 0.8.0 workflow

- **Today:** the child lands directly on a sky-themed practice screen with a large one-tap start button, practice modes, and today’s assignments.
- **Grown-up tools:** Assign, Summary, and Profile & local file are grouped behind the small settings button instead of permanent child-facing navigation.
- **Assign:** parents or coaches can create, edit, move, remove, import, or export assignments for any date. Sheet music is optional.
- **Summary:** parents or coaches can browse previous practice dates, elapsed practice time, repetitions, completion, and saved takes.
- Practice time pauses when the app leaves the screen and resumes when the active session returns.
- Camera and microphone permission can be requested again. Recording deletion requires confirmation and removes both the managed file and its log entry.
- Recorded takes use sortable names such as `2026-10-31 - Lincoln - Boreas Violin 2 - Take 01.mp4`. The same title is supplied to Android Share for manual YouTube uploads.
- The app keeps a last-known-good local snapshot and reports when damaged records were recovered or skipped.
- CSV export removes private local URIs. Imported device-video assignments remain as reattachment placeholders instead of pretending the old URI is portable.

## Local Excel file

Profile no longer contains Google Drive or YouTube channel connections. The app uses one local `.xlsx` file chosen through Android's local-only document picker.

From Profile, a parent can:

- Create a local Practice Partner workbook.
- Choose an existing Practice Partner workbook.
- Save current app assignments and a practice snapshot to the file.
- Load assignment additions or edits from the file.
- Open the file in an installed spreadsheet app.

Assignment and profile changes are saved automatically after editing settles. **Save changes to local file** is available for an immediate update. Loading is blocked while newer app changes are waiting to be saved, which avoids replacing them accidentally.

Assignments are the bidirectional data set. Practice timing, repetitions, completion, and event details remain local-first for now. The workbook receives a current practice snapshot when assignments or the profile are saved, but practice-history sheets are not loaded back into the app.

The workbook contains:

- `Profile`
- `Assignments`
- `Daily Progress`
- `Daily Summary`
- `Practice Log`

See [LOCAL_FILE_SETUP.md](LOCAL_FILE_SETUP.md).

Video content, private filesystem paths, local video URIs, and local sheet-music URIs are excluded from Excel. The safe recording filename is included in `Practice Log` so takes can be matched with manually shared or uploaded copies. Recorded practice takes stay inside Practice Partner and use Android Share only when a parent chooses. Teacher YouTube reference links inside assignments remain available, but there is no YouTube account or channel connection.

When upgrading from the older cloud-connection build, retired Drive and YouTube profile metadata is removed and the local workbook must be configured once.

Assignment CSV files now include `assignment_id`, so exporting and reimporting updates the same assignment instead of creating a duplicate. Older five-column CSV files remain supported.

## Build

Use JDK 21 and Android SDK Platform 37.2:

```bash
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
