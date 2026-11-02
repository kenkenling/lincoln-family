# LK Practice Code Review

Reviewed against: `LK_Practice_Plan.zip` (v1.0, October 31 2026)
Codebase version: `0.11.0` (versionCode 16)
Review date: 2026-11-01

Each item has a priority, file locations, what to change, and acceptance criteria.
Mark items `[x]` as they are completed.

---

## Architecture Improvements

### A1. Extract PracticeViewModel into focused managers — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - `PracticeTimerManager` (173 lines) — timer lifecycle, tick persistence, `PracticeTimerReading` StateFlow, injectable `PracticeTimerTicker` for tests.
  - `WorkbookSyncManager` (836 lines) — Excel auto-save, read/write/connect, assignment CRUD, CSV import/export, archive/restore/remove. Introduced `WorkbookStorage` and `WorkbookSyncSettings` interfaces for testability.
  - `RecordingManager` (138 lines) — file creation, managed-path validation, orphan cleanup, workbook permission release. `RecordingDeleteResult` return type.
  - `PracticeSessionManager` (338 lines) — start/finish/interrupt sessions, repetitions, reflections, event logging.
  - `AdultAccessManager` (74 lines) — PIN create/verify, lock/unlock, external-action tracking.
  - `ProfileDataManager` (213 lines) — add/save/delete/export child profiles with structured result types.
  - `AssignmentIdentity` (26 lines) — `assignmentKey()` and `friendlyDate()` shared by ViewModel and WorkbookSyncManager.
  - `PracticeViewModelFactory` (17 lines) — factory extracted from ViewModel companion.
  - `PracticeViewModel` reduced from 1,752 to 597 lines.
  - 4 new test files: `PracticeTimerManagerTest`, `RecordingManagerTest`, `PracticeSessionManagerTest`, `WorkbookSyncManagerTest`. 54/54 tests pass.
- **Review notes:** Clean extraction. All original behavior preserved. The `WorkbookSyncManager` at 836 lines is the largest new file; it absorbed assignment CRUD (`saveAssignment`, `importCsv`, etc.) in addition to the sync logic. This is acceptable since those operations are tightly coupled to workbook state. If it grows further during Phase C/D, consider splitting assignment CRUD into its own manager.

---

### A2. Migrate SharedPreferences to Room database — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - **`PracticeDatabase.kt`** (398 lines) — 6 entities (`HouseholdEntity`, `ChildProfileEntity`, `AssignmentEntity`, `DailyProgressEntity`, `PracticeEventEntity`, `PracticeSessionEntity`), 6 DAOs, `@Database(version = 1, exportSchema = true)`. Foreign keys with `CASCADE` delete from `ChildProfileEntity` to all practice data. Indexed on `childProfileId`, `date`, `assignmentId`, `sessionId`, and `sortOrder`. `DatabaseRunner` abstraction: `ExecutorDatabaseRunner` (production, single-thread executor with re-entrancy check) and `DirectDatabaseRunner` (tests). `PracticeDatabaseProvider` double-checked-locking singleton.
  - **`RoomEntityMappers.kt`** (285 lines) — bidirectional `toEntity()`/`toModel()` for all 6 types. `PracticePersistenceChangeSet` data class with per-table upsert/delete lists. `practicePersistenceChanges(previous, next)` diff function that compares two snapshots and produces only the changed rows. `practiceSnapshotFromEntities()` assembles a `PracticeSnapshot` from DAO results. Enum and JSON column helpers with safe fallback defaults.
  - **`RoomPersistence.kt`** (107 lines) — `loadPracticeSnapshot()`, `replacePracticeSnapshot()` (transactional full replace), `applyPracticeChanges()` (transactional granular upsert/delete), `deletePracticeData()`. Empty-list guards on all DAO calls.
  - **`LegacyRoomMigration.kt`** (378 lines) — transactional one-time migration from SharedPreferences JSON to Room. Handles: v1 (legacy `practice_partner_v1`), v5-v7 snapshots, v7 backups, single-profile-to-household upgrade, per-profile migration tracking, damaged-primary-snapshot backup recovery. Cleans up legacy SharedPreferences keys after migration. `normalizeHousehold()` extracted for reuse.
  - **Granular timer persistence:** `practicePersistenceChanges()` produces a diff — a timer tick that changes only `DailyProgress.elapsedSeconds` and one `PracticeSession.elapsedSeconds` results in exactly 2 row upserts (verified by `sqliteTotalChanges()` assertion in test).
  - **SharedPreferences retained for:** PIN hash/salt, local revision counters, file save timestamps, migration flags, privacy consent. These are fast key-value lookups that don't benefit from Room.
  - **Tests — `RoomPracticeStoreTest.kt`** (432 lines, 7 tests):
    1. `legacyHouseholdAndEveryProfileSnapshotMigrateWithoutLoss` — multi-profile v7 JSON → Room, verifies data equality and legacy key cleanup.
    2. `damagedPrimarySnapshotRecoversFromBackupDuringMigration` — corrupt primary, valid backup → recovers with warning.
    3. `originalSingleProfilePreferencesMigrateIntoRoom` — v1 legacy → Room, including completed tasks, elapsed time, legacy prefs cleared.
    4. `timerStyleChangeOnlyUpsertsProgressAndActiveSessionRows` — verifies exactly 2 SQLite rows changed for a timer tick (the key performance requirement).
    5. `granularChangesInsertUpdateAndDeleteOnlyChangedRecords` — assignment update + event deletion, verifies only changed rows are touched.
    6. `deletingAChildProfileCascadesAllRoomPracticeRows` — foreign-key cascade verified, sibling profile data untouched.
    7. `sixMonthsOfPracticeLoadsInUnderTwoHundredMilliseconds` — 180 days × (1 assignment + 3 sessions + 5 events + 1 progress) = 1,800 rows, average load under 200ms over 5 runs.
  - 109 tests pass.
- **Review notes:** The diff-based persistence (`practicePersistenceChanges`) is the architectural win — it turns the full-snapshot-rewrite problem into targeted row updates. The `sqliteTotalChanges()` assertion in the timer test is rigorous: it proves at the SQLite level that only 2 rows are modified per tick, not 6+ tables rewritten. The `ExecutorDatabaseRunner` with re-entrancy check is a pragmatic solution for Room's main-thread prohibition without needing coroutines in the store layer. Foreign-key cascades mean profile deletion automatically cleans up all child data without explicit per-table deletes. The legacy migration handles every historical format (v1, v5, v6, v7, v7-backup, single-profile, household) in a single transactional pass.

---

### A3. Replace hand-rolled JSON with kotlinx.serialization — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - Added `kotlin.plugin.serialization` v2.2.20 and `kotlinx-serialization-json:1.9.0` to Gradle.
  - `@Serializable` added to 7 model classes in `Models.kt`: `Household`, `ChildProfile`, `Assignment`/`PracticeTask`, `DailyProgress`, `PracticeEvent`, `PracticeSession`, `PracticeSnapshot`.
  - `StoreSerialization.kt` (197 lines) — shared `storeJson` config (`ignoreUnknownKeys`, `encodeDefaults`, `explicitNulls`, `coerceInputValues`), `PracticeSnapshotJsonCodec` and `HouseholdJsonCodec` with encode/decode.
  - Decode path uses a two-pass approach: first parse into `StoredPracticeSnapshotRows` (with `List<JsonElement>`), then decode each row individually with `decodeFromJsonElement` so a damaged row skips without breaking the entire snapshot — preserving the original per-record recovery behavior.
  - `enabledModes` fallback logic preserved: checks if the raw JSON has a `JsonArray` for `enabledModes` before accepting the decoded set, falling back to full mode set or PLAY_YOURSELF based on sourceType.
  - Legacy v1 migration in `PracticeStore` uses `@Serializable` `LegacyPracticeTask` and `LegacyPracticeEvent` data classes with a `decodeLegacyRows` helper.
  - `PracticeStore` reduced from 624 to 279 lines. `FamilyStore` from 316 to 251 lines.
  - Deleted `JsonExtensions.kt` (13 lines) and `JsonExtensionsTest.kt` (36 lines) — no longer needed since `org.json` is gone from the store layer.
  - `org.json` only remains in `ProfileArchiveWriter.kt` for the ZIP export format (out of scope for this item).
  - `StoreSerializationTest.kt` (210 lines) — 4 tests: v7 round-trip format match, unknown-field tolerance with damaged-row skip counting, older snapshot migration (missing family/mode fields), household v1 round-trip with unknown fields.
  - 59 tests pass (57 prior + 2 new serialization tests, minus 2 deleted JsonExtensions tests = net +2 from new StoreSerializationTest with 4 test methods).
- **Review notes:** The two-layer decode (`StoredPracticeSnapshotRows` → per-row `decodeFromJsonElement`) is clever — it gets the benefits of kotlinx.serialization while preserving damaged-record isolation. The `coerceInputValues = true` JSON config handles enum migration gracefully (unknown enum values fall back to defaults). The round-trip test against literal JSON strings from the previous manual format guarantees wire compatibility.

---

### A4. Remove duplicate code between PracticeStore and FamilyStore — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - `StoreConstants.kt` (17 lines) — shared `LOCAL_ONLY_MIGRATION_KEY` and `RETIRED_CONNECTION_KEYS`, `internal` visibility.
  - `JsonExtensions.kt` (13 lines) — shared `optNullableString`, `optIntOrNull`, `optLongOrNull`, `internal` visibility.
  - `JsonExtensionsTest.kt` (36 lines) — covers missing/null/blank/present for strings and missing/null/zero/present for numbers.
  - Removed from `PracticeStore.kt` (97 lines deleted): `loadProfile`, `saveProfile`, `loadLocalRevision`, `bumpLocalRevision`, `loadLastFileSaveRevision`, `saveLastFileSaveRevision`, `loadLastFileSaveAt`, `saveLastFileSaveAt`, `migrateRetiredConnections()`, duplicate `RETIRED_CONNECTION_KEYS`, duplicate `LOCAL_ONLY_MIGRATION_KEY`, duplicate `optNullableString`/`optIntOrNull`/`optLongOrNull`. Also removed the no-profile overloads `load(currentDate)`, `loadWithStatus(currentDate)`, and `save(snapshot)`.
  - Removed from `FamilyStore.kt` (17 lines deleted): duplicate `optNullableString`, duplicate `LOCAL_ONLY_MIGRATION_KEY`, duplicate `RETIRED_CONNECTION_KEYS`.
  - `PracticeStore` public API now: `load(profile, date)`, `loadWithStatus(profile, date)`, `save(profile, snapshot)`, `delete(profile)`, `referencedRecordingPaths(profiles)`.
  - 56 tests pass (54 existing + 2 new JsonExtensionsTest).
- **Review notes:** Clean extraction. Migration logic (`migrateRetiredConnections`) now lives only in `FamilyStore`, which is always called first via ViewModel init. The removed PracticeStore methods had no remaining callers.

---

### A5. Add dependency injection — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - **`PracticeViewModel` constructor no longer takes `Context`.** Parameters: `PracticeDataStore`, `FamilyDataStore`, `WorkbookStorage`, `RecordingManager`, `ProfileArchiveDestination`, plus optional `elapsedRealtime`, `wallClock`, `dateProvider`, `coroutineScope`, `timerTicker` — all with production defaults.
  - **`ProfileArchiveDestination`** (7 lines) — `fun interface` wrapping `open(uriText: String): OutputStream`. Replaces the `Context.contentResolver.openOutputStream` call that was the last Android dependency in the ViewModel/ProfileDataManager chain.
  - **`PracticeViewModelFactory.kt`** — `practiceViewModelFactory(store, familyStore, workbookStore, recordingManager, profileArchiveDestination)` takes only interfaces. `androidProfileArchiveDestination(context)` provides the Android implementation.
  - **`PracticeViewModel.factory(context)`** convenience companion — provides Android-backed defaults (`PracticeStore`, `FamilyStore`, `LocalWorkbookStore`, `RecordingManager.from`, `androidProfileArchiveDestination`) so `MainActivity` still has a single-line call.
  - **`ProfileDataManager`** takes `ProfileArchiveDestination` instead of `Context` + `ContentResolver`.
  - **Tests run without Robolectric:** `PracticeViewModelTest` has zero Android imports, no `@RunWith(RobolectricTestRunner::class)`, no `@Config`. The `ProfileArchiveDestination` is faked with `ByteArrayOutputStream()`. All 14+ ViewModel tests run as pure JVM/JUnit4.
  - 102 tests pass.
- **Review notes:** Option A (constructor injection with defaults) was the right call — it's simple, explicit, and avoids the Hilt annotation-processing overhead for a single-ViewModel app. The `fun interface ProfileArchiveDestination` is a clean seam: the production implementation is 2 lines in the factory, and tests fake it with a one-liner lambda. The remaining Robolectric tests (`PrivacyConsentStoreTest`, `MainActivityPrivacyGateTest`) are appropriate — they test Android-specific SharedPreferences and Activity lifecycle behavior that genuinely needs the Android framework.

---

## Product Plan Gaps

### P1. Add in-session mode switching with step indicator — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - **Model layer:**
    - `PracticeSession.modesUsed: Set<PracticeMode>` added with default `setOf(mode)` for backward compatibility.
    - `PracticeUiState.activeSession` and `activeSessionModesUsed` cached body vals.
    - `PracticeTask.availablePracticeModes()` — returns ordered list respecting `SourceType.NONE` constraint.
    - `PracticeSession.resolvedModesUsed()` / `orderedModesUsed()` — safe accessors that handle legacy sessions with empty `modesUsed`.
    - Snapshot version bumped 7→8. `StoreSerialization` decode normalizes `modesUsed` via `resolvedModesUsed()` on read, so legacy v7 sessions get `setOf(mode)` populated automatically.
  - **Session manager:**
    - `switchMode(mode)` — validates mode is available, updates `session.mode` and `session.modesUsed`, updates `uiState.mode`, logs "Switched to Play solo" event. Returns false for same-mode no-op. Guards: no active task, unavailable mode, missing session.
    - `startPractice` uses `availablePracticeModes()` and initializes `modesUsed = setOf(resolvedMode)`.
  - **ViewModel:**
    - `switchPracticeMode(mode)` delegates to `sessionManager.switchMode()`.
    - `setMode(mode)` now checks `activeTaskId` — if a session is active, it routes through `switchMode` instead of just updating UI state.
  - **Session page UI:**
    - `PracticeModeStepIndicator` composable: FilterChips with arrows ("Listen first → Play along → Play solo"), `SunYellow` selected, kid-friendly emoji + labels. Shows "Choose any step. Your timer and repetition count keep going."
    - `shouldShowPracticeModeStepIndicator()` returns false for single-mode assignments.
    - Wired via `onSwitchMode` callback from `MainActivity` → `viewModel::switchPracticeMode`.
  - **Summary page:** "Steps explored: Listen first → Play along → Play solo" line on each session card using `orderedModesUsed()`.
  - **Profile archive:** `modesUsed` included in exported JSON.
  - **Tests:**
    - `PracticeSessionManagerTest.switchModeTracksExploredStepsWithoutResettingPractice` — starts with Listen, switches to Play Along then Play Solo, verifies: timer/elapsed unchanged, reps preserved, `modesUsed` = all 3, `mode` updated, switch events logged, same-mode no-op returns false.
    - `PracticeViewModelTest.switchPracticeModeKeepsTimerAndProgressAndTracksEveryStepUsed` — end-to-end through ViewModel with clock tick, verifies identical contract through public API.
    - `StoreSerializationTest.versionSevenSnapshotMigratesModesUsedAndRoundTripsAsVersionEight` — v7 JSON without `modesUsed` decodes with `setOf(PLAY_YOURSELF)` populated, re-encodes as v8 with `modesUsed` field.
    - `ProfileArchiveWriterTest` updated with multi-mode `modesUsed` and verifies JSON output.
  - 92 tests pass.
- **Review notes:** The design correctly honors the autonomy principle — steps are suggestive, not prescriptive. Timer and repetitions are completely unaffected by mode switches. The `resolvedModesUsed()` accessor is a good defensive pattern for forward/backward compatibility. The version bump ensures old builds that encounter v8 data with `modesUsed` will hit `ignoreUnknownKeys` and gracefully ignore the new field.

---

### P2. Add absence-aware welcome copy — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - `Models.kt`: Cached `daysSinceLastPractice: Int` in `PracticeUiState` body, backed by `calculateDaysSinceLastPractice()` which checks both `sessions` (any session on a date) and `progressByDate` (via `DailyProgress.hasPracticeActivity()` — elapsed > 0 or completions or reps > 0). Ignores future dates, invalid date strings, and empty progress entries.
  - `TodayPage.kt`: `todayWelcomeGreeting(studentName, daysSinceLastPractice)` extracted as `internal fun` for direct unit testing. `TodaySkyHero` parameter changed from computing `firstName` inline to receiving `daysSinceLastPractice` and delegating to the greeting function.
  - Copy thresholds match the plan exactly:
    - 0 days: "Hi, [firstName]!"
    - 1 day: "Welcome back, [firstName]!"
    - 2–6 days: "Welcome back! Let's pick up where you left off."
    - 7+ days: "It's great to see you again! Ready for some music?"
  - Blank/whitespace-only names fall back to "Musician".
  - `ModelsTest.kt`: `daysSinceLastPracticeUsesLatestRealActivityWithoutFutureDates` — tests empty snapshot (0), today session (0), yesterday (1), progress-only 2 days ago (2), 6-day gap (6), 7-day gap with future session ignored and empty progress entry ignored (7).
  - `TodayPageTest.kt` (new, 25 lines): `welcomeCopyUsesSupportiveAbsenceThresholds` — tests all 4 tiers plus blank name fallback. Uses "Maya Chen" as the test name (not "Lincoln").
  - 75 tests pass.
- **Review notes:** No guilt, shame, or streak language anywhere. The `hasPracticeActivity()` helper correctly distinguishes real practice (elapsed time, completions, reps) from empty progress entries that might exist from a date rollover. Future dates are filtered with `filterNot { it.isAfter(today) }` to handle timezone edge cases where a session might be dated tomorrow.

---

### P3. Show weekly practice dots on the student Today page — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - `WeeklyDotsRow` composable in `TodayPage.kt` — Card with "Your practice week" heading, 7 circles (M T W T F S S), `SunYellow` for practiced / `CloudBlue` otherwise, 24dp dots in a `SpaceBetween` Row.
  - Placed between the hero card and the mode selector card at line 83-85.
  - Reuses `state.weeklySummary(state.currentDate)` — no new data computation.
  - `WEEKLY_PATTERN_COPY` constant: "A gentle pattern, not a streak—rest days are okay." — matches the exact copy from the adult Summary page.
  - `WeeklyDotItem` data class and `weeklyDotItems()` mapping function extracted as `internal` for testing.
  - `compactDayLabel()` maps `DayOfWeek` to single-character labels with `"?"` fallback for invalid dates.
  - `TodayPageTest.kt`: `weeklyDotsKeepMondayToSundayLabelsAndPracticePattern` — verifies M-S label order, practiced/not-practiced pattern mapping, and the constant copy string.
  - `TodayPage.kt` grew from 375 to 460 lines (still under 500).
  - 76 tests pass.
- **Review notes:** Clean separation — the composable only receives `WeeklyPracticeSummary`, and the mapping logic (`weeklyDotItems`) is pure and testable. The dots are intentionally minimal for the child UI: no day-of-month numbers, no time durations, just color. The "gentle pattern" copy directly implements the plan's anti-streak philosophy. The Tuesday/Thursday "T"/"T" ambiguity is acceptable for a 7-day visual pattern — the position (2nd vs 4th) disambiguates.

---

### P4. Add completion vs. mastery distinction — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - **Model layer:**
    - `MasteryStatus` enum: `NOT_ASSESSED` ("Not assessed"), `STUDENT_CONFIDENT` ("Student feels confident"), `TEACHER_APPROVED` ("Teacher approved").
    - `PracticeSession.masteryStatus: MasteryStatus = MasteryStatus.NOT_ASSESSED` with `@Serializable` support.
    - `PracticeSessionStatus.completionLabel()` added alongside existing `summaryLabel()` — explicitly says "Practice goal reached" / "Practice goal not reached" to reinforce that completion != mastery.
    - DailyProgress comment added: "Completion means the assigned practice goal was done; it does not imply mastery."
    - Schema version bumped 8→9. Legacy sessions decode with `NOT_ASSESSED` default via `coerceInputValues`.
  - **Reflection dialog redesigned:**
    - Reflections are now `FilterChip` selections instead of immediate-submit `OutlinedButton`s — student can select then confirm.
    - Added "I feel confident playing this" chip (⭐) that sets `masteryStatus = STUDENT_CONFIDENT` independently of the reflection choice.
    - Explicit copy: "Finishing the practice goal and mastering the music are different. Confidence can grow with more practice."
    - "Save reflection" confirm button (enabled when reflection or confidence is selected). "Skip" dismisses without saving.
    - `submitReflection(reflection, studentConfident)` signature updated.
  - **Mastery preservation rule:** If `masteryStatus` is already `TEACHER_APPROVED`, student reflection cannot downgrade it. Only a teacher (Phase D) can set or revoke `TEACHER_APPROVED`.
  - **Summary page:**
    - New `SessionCompletionAndMastery` composable shows completion and mastery as separate lines per session.
    - Checkmark icon for goal reached, star icon (⭐) for `TEACHER_APPROVED`.
    - Mastery text color varies: `Muted` for NOT_ASSESSED, `DeepSky` for STUDENT_CONFIDENT, `Forest` for TEACHER_APPROVED.
    - Both lines use `mergeForAccessibility()` for screen readers.
  - **Event logging:** "Confidence: Student feels confident" logged as a separate event when the student checks the confidence chip.
  - **Tests:**
    - `ModelsTest.completionAndMasteryLabelsRemainIndependent` — verifies labels are distinct.
    - `PracticeSessionManagerTest`: existing test extended to verify `NOT_ASSESSED` default after completing reps, then `STUDENT_CONFIDENT` after reflection with `studentConfident = true`. New test: `TEACHER_APPROVED` is not downgraded when student submits reflection without confidence.
    - `PracticeViewModelTest`: existing reflection test updated with `studentConfident = true`, verifies `STUDENT_CONFIDENT` on session.
    - `StoreSerializationTest`: v7 JSON migrates with `masteryStatus = NOT_ASSESSED`, re-encodes as v9 with `masteryStatus` field.
    - `ProfileArchiveWriterTest`: `masteryStatus` included in archive JSON output.
  - 94 tests pass.
- **Review notes:** The three-tier mastery model cleanly separates concerns: completion is automatic (goal met), student confidence is self-reported during reflection, and teacher approval is deferred to Phase D but the data model is ready. The preservation rule (TEACHER_APPROVED survives student reflection) prevents an accidental downgrade. The reflection dialog redesign from immediate-submit buttons to select-then-confirm is a UX improvement — it lets the student review their choices before committing.

---

### P5. Add assignment version history — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - **`AssignmentHistory`** data class: `assignmentId`, `childProfileId`, `revision`, `changedAtMillis`, `changedFields: Set<String>`, `previousAssignment: Assignment` (full immutable snapshot of the previous revision).
  - **`PracticeSnapshot.assignmentHistory: List<AssignmentHistory>`** — stored alongside tasks, progress, events, and sessions.
  - **`PracticeUiState.assignmentHistoryFor(assignmentId)`** — returns history entries sorted descending by revision, cached via `assignmentHistoryById` map.
  - **`preserveAssignmentVersionHistory(previous, candidate, changedAtMillis)`** — pure function that compares two snapshots, detects changed assignments, computes `changedFields` via field-by-field diff (`assignmentChangedFields`), creates `AssignmentHistory` entries with the previous revision as an immutable snapshot, and deduplicates by `(assignmentId, revision)` key.
  - **Room storage:** `AssignmentHistoryEntity` table with composite primary key `(assignmentId, revision)`, foreign key to `child_profiles` with CASCADE delete, indexed on `childProfileId` and `assignmentId`. Room database version bumped 1→2. `PRACTICE_DATABASE_MIGRATION_1_2` adds the `assignment_history` table and `teacherId` column to `assignments` via `ALTER TABLE`.
  - **`PracticePersistenceChangeSet`** extended with `assignmentHistoryToUpsert` and `assignmentHistoryToDelete`.
  - **Summary UI:** `SummaryComponents.kt` — `AssignmentVersionHistory` composable with expandable "Version history (N)" button. Each entry shows: version number, replacement timestamp, changed field names, full previous assignment details (title, date, goal, modes, reference, coach focus, sheet music, archive status, creator).
  - **Tests:**
    - `RoomPracticeStoreTest.roomVersionOneDatabaseMigratesToAssignmentHistorySchema` — creates a v1 database with real schema from exported JSON, runs `MIGRATION_1_2`, verifies the `assignment_history` table exists and `teacherId` column was added.
    - `RoomPracticeStoreTest` granular changes test: verifies `assignmentHistoryToUpsert` has 1 entry after a title edit, stored history matches the previous assignment, and `changedFields` is `setOf("title")`.
  - 113 tests pass.

---

### P6. Plan teacher data model extension points — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - **`TeacherProfile`** — `@Serializable data class(id, name, email)` with doc comment: "A teacher profile does not grant access by itself; runtime authorization must be based on an active parent-granted StudentTeacherLink."
  - **`StudentTeacherLink`** — `@Serializable data class(teacherId, childProfileId, grantedByParentId, grantedAtMillis, revokedAtMillis?)` with doc comment: "Only the granting parent may create or revoke this link, and revocation must stop all subsequent teacher reads and writes for the child."
  - **`TeacherFeedback`** — `@Serializable data class(sessionId, teacherId, comment, atMillis)` with doc comment: "Future writes must verify that teacherId has an active, parent-granted link to the child who owns sessionId."
  - **`Assignment.teacherId: String? = null`** — null means parent-created. Comment: "Future teacher writes require an active StudentTeacherLink."
  - **Room migration:** `ALTER TABLE assignments ADD COLUMN teacherId TEXT` in `MIGRATION_1_2`. Existing assignments get `NULL` (parent-created).
  - **Summary UI:** History entries show "Created by parent" or "Created by teacher" based on `teacherId`.
  - **No runtime teacher behavior:** No teacher login, no teacher views, no link management. These are Phase D data model extension points only.
  - Authorization rules are documented as KDoc comments directly on the data classes, matching section 3 of the product plan (parent-controlled access, revocation stops all access, teacher cannot browse unshared students).
- **Review notes:** The authorization comments use imperative language ("must require", "must verify", "must stop") which sets a clear contract for the Phase D implementer. The `teacherId` on `Assignment` is nullable rather than a separate join table, which is simpler and matches the current single-device architecture. The `StudentTeacherLink.revokedAtMillis` soft-delete pattern preserves an audit trail of past access grants.

---

### P7. Add privacy and consent infrastructure — DONE
- **Status:** Completed 2026-11-02. Verified by code review. Real-device wipe and TalkBack testing pending.
- **What was delivered:**
  - **Consent gate** — `MainActivity` checks `PrivacyConsentStore.hasAcceptedCurrentPolicy()` before the ViewModel is even instantiated. On first launch (or after a data wipe), only the consent screen renders; no `FamilyStore.loadHousehold()` or profile/snapshot creation occurs. `onStart()` and `onStop()` also check consent/initialization state to avoid touching an uninitialized ViewModel.
  - **`PrivacyConsentScreen`** (334 lines) — age range selection (Under 13 / 13–17 / 18+, no birth date), parent/guardian confirmation checkbox, privacy policy acceptance checkbox, inline data-handling summary bullets, "Read the full privacy policy" button. All three must be checked before "Accept and continue" is enabled. `canAcceptPrivacyConsent()` is a pure function testable without UI.
  - **`PrivacyConsent.kt`** (129 lines) — `PrivacyConsentStore` (separate SharedPreferences `lk_practice_privacy_consent`), `ChildAgeRange` enum, `PRIVACY_POLICY_SECTIONS` (8 sections covering who should use the app, information kept, storage, permissions/network, sharing, content rights, deletion, and policy changes), versioned acceptance with `CURRENT_PRIVACY_POLICY_VERSION = 1`. Bumping the version triggers re-consent.
  - **`PrivacyPolicyDialog`** — full scrollable policy rendered from `PRIVACY_POLICY_SECTIONS`. Reusable from both the consent screen and Parent Space.
  - **`PrivacySettingsDialog`** (224 lines) — accessible from Parent Space > Grown-Up Tools > "Privacy & data". Shows accepted age range, policy version, local-data summary, YouTube disclosure, and the "Delete all data" danger zone.
  - **`DeleteAllDataConfirmationDialog`** — requires typing `DELETE` (case-sensitive, capped at 12 chars). Calls `AppDataDeletionManager.deleteAllData()`.
  - **`AppDataDeletionManager`** (21 lines) — wraps `ActivityManager.clearApplicationUserData()` with an injectable lambda for testing. The platform wipe atomically removes all SharedPreferences, databases, caches, app-managed files, and the consent record, then terminates the process. Next launch shows the consent screen again.
  - **YouTube hardening** — `youtube-nocookie.com` replaces `youtube.com` in embed URLs and `loadDataWithBaseURL`. `CookieManager.setAcceptCookie(false)` and `setAcceptThirdPartyCookies(false)` added to the WebView factory.
  - **Backup disabled** — `allowBackup="false"` in manifest, `data_extraction_rules.xml` excludes all domains from cloud-backup and device-transfer, `backup_rules.xml` excludes all domains from full-backup.
  - **`PRIVACY_DATA_AUDIT.md`** (70 lines) — documents the launch/consent gate, data map (what's stored where and when), manifest/dependency audit, deletion behavior, external-file boundaries, and fictional-screenshot rules for store assets.
  - **Tests:**
    - `PrivacyConsentStoreTest` (5 tests): first-launch creates no data, acceptance requires all three confirmations, accepted policy restores age range, older version triggers re-consent, policy text covers required disclosures and has no "Lincoln".
    - `AppDataDeletionManagerTest` (2 tests): successful wipe, failed wipe reports correctly.
    - `MainActivityPrivacyGateTest` (2 tests): fresh launch doesn't touch practice preferences before consent, accepted policy allows ViewModel initialization.
  - No "Lincoln" in any privacy-related file.
  - 89 tests pass.
- **Review notes:** The consent gate design is sound — the ViewModel isn't even instantiated until consent is accepted, so there's zero risk of a race between consent check and data creation. Using `ActivityManager.clearApplicationUserData()` for deletion is the right call: it covers current and future storage atomically and avoids the risk of missing a new SharedPreferences file or Room database added later. The privacy policy is thorough — it explicitly discloses the YouTube network boundary, external-file deletion limits, and the absence of analytics/ads/cloud. Remaining before public beta: real-device wipe test on a disposable emulator, hosted privacy-policy URL for Play Console, and Play Console Data Safety form completion.

---

## Code-Level Improvements

### C1. Cache computed properties in PracticeUiState — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - Converted 11 `get()` computed properties to body-declared `val`s in `PracticeUiState`: `todayTasks`, `todayProgress`, `todayEvents`, `todaySessions`, `summaryTasks`, `summaryProgress`, `summaryEvents`, `summarySessions`, `historyDates`, `activeTask`, `pendingReflectionSession`.
  - These now compute once when the state is created or `copy()`'d, not on every Compose recomposition.
  - `tasksForDate()`, `archivedTasksForDate()`, `secondsForTask()`, `goalMet()`, `progressLabel()`, `weeklySummary()` remain as functions since they take parameters.
  - New test `dateSpecificCollectionsAreCachedUntilStateIsCopied` uses `assertSame`/`assertNotSame` to verify object identity stability and recomputation on `copy()`.
- **Review notes:** Correct use of Kotlin data class semantics — body `val`s are outside `equals`/`hashCode`/`copy` parameters but are re-initialized when `copy()` calls the primary constructor. The test proves both caching (same identity) and freshness after copy (different identity, correct content). `historyDates` — the most expensive computation (4 collections + distinct + sort) — now runs at most once per state transition.

---

### C2. Split PracticeScreens.kt into per-page files — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - `TodayPage.kt` (360 lines) — `TodayPage`, `TodaySkyHero`, `kidLabel()`, `kidEmoji()`.
  - `AssignmentPage.kt` (379 lines) — `AssignmentPage`, `AssignmentForm`, `AssignmentRow`, `ArchivedAssignmentRow`, `AssignmentSaveBar`, `AssignmentRemovalDialog`.
  - `AssignmentEditorCard.kt` (302 lines) — the form card extracted from `AssignmentPage` to keep both files under 400 lines.
  - `PracticeSessionPage.kt` (330 lines) — `PracticeSessionPage`, `PracticeReflectionDialog`.
  - `SummaryPage.kt` (396 lines) — `SummaryPage`.
  - `SharedComponents.kt` (57 lines) — `EmptyCard`, `formatDuration`, `summaryLabel()`, `timeLabel()`, `friendlyFullDate()`.
  - `PracticeScreens.kt` deleted (1,597 lines removed).
  - All 6 files under 400 lines. No file exceeds the 500-line acceptance bar.
- **Review notes:** The split went one step further than planned by extracting `AssignmentEditorCard` — a good call since the editor form alone was ~300 lines. `kidLabel()` and `kidEmoji()` are `internal` in `TodayPage.kt` and reused by `AssignmentPage.kt` and `PracticeSessionPage.kt` via same-package visibility. No stale imports to `PracticeScreens` remain.

---

### C3. Add ViewModel unit tests — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - `PracticeViewModelTest.kt` (901 lines) — 14 Robolectric tests (`@Config(sdk = [28])`) covering all 8 requested workflows plus 6 additional flows.
  - **Infrastructure for testability:**
    - `PracticeDataStores.kt` (39 lines) — `PracticeDataStore` and `FamilyDataStore` interfaces extracted from concrete `PracticeStore` and `FamilyStore`.
    - `PracticeViewModel` constructor now accepts: `PracticeDataStore`, `FamilyDataStore`, `WorkbookStorage` (already existed), `RecordingManager`, `dateProvider: () -> String`, `coroutineScope: CoroutineScope?`, `timerTicker: PracticeTimerTicker`. All have production defaults.
    - `PracticeTimerTicker.Default` companion added for clean default parameter.
    - `PracticeViewModelFactory` passes production implementations.
  - **Test fakes:** `ViewModelPracticeStoreFake` (in-memory snapshot map with `deletedProfileIds` tracking), `ViewModelFamilyStoreFake` (full household management with PIN, revisions), `ViewModelWorkbookStoreFake` (configurable read/write counts), `ViewModelClock` (controllable date, wall clock, elapsed realtime), `ViewModelTimerTicker` (manual tick dispatch). All defined as `private` within the test file.
  - **Coverage of the 8 requested flows:**
    1. Start practice > record reps > finish > session COMPLETED with correct repetition count
    2. Switch profiles > tasks isolated, snapshots separate in store
    3. Timer tick > date rollover > session INTERRUPTED, timer reset, old progress preserved
    4. Save assignment > edit > revision bump from 1→2, local revision increments
    5. Archive > hidden from todayTasks, progress/sessions preserved, revision bumped
    6. Delete profile > recording file deleted, store entry removed, household updated
    7. Adult gate > page blocked without PIN, create PIN, lock/unlock, wrong PIN rejected
    8. Excel merge > duplicate detection reported in message, valid import accepted
  - **Additional coverage:** Profile CRUD (add/save/export/delete), summary navigation, CSV import/export, recording lifecycle (create/save/delete/undeletable/outside-managed), reflection submission, external action locking, initialization interrupted session recovery, concurrent guards (recording active blocks switch/finish).
  - 93.8% line coverage on PracticeViewModel (380/405 lines).
  - 73 tests total (59 prior + 14 new), 0 failures.
- **Review notes:** Well-structured fixture pattern — the `fixture()` builder creates a complete test environment with controllable clock, ticker, and stores. Each test is self-contained and tests end-to-end through the ViewModel's public API, not internal state. The `FamilyDataStore` interface extends `WorkbookSyncSettings` (via `override fun saveChildProfile`), which cleanly consolidates the two separate interfaces the WorkbookSyncManager was already using. The Robolectric dependency is minimal — only needed for `RuntimeEnvironment.getApplication()` to satisfy the `appContext` parameter.

---

### C4. Improve accessibility — DONE
- **Status:** Completed 2026-11-01. Verified by code review. TalkBack device testing pending.
- **What was delivered:**
  - **Shared helpers** — `Accessibility.kt` (55 lines): `hideFromAccessibility()`, `mergeForAccessibility()`, `accessibilityHeading()`, `politeLiveRegion()`, `politeMergedStatus()`, `assertiveLiveRegion()` Modifier extensions; `taskCardAccessibilityDescription()` and `weeklyPracticeAccessibilityDescription()` pure functions; `shouldSkipRecordingCountdown()`.
  - **Task cards** — `TodayPage`, `SummaryPage`, `AssignmentPage`: `mergeForAccessibility()` on Card, `clearAndSetSemantics` on the title/progress Row with `taskCardAccessibilityDescription` (reads "Boreas. 2 of 4 repetitions. Not complete yet."). The "/" in progress is spoken as "of".
  - **Weekly dots** — `clearAndSetSemantics` on the entire Card: "4 of 7 practice days this week. A gentle pattern, not a streak—rest days are okay." instead of TalkBack reading 7 individual circles. Visual checkmarks added to practiced-day dots for non-color-only signaling.
  - **Headings** — `accessibilityHeading()` on section titles: "Today's music missions", hero greeting, "Assignment progress", etc.
  - **Live regions** — Recording countdown: `LiveRegionMode.Assertive` ("Recording starts in 3"). Recording status message: `politeLiveRegion()`. Snackbar already handled by Material3.
  - **Decorative elements hidden** — Cloud/sun emoji in hero (`hideFromAccessibility()`), emoji leading icons in mode chips, checkmark text in practiced dots.
  - **Camera label** — `clearAndSetSemantics { contentDescription = "Camera preview" }` on the CameraX `AndroidView`.
  - **Reduced motion** — `shouldSkipRecordingCountdown()` checks `MotionDurationScale.scaleFactor <= 0f` (system "Remove animations" setting) and skips the 3-2-1 visual countdown, going straight to recording.
  - **WCAG contrast** — 6 color values darkened in `Theme.kt`:
    - `Muted`: #5D7890 → #4F687E (5.78:1 on CloudWhite, was 4.58:1)
    - `Forest`/`DeepSky`: #287BC1 → #1E67A2
    - `SkyBlue`: #72C9F4 → #3679AB
    - `Danger`: #C94F5C → #C04654
    - `SkyOutline`: #A8DDF5 → #5D91AB
    - Text on DeepSky/SkyBlue backgrounds changed from `Paper.copy(alpha = 0.82f)` to full `Paper` to maintain contrast.
  - **Contrast regression test** — `AccessibilityTest.normalTextPalettePairsMeetWcagAaContrast()` asserts ≥4.5:1 for 9 text color pairs and ≥3.0:1 for 2 non-text (outline) pairs using the actual `Color` values from `Theme.kt`. This prevents future color changes from breaking WCAG compliance.
  - **Icon audit** — Remaining `contentDescription = null` icons are all either: decorative (inside labeled buttons like "Start"), inside `mergeForAccessibility()` cards, or next to visible text labels. No icon-only interactive controls are unlabeled.
  - `AccessibilityTest.kt` (100 lines): 4 tests — task card description, weekly summary description, reduced-motion countdown skip, and WCAG contrast regression.
  - 14 files changed, 80 tests pass.
- **Review notes:** The contrast regression test is a standout — it catches color regressions at build time rather than requiring manual inspection. The `shouldSkipRecordingCountdown` uses `MotionDurationScale` from the Compose coroutine context, which reflects the system "Remove animations" or "Animation duration scale" setting without needing a separate AccessibilityManager query. Real-device TalkBack testing should be done before public beta release to catch any focus-order or announcement-timing issues that unit tests can't detect.

---

### C5. Raise or manage the 500 event/session cap — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - `PracticeHistoryLimits.kt` (29 lines) — single source of truth: `MAX_PRACTICE_EVENTS = 2_000`, `MAX_PRACTICE_SESSIONS = 2_000`, `PRACTICE_EVENT_WARNING_COUNT` (80%), `PRACTICE_SESSION_WARNING_COUNT` (80%), `practiceHistoryCapacityWarning()` pure function.
  - All `takeLast` calls across 5 files updated to use the centralized constants: `PracticeSessionManager` (session creation, event logging), `StoreSerialization` (encode, decode), `PracticeStore` (legacy migration), `PracticeWorkbookTemplate` (Excel export).
  - Old `MAX_EVENTS`, `MAX_SESSIONS`, `MAX_STORED_RECORDS` constants removed — zero hardcoded `500` values remain.
  - `PracticeUiState.historyCapacityWarning: String?` — cached body val, computed from snapshot event/session counts.
  - Warning surfaced in `GrownUpToolsDialog` at the top of the Parent Space menu, via `HistoryCapacityWarning` composable. Two tiers: "nearing its current limit" at 80%, "at its current limit" at 100%. Both include counts and "Export a profile archive soon. LK Practice keeps the newest records."
  - **Tests:**
    - `PracticeHistoryLimitsTest` (2 tests): warning null below 80%, non-null at exactly 80% for either events or sessions independently, "at its current limit" copy at 100%.
    - `StoreSerializationTest.twelveMonthsOfDailyPracticeRoundTripsWithoutHistoryLoss` — 1,825 events (365×5/day) and 1,095 sessions (365×3/day) encode/decode with zero data loss, plus capacity warning present at that volume.
  - 98 tests pass.
- **Review notes:** The 4x increase (500→2,000) gives 12+ months of headroom for daily practice. The 80% parent warning is the right balance — it informs without alarming, and recommends the profile archive export that already exists. The `takeLast` approach (keep newest, drop oldest) is correct for this use case since recent practice is more actionable than old history. Full Room migration (A2) would eliminate the cap entirely but is a larger change.

---

### C6. Strengthen date handling — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **What was delivered:**
  - `DateKey.kt` (43 lines) — `@Serializable @JvmInline value class DateKey(val value: String) : Comparable<DateKey>`. Validates via `LocalDate.parse` in `init`. Cached `toLocalDate()` via `ConcurrentHashMap`. Companions: `from(LocalDate)`, `parse(String)`, `today()`.
  - Serializes as plain `"YYYY-MM-DD"` string — fully wire-compatible with existing snapshots, CSV, XLSX, and profile archives. Verified by `DateKeyTest.serializesAsTheExistingPlainIsoString`.
  - **Converted across 28 source files and 11 test files:**
    - Model fields: `Assignment.assignedDate`, `DailyProgress.date`, `PracticeEvent.date`, `PracticeSession.date`, `WeeklyPracticeDay.date` — all `DateKey`.
    - Map keys: `PracticeSnapshot.progressByDate: Map<DateKey, DailyProgress>`.
    - UI state: `PracticeUiState.currentDate`, `summaryDate` — both `DateKey`.
    - Store interfaces: `PracticeDataStore.load(profile, currentDate: DateKey)`.
    - ViewModel: `dateProvider: () -> DateKey`.
    - CSV codec, XLSX reader, workbook template, recording names, profile archive — all use `DateKey`.
    - `currentDateKey()` returns `DateKey`. `dateKeyForMillis()` returns `DateKey`.
    - `friendlyFullDate()`, `friendlyDate()` accept `DateKey`, use cached `toLocalDate()`.
    - `AssignmentForm.assignedDate` is `DateKey`.
  - **Compile-time safety:** Passing a raw `String` where `DateKey` is expected is now a type error. The `init` block validates on construction — no invalid date strings can exist as `DateKey` values.
  - **Cached parsing:** `ConcurrentHashMap<String, LocalDate>` shared across all `DateKey` instances. `assertSame(date.toLocalDate(), date.toLocalDate())` proves identity-level caching.
  - **Tests:**
    - `DateKeyTest` (3 tests): parse/format/equality, serialization as plain ISO string, rejection of arbitrary strings via `DateTimeParseException`.
    - All existing tests (101 total) updated to construct `DateKey(...)` instead of raw strings for date fields.
  - 101 tests pass.
- **Review notes:** Option B (value class) was the right choice — it catches date-string misuse at compile time while adding zero runtime overhead (inline classes are erased). The `Comparable<DateKey>` implementation delegates to string comparison, which is correct for ISO-8601 dates. The `ConcurrentHashMap` cache eliminates the repeated `LocalDate.parse` calls that were happening per-task per-frame in the Summary page. Wire compatibility is confirmed by the existing serialization round-trip tests passing unchanged against literal v7/v9 JSON.

---

### C7. Remove hardcoded "Lincoln" default name — DONE
- **Status:** Completed 2026-11-01. Verified by code review.
- **What was delivered:**
  - `FamilyStore.kt` line 24: `defaultLegacyName` changed from conditional `"Lincoln"` / `"Student"` to unconditional `"Student"`.
  - `PracticeStore.kt` line 188: `defaultName` changed from conditional to unconditional `"Student"`. The `hasLegacyProfile` check was also removed since the branch no longer matters.
  - Zero "Lincoln" references remain in any production source file.
  - Test files retain "Lincoln" as fixture data — correct, since tests exercise the recording filename format and profile archiving with a realistic name.
- **Review notes:** Clean, minimal change. Existing users who already have a saved profile name are unaffected — the default only applies when no `student_profile_name` key exists in SharedPreferences.

---

## Suggested work order

For a worker picking these up one at a time:

1. **C7** — Hardcoded name removal (5 min, low risk, quick win)
2. **A4** — Remove duplicate code (30 min, reduces confusion before bigger changes)
3. **C2** — Split PracticeScreens.kt (30 min, no logic changes, improves navigation)
4. **C1** — Cache computed properties (30 min, performance improvement)
5. **A3** — kotlinx.serialization (1-2 hours, eliminates fragile JSON code)
6. **A1** — Extract ViewModel managers (2-3 hours, major architecture improvement)
7. **C3** — ViewModel unit tests (2-3 hours, enabled by A1 and A5)
8. **A2** — Migrate to Room (3-4 hours, largest change, do after A3)
9. **P2** — Absence-aware welcome (30 min, user-visible improvement)
10. **P3** — Weekly dots on Today page (30 min, user-visible improvement)
11. **C4** — Accessibility (1-2 hours, required before public beta)
12. **P7** — Privacy/consent (2-3 hours, required before public beta)
13. **P1** — Guided mode progression (2-3 hours, Phase C feature)
14. **P4** — Completion vs mastery (1 hour, Phase C data model)
15. **C5** — Event/session cap (1 hour, scales with Room)
16. **C6** — Date type safety (1 hour, cleanup)
17. **A5** — Dependency injection (1 hour, improves testability)
18. **P5** — Assignment version history (1-2 hours, Phase D prep)
19. **P6** — Teacher data model (1 hour, Phase D prep)

---

## Phase E — Practice Review Video Features

### E1. Synchronized side-by-side playback in Summary
- **Priority:** High (ship first — works with YouTube and local references)
- **Files:** `SummaryPage.kt`, `MediaComponents.kt` (new composable), `Models.kt`
- **What it does:** When a parent taps "Watch" on a recorded practice take in Summary, show the student's recording and the assignment's reference video side-by-side with synchronized playback. The parent sees exactly what the student heard alongside how the student played.
- **Design:**
  - Add a `PracticeReviewPlayer` composable that takes a practice recording URI and a `PracticeTask` (for the reference source, start/end times).
  - Layout: landscape-friendly Row with two equal video panels. Portrait: stack vertically with the reference on top (smaller, 40%) and the student recording below (larger, 60%).
  - "Play both" button starts both videos simultaneously. Individual pause/seek controls on each panel.
  - Reference video uses the existing `LocalVideoPlayer` (for local sources) or `YouTubePlayer` (for YouTube). Student recording uses `LocalVideoPlayer` with the recording's `content://` URI.
  - YouTube references play in the WebView iframe as today — no frame extraction, no ToS violation. Synchronization is best-effort (tap to start both; YouTube iframe doesn't expose precise seek API).
  - Local references can synchronize precisely via `VideoView.seekTo()` on both panels.
  - If the assignment has no reference (`SourceType.NONE`), show only the student recording at full width.
- **What to do:**
  - [ ] Create `PracticeReviewPlayer` composable in a new `ReviewPlayer.kt` file.
  - [ ] Add a "Review with reference" button on practice-log recording entries in `SummaryPage.kt`, next to the existing "Watch" button. Show it only when the recording's linked assignment has a reference video.
  - [ ] Look up the assignment from the recording event's `taskId` to get the reference source, start/end seconds.
  - [ ] Handle the case where the assignment was deleted or archived — fall back to "Watch" (recording only).
  - [ ] Handle the case where a local reference video is no longer accessible — show a message in the reference panel and play the student recording alone.
  - [ ] The "Watch" button continues to open the recording in an external player as today (unchanged behavior).
  - [ ] Add accessibility: merged content description "Practice review: [assignment title], student recording and teacher reference side by side".
- **Acceptance:** A parent can tap "Review with reference" and see the student's take playing alongside the teacher's reference. Works for YouTube (best-effort sync) and local video (precise sync). Falls back gracefully when the reference is missing. No video frames are extracted from YouTube.

---

### E2. Composite side-by-side video export (local references only)
- **Priority:** Medium (ship after E1 — adds export capability for local-reference assignments)
- **Files:** New `VideoCompositor.kt`, `PracticeSessionManager.kt`, `SummaryPage.kt`
- **Constraint:** This feature is available ONLY for assignments with `SourceType.LOCAL`. YouTube assignments cannot be composited due to YouTube Terms of Service prohibiting frame capture and redistribution. The UI must not offer this option for YouTube-referenced assignments.
- **What it does:** After a practice session, a parent can generate a single MP4 file with the student's recording on the left and the teacher's reference clip on the right, synchronized and side-by-side. The resulting video can be shared, saved, or sent to a teacher.
- **Design:**
  - Use Android `MediaCodec` for decode and encode, `MediaMuxer` for output.
  - Decode both input videos frame-by-frame. Render each decoded frame onto one half of a shared `Surface` (left = student, right = reference). Encode the composited surface into a new MP4.
  - Audio: mix both audio tracks (student at 70% volume, reference at 30%), or offer a toggle for "student audio only" / "both" / "reference only". Use `AudioMixer` or manual PCM mixing.
  - Output resolution: 1280x720 (720p) with each panel at 640x720. Scale input frames to fit while preserving aspect ratio (letterbox with black bars if needed).
  - Reference clip is trimmed to the assignment's `startSeconds`–`endSeconds` range. If the student's recording is longer than the reference clip, loop the reference or freeze on the last frame.
  - Processing runs on `Dispatchers.IO` in a coroutine. Show a progress dialog: "Creating practice review video... X%" with a cancel button.
  - Output file is saved to the same managed recording directory with a naming pattern: `YYYY-MM-DD - StudentName - AssignmentTitle - Review.mp4`.
  - After creation, offer "Watch", "Share", and "Delete" — same as existing recordings.
- **What to do:**
  - [ ] Create `VideoCompositor` class with: `suspend fun compose(studentVideoPath: String, referenceVideoPath: String, referenceStartSeconds: Int, referenceEndSeconds: Int?, outputFile: File, onProgress: (Float) -> Unit): File`.
  - [ ] Handle format differences: different frame rates (normalize to 30fps), different resolutions (scale to fit 640x720 panels), different codecs (MediaCodec handles common formats).
  - [ ] Add audio mixing with configurable balance.
  - [ ] Add "Create review video" button in `SummaryPage.kt` on recording entries where `task.sourceType == SourceType.LOCAL` and the local reference is still accessible.
  - [ ] Do NOT show "Create review video" for `SourceType.YOUTUBE` or `SourceType.NONE` assignments.
  - [ ] Add a progress dialog composable.
  - [ ] Save the composite as a `PracticeEvent` with `videoPath` pointing to the review file and `event = "Practice review video created"`.
  - [ ] Add unit tests for the compositor's frame-timing logic (mock MediaCodec is hard — focus on the sync/loop/trim logic as pure functions).
  - [ ] Add a `VideoCompositorTest` that verifies: reference trimming respects start/end, looping when student recording exceeds reference, progress callbacks fire, cancellation stops processing.
- **Acceptance:** A parent can generate a side-by-side review video for a local-reference assignment. The video plays correctly with synchronized audio and video. YouTube assignments never offer this option. Processing shows progress and supports cancellation. The output file appears in the practice log with watch/share/delete controls.

---

### Suggested work order for E1/E2

1. **E1 first** — 2-3 hours. Gives immediate value for all assignment types including YouTube. No codec complexity.
2. **E2 after E1 is validated** — 3-5 hours. Heavier implementation but builds on E1's layout decisions. Only valuable if families actually use local references (check pilot feedback from E1 usage before investing).

---

## Phase E — Assignment Scheduling and Student-Initiated Practice

### E3. Multi-day assignment date ranges — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **Files:** `Models.kt`, `PracticeDatabase.kt`, `RoomEntityMappers.kt`, `RoomPersistence.kt`, `AssignmentPage.kt`, `AssignmentEditorCard.kt`, `TodayPage.kt`, `SummaryPage.kt`, `WorkbookSyncManager.kt`, `CsvCodec.kt`, `XlsxWorkbookReader.kt`, `PracticeWorkbookTemplate.kt`, `PracticeSessionManager.kt`, `StoreSerialization.kt`
- **Plan reference:** Section 5 — assignments have a "schedule/due date" field. Real teachers assign "practice this all week," not "practice this only on Tuesday."
- **Current behavior:** `Assignment.assignedDate: DateKey` is a single day. An assignment only appears on Today for that exact date. To assign the same piece for a week, a parent must create 7 separate assignments or re-date them daily.
- **What to change:**

  **Data model:**
  - [ ] Replace `Assignment.assignedDate: DateKey` with `startDate: DateKey` and `endDate: DateKey`. Single-day assignments have `startDate == endDate`.
  - [ ] Add `Assignment.isActiveOn(date: DateKey): Boolean` = `date >= startDate && date <= endDate && archivedAtMillis == null`.
  - [ ] Preserve `assignedDate` as a read alias: `val assignedDate: DateKey get() = startDate` for backward-compatible display, sorting, and history.

  **Room migration (v2 → v3):**
  - [ ] `ALTER TABLE assignments ADD COLUMN startDate TEXT NOT NULL DEFAULT ''`
  - [ ] `ALTER TABLE assignments ADD COLUMN endDate TEXT NOT NULL DEFAULT ''`
  - [ ] `UPDATE assignments SET startDate = assignedDate, endDate = assignedDate WHERE startDate = ''`
  - [ ] Keep the `assignedDate` column for backward compatibility (or drop it and use `startDate` for the index). Rename the index to `childProfileId, startDate, endDate`.

  **Today filtering:**
  - [ ] Change `todayTasks` from `it.assignedDate == currentDate` to `it.isActiveOn(currentDate)`.
  - [ ] Change `tasksForDate(date)` and `archivedTasksForDate(date)` similarly.
  - [ ] `historyDates` should include the full range of each assignment, not just the start date. Use `(startDate.value..endDate.value)` date sequence, or add start/end to the date set.

  **Summary filtering:**
  - [ ] `summaryTasks` should show assignments active on the summary date.
  - [ ] Progress remains per-date — completing 3 reps on Monday doesn't auto-complete Tuesday. Each day within the range has its own `DailyProgress`.

  **Assignment editor:**
  - [ ] Replace the single "Choose date" button with two: "Start date" and "End date" (or a date range picker).
  - [ ] Add a "Use today only" quick button that sets `startDate == endDate == today`.
  - [ ] Add quick-range buttons: "This week" (Monday to Sunday), "Next 7 days" (today + 6).
  - [ ] Validate: `endDate >= startDate`, range not exceeding 30 days (prevents accidental 10-year assignments).

  **CSV / Excel / Archive:**
  - [ ] CSV: add `start_date` and `end_date` columns. For backward compatibility, keep `date` column as an alias for `start_date` when `end_date` is empty.
  - [ ] XLSX: same — add columns, handle missing `end_date` as single-day.
  - [ ] Profile archive: include both dates in JSON export.
  - [ ] Workbook template version bump.

  **Duplicate detection:**
  - [ ] `assignmentKey` should use `startDate` + `endDate` instead of single date.

  **Session/event logging:**
  - [ ] Sessions still record the specific `date` they occurred on (within the range). No change to `PracticeSession.date`.

  **Serialization:**
  - [ ] Snapshot version bump (9 → 10). Decode: if `startDate` is missing, fall back to `assignedDate` for both start and end.

  **History:**
  - [ ] `assignmentChangedFields` should detect `startDate` and `endDate` changes.

- **Acceptance:**
  - A parent creates an assignment for "Monday to Friday." It appears on Today for each of those 5 days.
  - Progress on Monday (2/3 reps) does not affect Tuesday's progress — each day starts fresh.
  - A single-day assignment (`startDate == endDate`) behaves identically to today's behavior.
  - The Summary page shows the assignment on every date in its range.
  - CSV import with only a `date` column (no `end_date`) creates a single-day assignment.
  - Room migration preserves all existing single-day assignments unchanged.

---

### E4. Student-initiated practice on empty days — DONE
- **Status:** Completed 2026-11-02. Verified by code review.
- **Files:** `TodayPage.kt`, `PracticeSessionManager.kt`, `PracticeViewModel.kt`, `Models.kt`, `SummaryPage.kt`
- **Plan reference:** Section 4 — "Autonomy: children should understand what to do next and be able to finish a mission themselves." Section 10 — north-star metric: "weekly meaningful practice sessions completed independently."
- **Current behavior:** When Today has no assignments, the student sees "No music missions yet. Ask a grown-up to add today's plan." They can't practice until a parent creates an assignment.
- **What to change:**

  **Data model:**
  - [ ] Add `PracticeSession.studentInitiated: Boolean = false`. True means the student chose to practice without an assignment for today.
  - [ ] Room migration: `ALTER TABLE practice_sessions ADD COLUMN studentInitiated INTEGER NOT NULL DEFAULT 0`.
  - [ ] Serialization: add field with default false; legacy sessions decode as false.

  **Today page — empty-day UI:**
  - [ ] When `todayTasks` is empty AND there are non-archived assignments from any previous date, replace the "No music missions yet" empty card with a "Practice on your own" section.
  - [ ] Show two options:
    1. **Recent assignments** — a compact list of the student's most recent non-archived assignments (max 5, sorted by most recently practiced or most recently assigned). Each shows the title, instrument, assignment type, and a "Practice this" button.
    2. **"Surprise me!"** button — picks a random non-archived assignment from the student's full assignment list. Uses a different assignment each tap (no immediate repeats).
  - [ ] If there are truly no assignments at all (new profile), keep the current "Ask a grown-up" message.

  **Starting a self-initiated session:**
  - [ ] When the student taps "Practice this" or "Surprise me!", call `startPractice(task)` as today but with a flag marking it student-initiated.
  - [ ] In `PracticeSessionManager.startPractice`:
    - Skip the `task.assignedDate != state.currentDate` check when `studentInitiated = true` (the assignment is from a different date by definition).
    - Set `session.studentInitiated = true`.
    - Log event: "Self-directed practice started" instead of the mode label.
  - [ ] The rest of the session (modes, timer, reps, reflection, mastery) works identically.
  - [ ] Progress is tracked under today's date as a new `DailyProgress` entry, not under the assignment's original date.

  **Summary display:**
  - [ ] In the session card, show "Self-directed practice" badge when `studentInitiated == true`, so parents/teachers can distinguish assigned vs self-initiated sessions.
  - [ ] Color: use `LavenderCloud` background instead of `Paper` for self-directed session cards.

  **Today page — assignments exist but all complete:**
  - [ ] When all today's assignments are complete (allComplete == true), show a "Keep practicing" section below the completed missions with the same recent-assignments list and "Surprise me!" button. Copy: "All missions done! Want to keep playing?"

  **Motivation alignment:**
  - [ ] The hero text for empty days with available practice should say: "No missions today — but you can still make music!" instead of the current "Your music missions will appear here."
  - [ ] The "Surprise me!" button should use `SunYellow` with a playful label and a dice or music-note emoji.

  **What NOT to do:**
  - Do not auto-create assignments on the student's behalf.
  - Do not count self-initiated sessions toward the original assignment's completion on its assigned date.
  - Do not allow the student to modify, archive, or remove assignments — that stays in Parent Space.

- **Acceptance:**
  - A student with no today-assignments but prior assignments can tap "Practice this" on a recent assignment and complete a full practice session.
  - "Surprise me!" picks a random assignment and starts practice.
  - The session is logged with `studentInitiated = true` and displays distinctly in Summary.
  - Progress counts toward today's daily practice time and weekly dots, but not toward the original assignment's date completion.
  - A completely new profile with zero assignments still shows "Ask a grown-up."
  - Parents can see which sessions were self-directed vs assigned in the Summary.

---

### Suggested work order for E3/E4

1. **E3 first** — the date-range model change is structural and touches the data layer, Room schema, import/export, and filtering. Do it before E4 so the self-practice feature doesn't need to be retrofitted when ranges land.
2. **E4 after E3** — builds on top of the updated filtering logic. The `studentInitiated` flag is additive and doesn't conflict with ranges.
3. If time is tight, E3 alone delivers significant value — teachers won't need to re-create assignments daily.

---

### E5. Unified Play Along panel with one-tap record — DONE
- **Status:** Completed 2026-11-02. Verified by code review. Real-device sync testing requested.
- **Files:** `PracticeSessionPage.kt`, `MediaComponents.kt` (new `PlayAlongPanel` composable)
- **Problem:** In Play Along mode, the teacher reference video and student camera are separate cards stacked vertically in a scrolling list. The student must scroll down past the reference to see their camera, then scroll back up to rewatch the reference. This makes simultaneous "play along" impossible — the whole point of the mode is watching the teacher while playing.
- **Design: split-screen with synchronized record**

  **Layout — portrait (primary phone orientation):**
  ```
  ┌─────────────────────────┐
  │  Teacher reference (40%) │  ← smaller, top
  │  (video or YouTube)      │
  ├─────────────────────────┤
  │                          │
  │  Your camera (60%)       │  ← larger, bottom (this is what the kid watches)
  │  (live preview or        │
  │   recording playback)    │
  │                          │
  ├─────────────────────────┤
  │  ● Record  ⏹ Stop   ✓  │  ← single action bar
  └─────────────────────────┘
  ```

  **Layout — landscape (tablet or rotated phone):**
  ```
  ┌────────────┬────────────┐
  │  Teacher   │   Your     │
  │  reference │   camera   │
  │   (50%)    │   (50%)    │
  ├────────────┴────────────┤
  │  ● Record   ⏹ Stop  ✓  │
  └─────────────────────────┘
  ```

  **One-tap "Play & Record" button:**
  - Single button starts BOTH the reference video playback AND the camera recording simultaneously (after the countdown).
  - The reference video starts from `task.startSeconds` and plays through to `task.endSeconds`.
  - The camera recording begins at the same moment.
  - When the reference clip reaches its end, show a subtle pulse or border highlight — but don't auto-stop the recording (the student might still be finishing).
  - "Stop" ends the recording. The reference video pauses.
  - For YouTube references: best-effort sync. The iframe `postMessage` API can send a play command, but precise synchronization isn't guaranteed. This is acceptable — the student hears the reference audio while playing.
  - For local references: precise sync via `VideoView.start()` called at the same moment as recording start.

  **What stays outside the panel (still in the scrolling list):**
  - Task info card (title, type, progress, clip range) — compact, above the panel
  - Mode step indicator — above the panel
  - Coach notes — collapsible, above the panel
  - Repetition/time goal button — below the panel
  - Saved take card — below the panel
  - "Finish" button — below the panel

  **Mode-specific behavior:**
  - **Listen first:** Reference video only (full width), no camera. Unchanged from today.
  - **Play Along:** The new `PlayAlongPanel` — reference + camera side by side, one-tap record.
  - **Play solo:** Camera only (full width), no reference. Unchanged from today.

- **What to do:**
  - [ ] Create `PlayAlongPanel` composable in `MediaComponents.kt` (or a new `PlayAlongPanel.kt`). It takes: task (for reference source/start/end), recordingFileName, createRecordingFile, onSaved, onRecordingChanged callbacks.
  - [ ] Inside the panel, embed `ReferenceVideoPanel` (or a compact variant) and `CameraRecorderPanel` (or a compact variant) in a `Column` (portrait) or `Row` (landscape).
  - [ ] Detect orientation via `LocalConfiguration.current.orientation`. Switch layout at runtime.
  - [ ] Replace the separate "TEACHER REFERENCE" + "YOUR CAMERA" items in `PracticeSessionPage` with a single `PlayAlongPanel` item when `state.mode == PracticeMode.LISTEN_AND_PLAY`.
  - [ ] Add a "Play & Record" button that:
    - Starts the countdown (reuse existing countdown logic)
    - On countdown finish: starts recording AND sends play command to the reference video
    - For local video: call `videoView.seekTo(startSeconds * 1000)` then `videoView.start()`
    - For YouTube: use `postMessage` to the iframe with `{"event":"command","func":"playVideo"}`
  - [ ] Add a "Stop" button that stops recording and pauses the reference.
  - [ ] Keep the reference panel non-interactive during recording (no manual seek/pause — prevents accidental touches while the child is holding an instrument).
  - [ ] Reduce reference video height in portrait to ~140dp (from 210dp) to fit both panels.
  - [ ] Reduce camera preview height in portrait to ~180dp (from the current unconstrained height).
  - [ ] The combined panel should fit within ~400dp total in portrait, leaving room for the task card above and the action buttons below without scrolling.
  - [ ] Add accessibility: merged content description "Play along: teacher reference on top, your camera below. Tap Play and Record to start both."

- **What NOT to change:**
  - Listen mode and Play Solo mode keep their current full-width single-panel layout.
  - The recording file format, naming, storage, and event logging stay the same.
  - The countdown behavior stays the same (respects reduced-motion setting).

- **Acceptance:**
  - In Play Along mode, both the reference video and camera preview are visible simultaneously without scrolling.
  - "Play & Record" starts the reference from `startSeconds` and begins recording at the same time.
  - The student can see the teacher's demonstration while their own camera captures their playing.
  - Portrait and landscape orientations both work.
  - Listen mode and Play Solo mode are unaffected.
  - The panel fits on a typical phone screen (5.5"–6.7") without scrolling past the video area.
