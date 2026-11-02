package com.lkeducation.lkpractice

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

internal object AssignmentSerializer : KSerializer<Assignment> {
    override val descriptor: SerialDescriptor = AssignmentWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Assignment) {
        encoder.encodeSerializableValue(AssignmentWire.serializer(), value.toWire())
    }

    override fun deserialize(decoder: Decoder): Assignment {
        if (decoder !is JsonDecoder) {
            return decoder.decodeSerializableValue(AssignmentWire.serializer()).toModel()
        }
        val raw = decoder.decodeJsonElement().jsonObject
        val legacyDate = raw["assignedDate"]
        val normalized = JsonObject(
            buildMap {
                putAll(raw)
                if ("startDate" !in raw && legacyDate != null) {
                    put("startDate", legacyDate)
                }
                if ("endDate" !in raw && legacyDate != null) {
                    put("endDate", legacyDate)
                }
                remove("assignedDate")
            },
        )
        return decoder.json
            .decodeFromJsonElement(AssignmentWire.serializer(), normalized)
            .toModel()
    }
}

@Serializable
private data class AssignmentWire(
    val id: String,
    val startDate: DateKey,
    val endDate: DateKey = startDate,
    val title: String,
    val source: String,
    val sourceType: SourceType,
    val startSeconds: Int,
    val endSeconds: Int?,
    val repetitions: Int,
    val coachNote: String = "",
    val whyImportant: String = "",
    val sheetMusicUri: String? = null,
    val childProfileId: String = "",
    val teacherId: String? = null,
    val instrument: String = "",
    val assignmentType: AssignmentType = AssignmentType.TECHNIQUE,
    val goalType: PracticeGoalType = PracticeGoalType.REPETITIONS,
    val targetSeconds: Int = 0,
    val enabledModes: Set<PracticeMode> = setOf(PracticeMode.PLAY_YOURSELF),
    val revision: Int = 1,
    val archivedAtMillis: Long? = null,
)

private fun Assignment.toWire(): AssignmentWire = AssignmentWire(
    id = id,
    startDate = startDate,
    endDate = endDate,
    title = title,
    source = source,
    sourceType = sourceType,
    startSeconds = startSeconds,
    endSeconds = endSeconds,
    repetitions = repetitions,
    coachNote = coachNote,
    whyImportant = whyImportant,
    sheetMusicUri = sheetMusicUri,
    childProfileId = childProfileId,
    teacherId = teacherId,
    instrument = instrument,
    assignmentType = assignmentType,
    goalType = goalType,
    targetSeconds = targetSeconds,
    enabledModes = enabledModes,
    revision = revision,
    archivedAtMillis = archivedAtMillis,
)

private fun AssignmentWire.toModel(): Assignment = Assignment(
    id = id,
    startDate = startDate,
    endDate = endDate,
    title = title,
    source = source,
    sourceType = sourceType,
    startSeconds = startSeconds,
    endSeconds = endSeconds,
    repetitions = repetitions,
    coachNote = coachNote,
    whyImportant = whyImportant,
    sheetMusicUri = sheetMusicUri,
    childProfileId = childProfileId,
    teacherId = teacherId,
    instrument = instrument,
    assignmentType = assignmentType,
    goalType = goalType,
    targetSeconds = targetSeconds,
    enabledModes = enabledModes,
    revision = revision,
    archivedAtMillis = archivedAtMillis,
)
