package com.practicepartner.music

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.UUID

internal val storeJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
    coerceInputValues = true
}

internal object PracticeSnapshotJsonCodec {
    fun encode(snapshot: PracticeSnapshot): String =
        storeJson.encodeToString(
            StoredPracticeSnapshot(
                tasks = snapshot.tasks,
                progress = snapshot.progressByDate.values.sortedBy(DailyProgress::date),
                events = snapshot.events.takeLast(MAX_STORED_RECORDS),
                sessions = snapshot.sessions.takeLast(MAX_STORED_RECORDS),
            ),
        )

    fun decode(
        value: String,
        profile: ChildProfile,
    ): DecodedPracticeSnapshot? = runCatching {
        val stored = storeJson.decodeFromString<StoredPracticeSnapshotRows>(value)
        val tasks = decodeRows<PracticeTask>(stored.tasks) { task, row ->
            val storedModes = row.jsonObject["enabledModes"]
            task.copy(
                id = task.id.trim().also { require(it.isNotBlank()) },
                assignedDate = task.assignedDate.ifBlank { currentDateKey() },
                title = task.title.trim().also { require(it.isNotBlank()) },
                source = task.source.trim(),
                startSeconds = task.startSeconds.coerceAtLeast(0),
                repetitions = task.repetitions.coerceIn(1, 20),
                targetSeconds = task.targetSeconds.coerceAtLeast(0),
                enabledModes = task.enabledModes.takeIf {
                    storedModes is JsonArray && it.isNotEmpty()
                } ?: run {
                    if (task.sourceType == SourceType.NONE) {
                        setOf(PracticeMode.PLAY_YOURSELF)
                    } else {
                        PracticeMode.entries.toSet()
                    }
                },
                revision = task.revision.coerceAtLeast(1),
            )
        }
        val progress = decodeRows<DailyProgress>(stored.progress) { item, _ ->
            item.copy(
                date = item.date.also { require(it.isNotBlank()) },
                repetitionsByTask = item.repetitionsByTask.mapValues { (_, count) ->
                    count.coerceAtLeast(0)
                },
                elapsedSeconds = item.elapsedSeconds.coerceAtLeast(0L),
            )
        }
        val events = decodeRows<PracticeEvent>(stored.events) { event, _ ->
            event.copy(
                id = event.id.ifBlank { UUID.randomUUID().toString() },
                date = event.date.ifBlank { dateKeyForMillis(event.atMillis) },
                videoFileName = event.videoFileName
                    ?: recordingFileNameFromPath(event.videoPath),
            )
        }
        val sessions = decodeRows<PracticeSession>(stored.sessions) { session, _ ->
            session.copy(
                id = session.id.also { require(it.isNotBlank()) },
                assignmentId = session.assignmentId.also { require(it.isNotBlank()) },
                date = session.date.also { require(it.isNotBlank()) },
                repetitionsCompleted = session.repetitionsCompleted.coerceAtLeast(0),
                assignmentRevision = session.assignmentRevision.coerceAtLeast(1),
                targetRepetitions = session.targetRepetitions.coerceAtLeast(1),
                targetSeconds = session.targetSeconds.coerceAtLeast(0),
                elapsedSeconds = session.elapsedSeconds.coerceAtLeast(0L),
            )
        }
        DecodedPracticeSnapshot(
            snapshot = PracticeSnapshot(
                tasks = tasks.values,
                progressByDate = progress.values.associateBy(DailyProgress::date),
                events = events.values.takeLast(MAX_STORED_RECORDS),
                sessions = sessions.values.takeLast(MAX_STORED_RECORDS),
            ).scopedTo(profile),
            skippedRecords =
                tasks.skipped + progress.skipped + events.skipped + sessions.skipped,
        )
    }.getOrNull()
}

internal object HouseholdJsonCodec {
    fun encode(household: Household): String =
        storeJson.encodeToString(
            StoredHousehold(
                id = household.id,
                name = household.name,
                activeChildProfileId = household.activeChildProfileId,
                childProfiles = household.childProfiles,
            ),
        )

    fun decode(value: String): Household? = runCatching {
        val stored = storeJson.decodeFromString<StoredHouseholdRows>(value)
        val profiles = stored.childProfiles.mapNotNull { row ->
            runCatching {
                storeJson.decodeFromJsonElement<ChildProfile>(row)
            }.getOrNull()?.takeIf { profile ->
                profile.id.isNotBlank() && profile.name.isNotBlank()
            }
        }.distinctBy(ChildProfile::id)
        require(profiles.isNotEmpty())
        Household(
            id = stored.id.ifBlank { UUID.randomUUID().toString() },
            name = stored.name.ifBlank { "Our Family" },
            childProfiles = profiles,
            activeChildProfileId = stored.activeChildProfileId,
        )
    }.getOrNull()
}

@Serializable
private data class StoredPracticeSnapshot(
    val version: Int = PRACTICE_SNAPSHOT_VERSION,
    val tasks: List<PracticeTask>,
    @SerialName("progress")
    val progress: List<DailyProgress>,
    val events: List<PracticeEvent>,
    val sessions: List<PracticeSession> = emptyList(),
)

@Serializable
private data class StoredPracticeSnapshotRows(
    val version: Int = PRACTICE_SNAPSHOT_VERSION,
    val tasks: List<JsonElement>,
    @SerialName("progress")
    val progress: List<JsonElement>,
    val events: List<JsonElement>,
    val sessions: List<JsonElement> = emptyList(),
)

@Serializable
private data class StoredHousehold(
    val version: Int = HOUSEHOLD_VERSION,
    val id: String,
    val name: String,
    val activeChildProfileId: String,
    val childProfiles: List<ChildProfile>,
)

@Serializable
private data class StoredHouseholdRows(
    val version: Int = HOUSEHOLD_VERSION,
    val id: String = "",
    val name: String = "Our Family",
    val activeChildProfileId: String = "",
    val childProfiles: List<JsonElement>,
)

private data class DecodedRows<T>(
    val values: List<T>,
    val skipped: Int,
)

private inline fun <reified T> decodeRows(
    rows: List<JsonElement>,
    normalize: (T, JsonElement) -> T,
): DecodedRows<T> {
    var skipped = 0
    val values = rows.mapNotNull { row ->
        runCatching {
            normalize(storeJson.decodeFromJsonElement<T>(row), row)
        }.getOrElse {
            skipped += 1
            null
        }
    }
    return DecodedRows(values, skipped)
}

internal data class DecodedPracticeSnapshot(
    val snapshot: PracticeSnapshot,
    val skippedRecords: Int,
)

private const val PRACTICE_SNAPSHOT_VERSION = 7
private const val HOUSEHOLD_VERSION = 1
private const val MAX_STORED_RECORDS = 500
