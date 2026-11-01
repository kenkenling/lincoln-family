# LK Practice local family controls

Date: November 1, 2026
App version: `0.11.0` (`versionCode 16`)
Plan source: `LK_Practice_Plan.docx` from `LK_Practice_Plan.zip`

## Outcome

The next local-first checkpoint after the universal practice engine is
complete. A parent can export or delete one child's app-managed data, and can
archive an assignment without destroying its practice history.

No parent account, teacher invitation, cloud synchronization, uploaded media,
billing, or online consent system was added.

## Family export

Parent Space can export the selected child to a ZIP containing:

- a short `README.txt`;
- readable `profile.json`;
- `assignments.csv`;
- `practice-history.json`;
- available app-managed recordings under `recordings/`.

The export is scoped to one child. Text metadata excludes private Android
content URIs and absolute device paths. Missing or unavailable recordings do
not expose another profile's media.

## Child-profile deletion

Deletion is parent-only and requires typing the child's exact name. The app:

- refuses to delete the household's last child profile;
- removes that child's local practice snapshot and scoped settings;
- removes app-managed recordings only when no other profile references them;
- leaves an external Excel workbook in place because it is outside app-managed
  storage;
- releases the saved workbook document permission when no remaining profile
  uses that workbook;
- erases legacy single-profile snapshot copies when they belong to the deleted
  child;
- switches safely to a remaining child.

The dialog explains these retention rules before deletion.

## Reversible assignment archiving

Archiving is the default non-destructive way to remove an assignment from
Today. An archived assignment:

- is hidden from the child's Today screen;
- remains visible in Parent Summary;
- keeps all sessions, events, progress, recordings, and reflections;
- can be restored;
- increments its assignment revision when archived or restored.

Permanent Remove remains available as a separate adult action.

## Persistence and workbook changes

Per-child JSON snapshots and workbook templates are version 7. Version 6 and
version 5 snapshots remain readable for migration.

CSV and Excel assignment rows include `Archived UTC`. The workbook still uses
the app as the source of truth for practice history: assignments load in both
directions, while progress, session, and event sheets are readable snapshots.

## Verification

Automated:

- `42` unit tests, `0` skipped, `0` failures, `0` errors;
- Android lint: `0` errors and two dependency-version advisories;
- `:app:assembleDebug` successful;
- `git diff --check` clean.

Emulator (`emulator-5554`):

- exported Lincoln's profile and inspected every ZIP entry;
- confirmed text metadata contains no private content URI or absolute app path;
- created and deleted a temporary second child using exact-name confirmation;
- confirmed the final-profile deletion guard;
- confirmed Lincoln's retained practice summary and reflection after deleting
  the temporary profile;
- archived `Repertoire passage` and confirmed it disappeared from Today;
- confirmed the assignment and its session remained in Summary as
  `Archived from Today`;
- restored it and confirmed it returned to Today without losing history.
- installed the final APK at `360dp` width with `130%` text and confirmed the
  chooser and Today screen remained readable;
- confirmed Parent Space scrolls at that setting so every adult tool remains
  reachable, then restored the emulator to its original size and `100%` text.
- enabled emulator airplane mode, started a no-reference mission, force-stopped
  the app, relaunched while still offline, and confirmed the saved session
  became `INTERRUPTED` instead of being lost or duplicated;
- restored connectivity and verified airplane mode returned to off.

## Remaining boundary

The next plan step that changes the architecture is authenticated adult and
teacher collaboration. It should not be simulated with a Drive link or shared
spreadsheet. It requires decisions for identity, household ownership,
invitation and revocation, child consent and privacy, authorization rules,
backend storage, conflict resolution, media handling, and account deletion.

TalkBack, reduced-motion, measured contrast, and a broader target-device matrix
can continue locally before that architecture decision.
