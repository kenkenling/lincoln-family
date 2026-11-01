# LK Practice Phase C: universal practice engine

Date: November 1, 2026
App version: `0.10.0` (`versionCode 15`)
Plan source: `LK_Practice_Plan.docx` from `LK_Practice_Plan.zip`

> This file records the Phase C release. The subsequent local family-control
> checkpoint is complete in app version `0.11.0`; see
> `LK_PRACTICE_PHASE_D_LOCAL_CONTROLS.md`.

## Outcome

Phase C is implemented as a local-first, instrument-neutral practice engine.
The child flow remains Today-first. The parent configures the plan; the child
chooses an allowed practice step, starts immediately, and finishes with an
optional one-tap reflection.

No account, cloud synchronization, teacher invitation, uploaded media, billing,
or online consent system was added.

## Assignment contract

Every assignment now records:

- student and instrument ownership;
- warm-up, technique, repertoire, or other kind;
- repetition goal (`1`–`20`) or time goal (`1`–`60` minutes);
- selected Listen, Play Along, and/or Play Solo steps;
- optional reference video, clip range, sheet music, coach focus, and purpose;
- a monotonically increasing revision.

The parent editor includes three small quick-start templates. Violin and piano
receive instrument-specific wording; every other instrument receives a generic
warm-up, technique, and repertoire set. A no-reference assignment safely uses
Play Solo.

## Session and history behavior

A session snapshots the assignment title, revision, kind, goal, and target at
start. Editing the assignment later does not rewrite completed history.

Sessions record:

- active elapsed time;
- repetitions completed in that session;
- `ACTIVE`, `COMPLETED`, `ENDED`, or `INTERRUPTED` outcome;
- optional `PROUD`, `MAKING_PROGRESS`, or `NEED_HELP` reflection.

`COMPLETED` means the configured goal was reached. Tapping Finish before the
goal is met records `ENDED` (“Finished for now”), preserving honest progress
without treating the attempt as failure. A process restart closes any open
session as `INTERRUPTED` using its last persisted elapsed time.

Time goals accumulate across multiple sessions for the same assignment and
date. Repetition goals continue to use the daily repetition counter. Older
unfinished assignments remain in their original date history; they are not
silently moved to another day.

## Motivation and summary

The child reflection contains no score. Its response only tells a parent what
kind of support may help.

Parent Space now shows:

- assignment progress for either goal type;
- each session’s duration, mode, outcome, assignment version, and reflection;
- a Monday-through-Sunday pattern with practiced days and total time;
- explicit copy that the pattern is not a streak and rest days are okay.

## Persistence and portability

Per-child JSON snapshots are version 6. Version 5 snapshots are read once and
written into the version 6 key. The last-known-good backup behavior remains.

The workbook template is version 6 and adds assignment kind, goal, steps,
revision, per-assignment practice seconds, session/reflection counts, and a
`Practice Sessions` worksheet. CSV carries the assignment fields in both
directions. Only assignment rows are imported; session and progress worksheets
remain readable exports rather than a competing source of truth.

## Verification

Automated:

- `38` unit tests, `0` failures, `0` errors;
- Android lint: `0` errors and two dependency-version advisories;
- `:app:assembleDebug` successful;
- `git diff --check` clean.

Emulator (`emulator-5554`):

- installed and confirmed `versionName=0.10.0`, `versionCode=15`;
- created and re-used the local Parent Space PIN;
- selected the Violin repertoire template and saved a 10-minute assignment;
- confirmed Today exposed only the assignment’s Play Solo step;
- started a timed session and observed live daily/session time;
- finished before the goal and confirmed `Finished for now`;
- recorded `Making progress`;
- confirmed Parent Space relocked;
- confirmed weekly pattern, session duration, assignment version, outcome, and
  reflection in Practice Summary.

The emulator-only test assignment and reflection remain in emulator app data.

## Following checkpoint

The parent-controlled export, child-profile deletion, retention copy, and
reversible assignment archiving planned after Phase C are complete in
`LK_PRACTICE_PHASE_D_LOCAL_CONTROLS.md`.

A small-phone and enlarged-text spot check is complete in the following
checkpoint. TalkBack, reduced-motion, contrast instrumentation, and a broader
device matrix remain open. Authenticated teacher invitations and private
cross-device synchronization also remain a later architecture decision because
they require a real identity, authorization, revocation, consent, and backend
design.
