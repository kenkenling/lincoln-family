package com.practicepartner.music

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID

data class PracticeLoadResult(
    val snapshot: PracticeSnapshot,
    val warning: String? = null,
)

class PracticeStore(context: Context) : PracticeDataStore {
    private val prefs = context.getSharedPreferences("practice_partner_v2", Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences("practice_partner_v1", Context.MODE_PRIVATE)

    override fun load(
        profile: ChildProfile,
        currentDate: String,
    ): PracticeSnapshot = loadWithStatus(profile, currentDate).snapshot

    override fun loadWithStatus(
        profile: ChildProfile,
        currentDate: String,
    ): PracticeLoadResult {
        val key = snapshotKey(profile.id)
        val backupKey = snapshotBackupKey(profile.id)
        val stored = prefs.getString(key, null)
        if (stored != null) {
            decodeStoredSnapshot(stored, profile)?.let { decoded ->
                ensureLegacySnapshotOwner(profile.id)
                return PracticeLoadResult(
                    snapshot = decoded.snapshot,
                    warning = skippedRecordWarning(decoded.skippedRecords),
                )
            }

            val backup = prefs.getString(backupKey, null)
            val recovered = backup?.let { decodeStoredSnapshot(it, profile) }
            if (recovered != null) {
                prefs.edit(commit = true) {
                    putString(key, backup)
                    putString(LEGACY_SNAPSHOT_OWNER_KEY, profile.id)
                }
                return PracticeLoadResult(
                    snapshot = recovered.snapshot,
                    warning = buildString {
                        append("Practice data was recovered from the last local backup.")
                        skippedRecordWarning(recovered.skippedRecords)?.let {
                            append(' ')
                            append(it)
                        }
                    },
                )
            }

            return PracticeLoadResult(
                snapshot = PracticeSnapshot(),
                warning = "The local practice file could not be read and no valid local backup was available.",
            )
        }
        val upgraded = previousSnapshotKeys(profile.id)
            .asSequence()
            .mapNotNull { previousKey -> prefs.getString(previousKey, null) }
            .mapNotNull { value -> decodeStoredSnapshot(value, profile) }
            .firstOrNull()
        if (upgraded != null) {
            val encoded = PracticeSnapshotJsonCodec.encode(upgraded.snapshot)
            prefs.edit(commit = true) {
                putString(key, encoded)
                putString(LEGACY_SNAPSHOT_OWNER_KEY, profile.id)
            }
            return PracticeLoadResult(
                snapshot = upgraded.snapshot,
                warning = skippedRecordWarning(upgraded.skippedRecords),
            )
        }
        val migrated = claimLegacySnapshot(profile, currentDate)
        val encoded = PracticeSnapshotJsonCodec.encode(migrated.snapshot.scopedTo(profile))
        prefs.edit(commit = true) {
            putString(key, encoded)
            putString(LEGACY_SNAPSHOT_OWNER_KEY, profile.id)
        }
        return migrated
    }

    override fun save(profile: ChildProfile, snapshot: PracticeSnapshot) {
        val key = snapshotKey(profile.id)
        val backupKey = snapshotBackupKey(profile.id)
        val encoded = PracticeSnapshotJsonCodec.encode(snapshot.scopedTo(profile))
        val previous = prefs.getString(key, null)
            ?.takeIf { decodeStoredSnapshot(it, profile) != null }
        prefs.edit(commit = true) {
            if (previous != null) {
                putString(backupKey, previous)
            }
            putString(key, encoded)
        }
    }

    override fun delete(profile: ChildProfile) {
        val ownsLegacySnapshot =
            prefs.getString(LEGACY_SNAPSHOT_OWNER_KEY, null) == profile.id
        prefs.edit(commit = true) {
            remove(snapshotKey(profile.id))
            remove(snapshotBackupKey(profile.id))
            previousSnapshotKeys(profile.id).forEach(::remove)
            if (ownsLegacySnapshot) {
                remove("snapshot")
                remove("snapshot_backup")
                remove(LEGACY_SNAPSHOT_OWNER_KEY)
            }
        }
        if (ownsLegacySnapshot) {
            legacyPrefs.edit(commit = true) {
                clear()
            }
        }
    }

    override fun referencedRecordingPaths(profiles: List<ChildProfile>): Set<String> =
        profiles.flatMap { profile ->
            load(profile).events.mapNotNull(PracticeEvent::videoPath)
        }.toSet()

    private fun claimLegacySnapshot(
        profile: ChildProfile,
        currentDate: String,
    ): PracticeLoadResult {
        if (prefs.contains(LEGACY_SNAPSHOT_OWNER_KEY)) {
            return PracticeLoadResult(PracticeSnapshot())
        }

        val primary = prefs.getString("snapshot", null)
            ?.let { decodeStoredSnapshot(it, profile) }
        val backup = prefs.getString("snapshot_backup", null)
            ?.let { decodeStoredSnapshot(it, profile) }
        val migrated = when {
            primary != null -> PracticeLoadResult(
                snapshot = primary.snapshot,
                warning = skippedRecordWarning(primary.skippedRecords),
            )
            backup != null -> PracticeLoadResult(
                snapshot = backup.snapshot,
                warning = "Practice data was recovered from the previous single-student backup.",
            )
            else -> PracticeLoadResult(migrateLegacy(currentDate, profile))
        }
        return migrated
    }

    private fun ensureLegacySnapshotOwner(childProfileId: String) {
        if (prefs.contains(LEGACY_SNAPSHOT_OWNER_KEY)) return
        prefs.edit(commit = true) {
            putString(LEGACY_SNAPSHOT_OWNER_KEY, childProfileId)
        }
    }

    private fun migrateLegacy(
        currentDate: String,
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

        val completed = legacyPrefs.getStringSet("completedIds", emptySet())?.toSet().orEmpty()
        val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
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
            events = events.takeLast(500),
        )
    }

    private fun decodeStoredSnapshot(
        value: String,
        profile: ChildProfile,
    ): DecodedPracticeSnapshot? = PracticeSnapshotJsonCodec.decode(value, profile)

    private fun snapshotKey(childProfileId: String): String =
        "snapshot_v7:$childProfileId"

    private fun snapshotBackupKey(childProfileId: String): String =
        "snapshot_v7_backup:$childProfileId"

    private fun previousSnapshotKeys(childProfileId: String): List<String> = listOf(
        "snapshot_v6:$childProfileId",
        "snapshot_v6_backup:$childProfileId",
        "snapshot_v5:$childProfileId",
        "snapshot_v5_backup:$childProfileId",
    )
}

private const val LEGACY_SNAPSHOT_OWNER_KEY = "legacy_snapshot_owner_child_id"

private fun skippedRecordWarning(skippedRecords: Int): String? =
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
