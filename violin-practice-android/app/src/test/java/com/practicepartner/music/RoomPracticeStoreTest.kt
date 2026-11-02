package com.practicepartner.music

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.Room
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.system.measureNanoTime
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomPracticeStoreTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private lateinit var database: PracticeDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var legacyPrefs: SharedPreferences
    private lateinit var migration: LegacyRoomMigration
    private lateinit var practiceStore: PracticeStore
    private lateinit var familyStore: FamilyStore

    @Before
    fun setUp() {
        prefs = context.getSharedPreferences(
            PRACTICE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).also { it.edit(commit = true) { clear() } }
        legacyPrefs = context.getSharedPreferences(
            LEGACY_PRACTICE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).also { it.edit(commit = true) { clear() } }
        database = Room.inMemoryDatabaseBuilder(
            context,
            PracticeDatabase::class.java,
        ).allowMainThreadQueries().build()
        migration = LegacyRoomMigration(database, prefs, legacyPrefs)
        practiceStore = PracticeStore(
            database = database,
            runner = DirectDatabaseRunner,
            migration = migration,
        )
        familyStore = FamilyStore(
            database = database,
            runner = DirectDatabaseRunner,
            prefs = prefs,
            migration = migration,
        )
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit(commit = true) { clear() }
        legacyPrefs.edit(commit = true) { clear() }
    }

    @Test
    fun legacyHouseholdAndEveryProfileSnapshotMigrateWithoutLoss() {
        val first = profile("child-1", "Student One")
        val second = profile("child-2", "Student Two")
        val household = Household(
            id = "household-1",
            childProfiles = listOf(first, second),
            activeChildProfileId = second.id,
        )
        val firstSnapshot = completeSnapshot(first, DateKey("2026-10-01"), "first")
        val secondSnapshot = completeSnapshot(second, DateKey("2026-10-02"), "second")
        prefs.edit(commit = true) {
            putString(HOUSEHOLD_KEY, HouseholdJsonCodec.encode(household))
            putString(snapshotKey(first.id), PracticeSnapshotJsonCodec.encode(firstSnapshot))
            putString(snapshotKey(second.id), PracticeSnapshotJsonCodec.encode(secondSnapshot))
            putLong("local_data_revision", 7L)
        }

        val migratedHousehold = familyStore.loadHousehold()

        assertEquals(household, migratedHousehold)
        assertEquals(firstSnapshot, practiceStore.load(first))
        assertEquals(secondSnapshot, practiceStore.load(second))
        assertEquals(7L, familyStore.loadLocalRevision(second.id))
        assertTrue(prefs.getBoolean(ROOM_DATA_MIGRATION_KEY, false))
        assertFalse(prefs.contains(HOUSEHOLD_KEY))
        assertFalse(prefs.contains(snapshotKey(first.id)))
        assertFalse(prefs.contains(snapshotKey(second.id)))
        assertNotNull(database.householdDao().load())
        assertEquals(2, database.childProfileDao().loadForHousehold(household.id).size)
    }

    @Test
    fun damagedPrimarySnapshotRecoversFromBackupDuringMigration() {
        val profile = profile("child-1", "Student")
        val household = Household(
            id = "household-1",
            childProfiles = listOf(profile),
            activeChildProfileId = profile.id,
        )
        val backup = completeSnapshot(profile, DateKey("2026-10-01"), "backup")
        prefs.edit(commit = true) {
            putString(HOUSEHOLD_KEY, HouseholdJsonCodec.encode(household))
            putString(snapshotKey(profile.id), "{damaged")
            putString(
                snapshotBackupKey(profile.id),
                PracticeSnapshotJsonCodec.encode(backup),
            )
        }

        familyStore.loadHousehold()
        val loaded = practiceStore.loadWithStatus(profile)

        assertEquals(backup, loaded.snapshot)
        assertTrue(loaded.warning.orEmpty().contains("recovered"))
        assertFalse(prefs.contains(snapshotBackupKey(profile.id)))
    }

    @Test
    fun originalSingleProfilePreferencesMigrateIntoRoom() {
        prefs.edit(commit = true) {
            putString("student_profile_id", "legacy-child")
            putString("student_profile_name", "Legacy Student")
            putString("student_profile_instrument", "Violin")
        }
        legacyPrefs.edit(commit = true) {
            putString(
                "tasks",
                """
                    [
                      {
                        "id":"legacy-task",
                        "title":"Long bow",
                        "source":"",
                        "sourceType":"none",
                        "startSeconds":0,
                        "endSeconds":null,
                        "repetitions":3
                      }
                    ]
                """.trimIndent(),
            )
            putStringSet("completedIds", setOf("legacy-task"))
            putLong("elapsedSeconds", 240L)
        }

        val household = migration.migrateAllIfNeeded(DateKey("2026-09-15"))
        val profile = household.activeChildProfile!!
        val snapshot = practiceStore.load(profile)

        assertEquals("legacy-child", profile.id)
        assertEquals("Legacy Student", profile.name)
        assertEquals(listOf("legacy-task"), snapshot.tasks.map(PracticeTask::id))
        assertEquals(
            setOf("legacy-task"),
            snapshot.progressByDate.getValue(DateKey("2026-09-15")).completedTaskIds,
        )
        assertEquals(
            240L,
            snapshot.progressByDate.getValue(DateKey("2026-09-15")).elapsedSeconds,
        )
        assertTrue(legacyPrefs.all.isEmpty())
    }

    @Test
    fun roomVersionOneDatabaseMigratesToAssignmentHistorySchema() {
        val databaseName = "practice-migration-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        val schemaFile = listOf(
            File("schemas/com.practicepartner.music.PracticeDatabase/1.json"),
            File("app/schemas/com.practicepartner.music.PracticeDatabase/1.json"),
        ).first(File::isFile)
        val schema = JSONObject(schemaFile.readText()).getJSONObject("database")
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacy ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                legacy.execSQL(
                    entity.getString("createSql").replace(
                        "\${TABLE_NAME}",
                        entity.getString("tableName"),
                    ),
                )
            }
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val indices = entity.optJSONArray("indices") ?: return@repeat
                repeat(indices.length()) { indexPosition ->
                    legacy.execSQL(
                        indices.getJSONObject(indexPosition)
                            .getString("createSql")
                            .replace(
                                "\${TABLE_NAME}",
                                entity.getString("tableName"),
                            ),
                    )
                }
            }
            val setupQueries = schema.getJSONArray("setupQueries")
            repeat(setupQueries.length()) { index ->
                legacy.execSQL(setupQueries.getString(index))
            }
            legacy.execSQL(
                "INSERT INTO households (id, name, activeChildProfileId) " +
                    "VALUES ('household-1', 'Our Family', 'child-1')",
            )
            legacy.execSQL(
                "INSERT INTO child_profiles (" +
                    "id, householdId, name, instrument, excelWorkbookUri, " +
                    "excelWorkbookName, sortOrder" +
                    ") VALUES ('child-1', 'household-1', 'Student', 'Violin', " +
                    "NULL, NULL, 0)",
            )
            legacy.execSQL(
                "INSERT INTO assignments (" +
                    "id, childProfileId, assignedDate, title, source, sourceType, " +
                    "startSeconds, endSeconds, repetitions, coachNote, whyImportant, " +
                    "sheetMusicUri, instrument, assignmentType, goalType, targetSeconds, " +
                    "enabledModesJson, revision, archivedAtMillis, sortOrder" +
                    ") VALUES (" +
                    "'task-1', 'child-1', '2026-11-01', 'Boreas', '', 'NONE', " +
                    "0, NULL, 3, '', '', NULL, 'Violin', 'REPERTOIRE', " +
                    "'REPETITIONS', 0, '[\"PLAY_YOURSELF\"]', 1, NULL, 0" +
                    ")",
            )
            legacy.version = 1
        }

        val migrated = Room.databaseBuilder(
            context,
            PracticeDatabase::class.java,
            databaseName,
        ).allowMainThreadQueries()
            .addMigrations(PRACTICE_DATABASE_MIGRATION_1_2)
            .build()
        try {
            val assignment = migrated.assignmentDao().loadForProfile("child-1").single()

            assertNull(assignment.teacherId)
            assertEquals("Boreas", assignment.title)
            assertTrue(migrated.assignmentHistoryDao().loadForProfile("child-1").isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun timerStyleChangeOnlyUpsertsProgressAndActiveSessionRows() {
        val profile = profile("child-1", "Student")
        familyStore.saveHousehold(
            Household(
                id = "household-1",
                childProfiles = listOf(profile),
                activeChildProfileId = profile.id,
            ),
        )
        val before = completeSnapshot(profile, DateKey("2026-11-01"), "timer")
        practiceStore.replace(profile, before)
        val date = before.progressByDate.keys.single()
        val next = before.copy(
            progressByDate = before.progressByDate + (
                date to before.progressByDate.getValue(date).copy(elapsedSeconds = 125L)
                ),
            sessions = before.sessions.map {
                it.copy(elapsedSeconds = 125L)
            },
        )

        val changes = practicePersistenceChanges(before, next)
        val changesBeforeUpdate = sqliteTotalChanges()
        practiceStore.applyChanges(profile, before, next)
        val changedRows = sqliteTotalChanges() - changesBeforeUpdate

        assertTrue(changes.assignmentsToUpsert.isEmpty())
        assertTrue(changes.assignmentIdsToDelete.isEmpty())
        assertEquals(1, changes.progressToUpsert.size)
        assertTrue(changes.eventsToUpsert.isEmpty())
        assertEquals(1, changes.sessionsToUpsert.size)
        assertEquals(2, changedRows)
        assertEquals(next, practiceStore.load(profile))
    }

    @Test
    fun granularChangesInsertUpdateAndDeleteOnlyChangedRecords() {
        val profile = profile("child-1", "Student")
        persistHousehold(profile)
        val before = completeSnapshot(profile, DateKey("2026-11-01"), "before")
        practiceStore.replace(profile, before)
        val next = before.copy(
            tasks = before.tasks.map { it.copy(coachNote = "Use a slower bow.") },
            events = emptyList(),
        )

        val changes = practicePersistenceChanges(before, next)
        practiceStore.applyChanges(profile, before, next)

        assertEquals(listOf(before.tasks.single().id), changes.assignmentsToUpsert.map { it.id })
        assertEquals(listOf(before.events.single().id), changes.eventIdsToDelete)
        assertTrue(changes.progressToUpsert.isEmpty())
        assertTrue(changes.sessionsToUpsert.isEmpty())
        assertEquals(next, practiceStore.load(profile))
    }

    @Test
    fun assignmentEditPersistsPreviousRevisionInTheSameGranularChange() {
        val profile = profile("child-1", "Student")
        persistHousehold(profile)
        val before = completeSnapshot(profile, DateKey("2026-11-01"), "history")
        practiceStore.replace(profile, before)
        val next = preserveAssignmentVersionHistory(
            previous = before,
            candidate = before.copy(
                tasks = before.tasks.map {
                    it.copy(
                        title = "Boreas seating excerpt",
                        revision = it.revision + 1,
                    )
                },
            ),
            changedAtMillis = 2_000L,
        )

        val changes = practicePersistenceChanges(before, next)
        practiceStore.applyChanges(profile, before, next)

        assertEquals(1, changes.assignmentsToUpsert.size)
        assertEquals(1, changes.assignmentHistoryToUpsert.size)
        assertTrue(changes.assignmentHistoryToDelete.isEmpty())
        assertEquals(next, practiceStore.load(profile))
        val storedHistory = database.assignmentHistoryDao()
            .loadForProfile(profile.id)
            .single()
            .toModel()
        assertEquals(before.tasks.single(), storedHistory.previousAssignment)
        assertEquals(setOf("title"), storedHistory.changedFields)
    }

    @Test
    fun deletingAChildProfileCascadesAllRoomPracticeRows() {
        val first = profile("child-1", "Student One")
        val second = profile("child-2", "Student Two")
        familyStore.saveHousehold(
            Household(
                id = "household-1",
                childProfiles = listOf(first, second),
                activeChildProfileId = first.id,
            ),
        )
        practiceStore.replace(
            first,
            completeSnapshot(first, DateKey("2026-11-01"), "first"),
        )
        practiceStore.replace(
            second,
            completeSnapshot(second, DateKey("2026-11-01"), "second"),
        )

        familyStore.removeChildProfile(first.id)

        assertTrue(database.assignmentDao().loadForProfile(first.id).isEmpty())
        assertTrue(database.dailyProgressDao().loadForProfile(first.id).isEmpty())
        assertTrue(database.practiceEventDao().loadForProfile(first.id).isEmpty())
        assertTrue(database.practiceSessionDao().loadForProfile(first.id).isEmpty())
        assertEquals(1, database.assignmentDao().loadForProfile(second.id).size)
    }

    @Test
    fun sixMonthsOfPracticeLoadsInUnderTwoHundredMilliseconds() {
        val profile = profile("child-1", "Student")
        persistHousehold(profile)
        val start = LocalDate.of(2026, 1, 1)
        val assignments = mutableListOf<PracticeTask>()
        val progress = linkedMapOf<DateKey, DailyProgress>()
        val events = mutableListOf<PracticeEvent>()
        val sessions = mutableListOf<PracticeSession>()
        repeat(180) { day ->
            val date = DateKey.from(start.plusDays(day.toLong()))
            val assignment = PracticeTask(
                id = "task-$day",
                assignedDate = date,
                title = "Practice $day",
                source = "",
                sourceType = SourceType.NONE,
                startSeconds = 0,
                endSeconds = null,
                repetitions = 3,
                childProfileId = profile.id,
                instrument = profile.instrument,
            )
            assignments += assignment
            progress[date] = DailyProgress(
                date = date,
                childProfileId = profile.id,
                completedTaskIds = setOf(assignment.id),
                repetitionsByTask = mapOf(assignment.id to 3),
                elapsedSeconds = 900L,
            )
            repeat(3) { sessionIndex ->
                val timestamp = day * 100_000L + sessionIndex
                sessions += PracticeSession(
                    id = "session-$day-$sessionIndex",
                    childProfileId = profile.id,
                    assignmentId = assignment.id,
                    assignmentTitle = assignment.title,
                    instrument = profile.instrument,
                    date = date,
                    mode = PracticeMode.PLAY_YOURSELF,
                    startedAtMillis = timestamp,
                    endedAtMillis = timestamp + 300L,
                    repetitionsCompleted = 1,
                    status = PracticeSessionStatus.COMPLETED,
                    elapsedSeconds = 300L,
                )
            }
            repeat(5) { eventIndex ->
                val timestamp = day * 100_000L + 10_000L + eventIndex
                events += PracticeEvent(
                    id = "event-$day-$eventIndex",
                    childProfileId = profile.id,
                    date = date,
                    taskId = assignment.id,
                    taskTitle = assignment.title,
                    event = "Practice event $eventIndex",
                    atMillis = timestamp,
                )
            }
        }
        val snapshot = PracticeSnapshot(assignments, progress, events, sessions)
        practiceStore.replace(profile, snapshot)
        practiceStore.load(profile)

        val elapsedMillis = measureNanoTime {
            repeat(5) {
                assertEquals(snapshot, practiceStore.load(profile))
            }
        } / 1_000_000.0 / 5.0

        assertTrue(
            "Expected an average load below 200ms, but it was ${"%.1f".format(elapsedMillis)}ms",
            elapsedMillis < 200.0,
        )
    }

    private fun profile(id: String, name: String): ChildProfile = ChildProfile(
        id = id,
        name = name,
        instrument = "Violin",
    )

    private fun persistHousehold(profile: ChildProfile) {
        familyStore.saveHousehold(
            Household(
                id = "household-${profile.id}",
                childProfiles = listOf(profile),
                activeChildProfileId = profile.id,
            ),
        )
    }

    private fun sqliteTotalChanges(): Int =
        database.openHelper.writableDatabase
            .query("SELECT total_changes()")
            .use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }

    private fun completeSnapshot(
        profile: ChildProfile,
        date: DateKey,
        suffix: String,
    ): PracticeSnapshot {
        val task = PracticeTask(
            id = "task-$suffix",
            assignedDate = date,
            title = "Boreas $suffix",
            source = "https://youtu.be/example",
            sourceType = SourceType.YOUTUBE,
            startSeconds = 5,
            endSeconds = 25,
            repetitions = 4,
            coachNote = "Slow bow",
            whyImportant = "Even sound",
            childProfileId = profile.id,
            instrument = profile.instrument,
            assignmentType = AssignmentType.REPERTOIRE,
            enabledModes = PracticeMode.entries.toSet(),
        )
        val session = PracticeSession(
            id = "session-$suffix",
            childProfileId = profile.id,
            assignmentId = task.id,
            assignmentTitle = task.title,
            instrument = profile.instrument,
            date = date,
            mode = PracticeMode.PLAY_YOURSELF,
            modesUsed = setOf(
                PracticeMode.LISTEN,
                PracticeMode.PLAY_YOURSELF,
            ),
            startedAtMillis = 1_000L,
            endedAtMillis = 1_120L,
            repetitionsCompleted = 4,
            status = PracticeSessionStatus.COMPLETED,
            assignmentType = AssignmentType.REPERTOIRE,
            targetRepetitions = 4,
            elapsedSeconds = 120L,
            reflection = PracticeReflection.PROUD,
            masteryStatus = MasteryStatus.STUDENT_CONFIDENT,
        )
        return PracticeSnapshot(
            tasks = listOf(task),
            progressByDate = mapOf(
                date to DailyProgress(
                    date = date,
                    childProfileId = profile.id,
                    completedTaskIds = setOf(task.id),
                    repetitionsByTask = mapOf(task.id to 4),
                    elapsedSeconds = 120L,
                ),
            ),
            events = listOf(
                PracticeEvent(
                    id = "event-$suffix",
                    date = date,
                    taskId = task.id,
                    taskTitle = task.title,
                    event = "Practice completed",
                    atMillis = 1_100L,
                    videoUri = "content://video/$suffix",
                    videoPath = "/recordings/$suffix.mp4",
                    videoFileName = "$suffix.mp4",
                    childProfileId = profile.id,
                    sessionId = session.id,
                ),
            ),
            sessions = listOf(session),
        )
    }
}
