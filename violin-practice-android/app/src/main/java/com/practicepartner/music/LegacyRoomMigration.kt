package com.practicepartner.music

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID

internal class LegacyRoomMigration(
    private val database: PracticeDatabase,
    private val prefs: SharedPreferences,
    private val legacyPrefs: SharedPreferences,
) {
    fun migrateAllIfNeeded(
        currentDate: DateKey = currentDateKey(),
    ): Household {
        val alreadyMigrated = prefs.getBoolean(ROOM_DATA_MIGRATION_KEY, false)
        val existing = loadRoomHousehold()
        if (alreadyMigrated && existing != null) return existing

        val household = existing ?: readLegacyHousehold()
        val migrationResults = household.childProfiles.associateWith { profile ->
            if (prefs.getBoolean(profileMigrationKey(profile.id), false)) {
                null
            } else {
                readLegacyPractice(profile, currentDate)
            }
        }
        database.runInTransaction {
            database.householdDao().upsert(household.toEntity())
            database.childProfileDao().upsertAll(
                household.childProfiles.mapIndexed { index, profile ->
                    profile.toEntity(household.id, index)
                },
            )
            database.childProfileDao().deleteExcept(
                household.id,
                household.childProfiles.map(ChildProfile::id),
            )
            migrationResults.forEach { (profile, result) ->
                result?.let {
                    database.replacePracticeSnapshotInTransaction(profile, it.snapshot)
                }
            }
        }

        val legacyPracticeKeys = legacyPracticeKeys()
        prefs.edit(commit = true) {
            putBoolean(ROOM_DATA_MIGRATION_KEY, true)
            migrationResults.forEach { (profile, result) ->
                putBoolean(profileMigrationKey(profile.id), true)
                result?.warning?.let {
                    putString(migrationWarningKey(profile.id), it)
                }
            }
            remove(HOUSEHOLD_KEY)
            remove("student_profile_id")
            remove("student_profile_name")
            remove("student_profile_instrument")
            remove("excel_workbook_uri")
            remove("excel_workbook_name")
            remove(LEGACY_SNAPSHOT_OWNER_KEY)
            legacyPracticeKeys.forEach(::remove)
        }
        legacyPrefs.edit(commit = true) { clear() }
        return household
    }

    fun migrateProfileIfNeeded(
        profile: ChildProfile,
        currentDate: DateKey,
    ) {
        if (
            prefs.getBoolean(ROOM_DATA_MIGRATION_KEY, false) ||
            prefs.getBoolean(profileMigrationKey(profile.id), false)
        ) {
            return
        }
        val result = readLegacyPractice(profile, currentDate)
        database.replacePracticeSnapshot(profile, result.snapshot)
        prefs.edit(commit = true) {
            putBoolean(profileMigrationKey(profile.id), true)
            result.warning?.let {
                putString(migrationWarningKey(profile.id), it)
            }
            profilePracticeKeys(profile.id).forEach(::remove)
        }
    }

    fun clearProfileLegacyData(childProfileId: String) {
        val ownsSingleProfileSnapshot =
            prefs.getString(LEGACY_SNAPSHOT_OWNER_KEY, null) == childProfileId
        prefs.edit(commit = true) {
            putBoolean(profileMigrationKey(childProfileId), true)
            remove(migrationWarningKey(childProfileId))
            profilePracticeKeys(childProfileId).forEach(::remove)
            if (ownsSingleProfileSnapshot) {
                remove("snapshot")
                remove("snapshot_backup")
                remove(LEGACY_SNAPSHOT_OWNER_KEY)
            }
        }
    }

    fun consumeWarning(childProfileId: String): String? {
        val key = migrationWarningKey(childProfileId)
        val warning = prefs.getString(key, null)
        if (warning != null) {
            prefs.edit(commit = true) { remove(key) }
        }
        return warning
    }

    private fun loadRoomHousehold(): Household? {
        val household = database.householdDao().load() ?: return null
        val profiles = database.childProfileDao().loadForHousehold(household.id)
        if (profiles.isEmpty()) return null
        return normalizeHousehold(householdFromEntities(household, profiles))
    }

    private fun readLegacyHousehold(): Household {
        prefs.getString(HOUSEHOLD_KEY, null)
            ?.let(HouseholdJsonCodec::decode)
            ?.let(::normalizeHousehold)
            ?.let { return it }

        val defaultLegacyName = "Student"
        val profile = ChildProfile(
            id = prefs.getString("student_profile_id", null) ?: UUID.randomUUID().toString(),
            name = prefs.getString("student_profile_name", defaultLegacyName)
                .orEmpty()
                .ifBlank { defaultLegacyName },
            instrument = prefs.getString("student_profile_instrument", "Violin")
                .orEmpty()
                .ifBlank { "Violin" },
            excelWorkbookUri = prefs.getString("excel_workbook_uri", null),
            excelWorkbookName = prefs.getString("excel_workbook_name", null),
        )
        return Household(
            id = UUID.randomUUID().toString(),
            childProfiles = listOf(profile),
            activeChildProfileId = profile.id,
        )
    }

    private fun readLegacyPractice(
        profile: ChildProfile,
        currentDate: DateKey,
    ): PracticeLoadResult {
        val primaryKey = snapshotKey(profile.id)
        val primary = prefs.getString(primaryKey, null)
        if (primary != null) {
            val decoded = decodeStoredSnapshot(primary, profile)
            if (decoded != null) return decoded.asLoadResult()

            val backup = prefs.getString(snapshotBackupKey(profile.id), null)
                ?.let { decodeStoredSnapshot(it, profile) }
            if (backup != null) {
                return PracticeLoadResult(
                    snapshot = backup.snapshot,
                    warning = buildString {
                        append("Practice data was recovered from the last local backup.")
                        skippedRecordWarning(backup.skippedRecords)?.let {
                            append(' ')
                            append(it)
                        }
                    },
                )
            }
            return PracticeLoadResult(
                snapshot = PracticeSnapshot(),
                warning =
                    "The local practice file could not be read and no valid local backup was available.",
            )
        }

        previousSnapshotKeys(profile.id).forEach { previousKey ->
            prefs.getString(previousKey, null)
                ?.let { decodeStoredSnapshot(it, profile) }
                ?.let { return it.asLoadResult() }
        }

        val owner = prefs.getString(LEGACY_SNAPSHOT_OWNER_KEY, null)
        if (owner == null || owner == profile.id) {
            prefs.getString("snapshot", null)
                ?.let { decodeStoredSnapshot(it, profile) }
                ?.let { return it.asLoadResult() }
            prefs.getString("snapshot_backup", null)
                ?.let { decodeStoredSnapshot(it, profile) }
                ?.let {
                    return PracticeLoadResult(
                        snapshot = it.snapshot,
                        warning =
                            "Practice data was recovered from the previous single-student backup.",
                    )
                }
            if (owner == null) {
                return PracticeLoadResult(migrateVersionOnePractice(currentDate, profile))
            }
        }
        return PracticeLoadResult(PracticeSnapshot())
    }

    private fun migrateVersionOnePractice(
        currentDate: DateKey,
        profile: ChildProfile,
    ): PracticeSnapshot {
        val tasks = decodeLegacyRows<LegacyPracticeTask>(
            legacyPrefs.getString("tasks", "[]").orEmpty(),
        ).map { row ->
            PracticeTask(
                id = row.id.ifBlank { UUID.randomUUID().toString() },
                assignedDate = currentDate,
                title = row.title,
                source = row.source,
                sourceType = runCatching {
                    SourceType.valueOf(row.sourceType.uppercase())
                }.getOrElse {
                    sourceTypeFor(row.source) ?: SourceType.NONE
                },
                startSeconds = row.startSeconds,
                endSeconds = row.endSeconds,
                repetitions = row.repetitions.coerceIn(1, 20),
                childProfileId = profile.id,
                instrument = profile.instrument,
            )
        }
        val completed = legacyPrefs.getStringSet("completedIds", emptySet())
            ?.toSet()
            .orEmpty()
        val taskIds = tasks.mapTo(mutableSetOf(), PracticeTask::id)
        val progress = DailyProgress(
            date = currentDate,
            childProfileId = profile.id,
            completedTaskIds = completed.intersect(taskIds),
            repetitionsByTask = tasks.associate { task ->
                task.id to if (task.id in completed) task.repetitions else 0
            },
            elapsedSeconds = legacyPrefs.getLong("elapsedSeconds", 0L).coerceAtLeast(0L),
        )
        val events = decodeLegacyRows<LegacyPracticeEvent>(
            legacyPrefs.getString("events", "[]").orEmpty(),
        ).map { row ->
            val atMillis = row.atMillis ?: System.currentTimeMillis()
            PracticeEvent(
                id = UUID.randomUUID().toString(),
                date = dateKeyForMillis(atMillis),
                taskId = null,
                taskTitle = row.taskTitle,
                event = row.event,
                atMillis = atMillis,
                videoUri = row.videoUri,
                videoPath = row.videoPath,
                videoFileName = recordingFileNameFromPath(row.videoPath),
                childProfileId = profile.id,
            )
        }
        return PracticeSnapshot(
            tasks = tasks,
            progressByDate = if (tasks.isEmpty() && progress.elapsedSeconds == 0L) {
                emptyMap()
            } else {
                mapOf(currentDate to progress)
            },
            events = events.takeLast(MAX_PRACTICE_EVENTS),
        )
    }

    private fun decodeStoredSnapshot(
        value: String,
        profile: ChildProfile,
    ): DecodedPracticeSnapshot? = PracticeSnapshotJsonCodec.decode(value, profile)

    private fun legacyPracticeKeys(): List<String> = prefs.all.keys.filter { key ->
        key == "snapshot" ||
            key == "snapshot_backup" ||
            key.startsWith("snapshot_v5:") ||
            key.startsWith("snapshot_v5_backup:") ||
            key.startsWith("snapshot_v6:") ||
            key.startsWith("snapshot_v6_backup:") ||
            key.startsWith("snapshot_v7:") ||
            key.startsWith("snapshot_v7_backup:")
    }
}

internal fun normalizeHousehold(household: Household): Household {
    require(household.childProfiles.isNotEmpty()) {
        "A household must contain at least one child profile."
    }
    val profiles = household.childProfiles
        .map { profile ->
            profile.copy(
                name = profile.name.trim().ifBlank { "Student" },
                instrument = profile.instrument.trim().ifBlank { "Instrument" },
            )
        }
        .distinctBy(ChildProfile::id)
    val activeId = household.activeChildProfileId
        .takeIf { id -> profiles.any { it.id == id } }
        ?: profiles.first().id
    return household.copy(
        name = household.name.trim().ifBlank { "Our Family" },
        childProfiles = profiles,
        activeChildProfileId = activeId,
    )
}

internal fun snapshotKey(childProfileId: String): String =
    "snapshot_v7:$childProfileId"

internal fun snapshotBackupKey(childProfileId: String): String =
    "snapshot_v7_backup:$childProfileId"

internal fun previousSnapshotKeys(childProfileId: String): List<String> = listOf(
    "snapshot_v6:$childProfileId",
    "snapshot_v6_backup:$childProfileId",
    "snapshot_v5:$childProfileId",
    "snapshot_v5_backup:$childProfileId",
)

private fun profilePracticeKeys(childProfileId: String): List<String> =
    listOf(snapshotKey(childProfileId), snapshotBackupKey(childProfileId)) +
        previousSnapshotKeys(childProfileId)

private fun migrationWarningKey(childProfileId: String): String =
    "$ROOM_MIGRATION_WARNING_PREFIX$childProfileId"

private fun profileMigrationKey(childProfileId: String): String =
    "$ROOM_PROFILE_MIGRATION_PREFIX$childProfileId"

private fun DecodedPracticeSnapshot.asLoadResult(): PracticeLoadResult =
    PracticeLoadResult(
        snapshot = snapshot,
        warning = skippedRecordWarning(skippedRecords),
    )

internal fun skippedRecordWarning(skippedRecords: Int): String? =
    skippedRecords.takeIf { it > 0 }?.let {
        "Recovered the readable data and skipped $it damaged record${if (it == 1) "" else "s"}."
    }

@Serializable
private data class LegacyPracticeTask(
    val id: String = "",
    val title: String = "",
    val source: String = "",
    val sourceType: String = "youtube",
    val startSeconds: Int = 0,
    val endSeconds: Int? = null,
    val repetitions: Int = 1,
)

@Serializable
private data class LegacyPracticeEvent(
    val taskTitle: String = "Practice session",
    val event: String = "",
    val atMillis: Long? = null,
    val videoUri: String? = null,
    val videoPath: String? = null,
)

private inline fun <reified T> decodeLegacyRows(value: String): List<T> =
    runCatching {
        storeJson.decodeFromString<List<JsonElement>>(value)
    }.getOrDefault(emptyList()).mapNotNull { row ->
        runCatching {
            storeJson.decodeFromJsonElement<T>(row)
        }.getOrNull()
    }

internal const val HOUSEHOLD_KEY = "household_v1"
internal const val LEGACY_SNAPSHOT_OWNER_KEY = "legacy_snapshot_owner_child_id"
internal const val ROOM_DATA_MIGRATION_KEY = "room_data_migration_v1"
private const val ROOM_MIGRATION_WARNING_PREFIX = "room_migration_warning_v1:"
private const val ROOM_PROFILE_MIGRATION_PREFIX = "room_profile_migration_v1:"
