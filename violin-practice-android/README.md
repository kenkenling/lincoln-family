# Practice Partner for Android

Practice Partner is a simple parent-to-child violin-practice app. It is separate from the Lincoln Family web portal and is local-first: recordings and device file paths remain private unless a parent explicitly shares or exports something.

## Version 0.6.1 workflow

- **Today:** choose Listen, Listen & Play, or Play Yourself; follow the assigned clip; record repetitions; and mark work complete.
- **Assign:** create, edit, move, remove, import, or export assignments for any date. Sheet music is optional.
- **Summary:** browse previous practice dates, elapsed practice time, repetitions, completion, and saved takes.
- Practice time pauses when the app leaves the screen and resumes when the active session returns.
- Camera and microphone permission can be requested again. Recording deletion requires confirmation and removes both the managed file and its log entry.
- The app keeps a last-known-good local snapshot and reports when damaged records were recovered or skipped.
- CSV export removes private local URIs. Imported device-video assignments remain as reattachment placeholders instead of pretending the old URI is portable.

## Excel and Google Drive

The app creates a real `.xlsx` workbook through Android's Google Drive document provider. It does not request a Google password, OAuth token, broad Drive access, or sharing permission.

Before writing, Practice Partner verifies:

1. The selected provider is the official Google Drive app.
2. The selected Drive account matches the email entered in Profile.
3. An existing file is a Practice Partner workbook, not an unrelated spreadsheet.
4. The newly written workbook can be read back and passes its integrity check.

After connection, assignment and profile changes show as unsynced and are written automatically after editing settles. **Sync assignments to Excel** is available for an immediate update. **Load assignments from Excel** imports teacher or parent additions and edits, but is blocked until newer app assignment/profile changes are saved to avoid accidental overwrite.

Assignments are the bidirectional data set. Practice timing, repetitions, completion, and event details remain local-first for now. The workbook receives a current practice snapshot when an assignment/profile sync or a manual workbook save happens, but practice-history sheets are not loaded back into the app.

The workbook contains:

- `Profile`
- `Assignments`
- `Daily Progress`
- `Daily Summary`
- `Practice Log`

Google Drive controls the final network upload. The app confirms that the file was written through the Drive provider, but does not claim that Drive's server upload has completed. See [EXCEL_SYNC_SETUP.md](EXCEL_SYNC_SETUP.md).

Recordings, private filesystem paths, local video URIs, and local sheet-music URIs are excluded from Excel. YouTube reference links remain available. Direct YouTube channel authorization/upload is intentionally outside this release; saved takes continue to use Android Share.

Assignment CSV files now include `assignment_id`, so exporting and reimporting updates the same assignment instead of creating a duplicate. Older five-column CSV files remain supported.

## Build

Use JDK 21 and Android SDK Platform 37.2:

```bash
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
