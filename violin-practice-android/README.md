# LK Practice for Android

LK Practice is a child-first music-practice app with a protected Parent Space.
It is separate from the Lincoln Family web portal and remains local-first:
recordings and private device paths are not shared unless a parent explicitly
uses Android Share or exports a file.

Primary message: **Practice Makes Progress.**

## Version 1.0.0-beta.1 workflow

- **Choose profile:** the app opens to named child cards and a Parent Space card.
- **Today:** a child enters a profile in one tap and sees the existing
  sky-themed mission-first practice screen.
- **Parent Space:** assignments, summaries, profiles, local files, video
  sharing, and recording deletion require a local four-digit parent PIN.
- **Family profiles:** each child has separate assignments, progress, events,
  practice sessions, recordings, and optional Excel file.
- **Family data controls:** a parent can export one child to a ZIP containing
  readable profile, assignment, history, and available app-managed recordings.
  Deletion requires typing the child’s name, cannot remove the last profile,
  and leaves any external Excel file in place.
- **Assignments:** parents can create, edit, move, archive, restore, remove,
  import, or export a plan for any date. Archiving hides an assignment from
  Today while preserving its history. Violin, piano, and generic quick-start
  templates keep setup simple. Each assignment chooses a kind, a repetition or
  time goal, and one or more allowed practice steps. A reference video and
  sheet music are optional.
- **Practice sessions:** each session records its child, assignment, instrument,
  assignment version, goal snapshot, mode, status, elapsed time, repetitions,
  and optional one-tap reflection.
- **Summary:** parents can browse practice dates, elapsed time, repetitions,
  completion, saved takes, session outcomes, reflections, and a gentle
  seven-day practice pattern without streak penalties.
- Practice time pauses when the app leaves the screen. Parent Space locks when
  the app backgrounds, except while an authorized Android file picker is open.
- Recorded takes use sortable names such as
  `2026-10-31 - Lincoln - Boreas Violin 2 - Take 01.mp4`. The same title is
  supplied to Android Share for manual uploads.
- The app keeps a last-known-good local snapshot and reports when damaged
  records were recovered or skipped.

The current release has no parent account, cloud sync, teacher invitation,
Google Drive, Google Sheets, or YouTube channel connection. The PIN is a local
shared-device gate, not online authentication.

## Local Excel files

Each child can have one local `.xlsx` file chosen through Android's local-only
document picker. From **Parent Space > Profile & local file**, a parent can:

- Create a new `LK Practice - Student Name.xlsx` workbook.
- Choose an existing LK Practice or compatible legacy Practice Partner workbook.
- Save current assignments and a readable practice snapshot.
- Load assignment additions or edits from the file.
- Open the file in an installed spreadsheet app.

Assignment and profile changes are saved automatically after editing settles.
**Save changes to local file** forces an immediate update. Loading is blocked
while newer app changes are waiting to be saved.

Assignments are bidirectional. Practice timing, repetitions, completion,
sessions, and events remain local-first; workbook history sheets are readable
snapshots and are not loaded back into the app.

The workbook contains:

- `Profile`
- `Assignments`
- `Daily Progress`
- `Daily Summary`
- `Practice Sessions`
- `Practice Log`

See [LOCAL_FILE_SETUP.md](LOCAL_FILE_SETUP.md) for exact columns.

Video content, private filesystem paths, local video URIs, and local sheet-music
URIs are excluded from Excel. A safe recording filename is included in
`Practice Log`. Teacher YouTube reference links remain available inside an
assignment without connecting a YouTube account.

CSV files include stable assignment IDs, student and instrument ownership,
assignment kind, goal, practice steps, and revision. Older five-column CSV
files remain supported.

## Architecture and roadmap

The audited baseline, migration behavior, architecture diagram, security
boundary, and regression checklist are documented in
[docs/LK_PRACTICE_BASELINE_AND_PHASE_B.md](docs/LK_PRACTICE_BASELINE_AND_PHASE_B.md).
The completed universal practice-engine checkpoint is documented in
[docs/LK_PRACTICE_PHASE_C.md](docs/LK_PRACTICE_PHASE_C.md).
The completed local family-control and retention checkpoint is documented in
[docs/LK_PRACTICE_PHASE_D_LOCAL_CONTROLS.md](docs/LK_PRACTICE_PHASE_D_LOCAL_CONTROLS.md).
The ticket-by-ticket implementation boundary is tracked in
[docs/LK_PRACTICE_PLAN_STATUS.md](docs/LK_PRACTICE_PLAN_STATUS.md).

## Build

Use JDK 21 and Android SDK Platform 37.2:

```bash
./gradlew testDebugUnitTest lintDebug :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
