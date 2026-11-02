package com.practicepartner.music

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal fun Household.toEntity(): HouseholdEntity = HouseholdEntity(
    id = id,
    name = name,
    activeChildProfileId = activeChildProfileId,
)

internal fun ChildProfile.toEntity(
    householdId: String,
    sortOrder: Int,
): ChildProfileEntity = ChildProfileEntity(
    id = id,
    householdId = householdId,
    name = name,
    instrument = instrument,
    excelWorkbookUri = excelWorkbookUri,
    excelWorkbookName = excelWorkbookName,
    sortOrder = sortOrder,
)

internal fun ChildProfileEntity.toModel(): ChildProfile = ChildProfile(
    id = id,
    name = name,
    instrument = instrument,
    excelWorkbookUri = excelWorkbookUri,
    excelWorkbookName = excelWorkbookName,
)

internal fun householdFromEntities(
    household: HouseholdEntity,
    profiles: List<ChildProfileEntity>,
): Household = Household(
    id = household.id,
    name = household.name,
    childProfiles = profiles.map(ChildProfileEntity::toModel),
    activeChildProfileId = household.activeChildProfileId,
)

internal fun PracticeTask.toEntity(sortOrder: Int): AssignmentEntity = AssignmentEntity(
    id = id,
    childProfileId = childProfileId,
    assignedDate = assignedDate.value,
    title = title,
    source = source,
    sourceType = sourceType.name,
    startSeconds = startSeconds,
    endSeconds = endSeconds,
    repetitions = repetitions,
    coachNote = coachNote,
    whyImportant = whyImportant,
    sheetMusicUri = sheetMusicUri,
    teacherId = teacherId,
    instrument = instrument,
    assignmentType = assignmentType.name,
    goalType = goalType.name,
    targetSeconds = targetSeconds,
    enabledModesJson = storeJson.encodeToString(enabledModes),
    revision = revision,
    archivedAtMillis = archivedAtMillis,
    sortOrder = sortOrder,
)

internal fun AssignmentEntity.toModel(): PracticeTask = PracticeTask(
    id = id,
    childProfileId = childProfileId,
    assignedDate = DateKey.parse(assignedDate),
    title = title,
    source = source,
    sourceType = enumValueOrDefault(sourceType, SourceType.NONE),
    startSeconds = startSeconds,
    endSeconds = endSeconds,
    repetitions = repetitions,
    coachNote = coachNote,
    whyImportant = whyImportant,
    sheetMusicUri = sheetMusicUri,
    teacherId = teacherId,
    instrument = instrument,
    assignmentType = enumValueOrDefault(assignmentType, AssignmentType.TECHNIQUE),
    goalType = enumValueOrDefault(goalType, PracticeGoalType.REPETITIONS),
    targetSeconds = targetSeconds,
    enabledModes = decodeOrDefault(
        enabledModesJson,
        setOf(PracticeMode.PLAY_YOURSELF),
    ),
    revision = revision,
    archivedAtMillis = archivedAtMillis,
)

internal fun AssignmentHistory.toEntity(): AssignmentHistoryEntity =
    AssignmentHistoryEntity(
        assignmentId = assignmentId,
        revision = revision,
        childProfileId = childProfileId,
        changedAtMillis = changedAtMillis,
        changedFieldsJson = storeJson.encodeToString(changedFields),
        previousAssignmentJson = storeJson.encodeToString(previousAssignment),
    )

internal fun AssignmentHistoryEntity.toModel(): AssignmentHistory {
    val assignment = storeJson.decodeFromString<Assignment>(previousAssignmentJson)
    return AssignmentHistory(
        assignmentId = assignmentId,
        revision = revision,
        childProfileId = childProfileId,
        changedAtMillis = changedAtMillis,
        changedFields = decodeOrDefault(changedFieldsJson, emptySet()),
        previousAssignment = assignment.copy(
            id = assignmentId,
            childProfileId = childProfileId,
            revision = revision,
        ),
    )
}

internal fun DailyProgress.toEntity(): DailyProgressEntity = DailyProgressEntity(
    childProfileId = childProfileId,
    date = date.value,
    completedTaskIdsJson = storeJson.encodeToString(completedTaskIds),
    repetitionsByTaskJson = storeJson.encodeToString(repetitionsByTask),
    elapsedSeconds = elapsedSeconds,
)

internal fun DailyProgressEntity.toModel(): DailyProgress = DailyProgress(
    childProfileId = childProfileId,
    date = DateKey.parse(date),
    completedTaskIds = decodeOrDefault(completedTaskIdsJson, emptySet()),
    repetitionsByTask = decodeOrDefault(repetitionsByTaskJson, emptyMap()),
    elapsedSeconds = elapsedSeconds,
)

internal fun PracticeEvent.toEntity(): PracticeEventEntity = PracticeEventEntity(
    id = id,
    childProfileId = childProfileId,
    date = date.value,
    taskId = taskId,
    taskTitle = taskTitle,
    event = event,
    atMillis = atMillis,
    videoUri = videoUri,
    videoPath = videoPath,
    videoFileName = videoFileName,
    sessionId = sessionId,
    sortOrder = atMillis,
)

internal fun PracticeEventEntity.toModel(): PracticeEvent = PracticeEvent(
    id = id,
    childProfileId = childProfileId,
    date = DateKey.parse(date),
    taskId = taskId,
    taskTitle = taskTitle,
    event = event,
    atMillis = atMillis,
    videoUri = videoUri,
    videoPath = videoPath,
    videoFileName = videoFileName,
    sessionId = sessionId,
)

internal fun PracticeSession.toEntity(): PracticeSessionEntity = PracticeSessionEntity(
    id = id,
    childProfileId = childProfileId,
    assignmentId = assignmentId,
    assignmentTitle = assignmentTitle,
    instrument = instrument,
    date = date.value,
    mode = mode.name,
    modesUsedJson = storeJson.encodeToString(resolvedModesUsed()),
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    repetitionsCompleted = repetitionsCompleted,
    status = status.name,
    assignmentRevision = assignmentRevision,
    assignmentType = assignmentType.name,
    goalType = goalType.name,
    targetRepetitions = targetRepetitions,
    targetSeconds = targetSeconds,
    elapsedSeconds = elapsedSeconds,
    reflection = reflection?.name,
    masteryStatus = masteryStatus.name,
    sortOrder = startedAtMillis,
)

internal fun PracticeSessionEntity.toModel(): PracticeSession {
    val resolvedMode = enumValueOrDefault(mode, PracticeMode.PLAY_YOURSELF)
    return PracticeSession(
        id = id,
        childProfileId = childProfileId,
        assignmentId = assignmentId,
        assignmentTitle = assignmentTitle,
        instrument = instrument,
        date = DateKey.parse(date),
        mode = resolvedMode,
        modesUsed = decodeOrDefault(modesUsedJson, setOf(resolvedMode)) + resolvedMode,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        repetitionsCompleted = repetitionsCompleted,
        status = enumValueOrDefault(status, PracticeSessionStatus.INTERRUPTED),
        assignmentRevision = assignmentRevision,
        assignmentType = enumValueOrDefault(assignmentType, AssignmentType.TECHNIQUE),
        goalType = enumValueOrDefault(goalType, PracticeGoalType.REPETITIONS),
        targetRepetitions = targetRepetitions,
        targetSeconds = targetSeconds,
        elapsedSeconds = elapsedSeconds,
        reflection = reflection?.let {
            enumValueOrNull<PracticeReflection>(it)
        },
        masteryStatus = enumValueOrDefault(masteryStatus, MasteryStatus.NOT_ASSESSED),
    )
}

internal data class PracticePersistenceChangeSet(
    val assignmentsToUpsert: List<AssignmentEntity> = emptyList(),
    val assignmentIdsToDelete: List<String> = emptyList(),
    val assignmentHistoryToUpsert: List<AssignmentHistoryEntity> = emptyList(),
    val assignmentHistoryToDelete: List<AssignmentHistoryEntity> = emptyList(),
    val progressToUpsert: List<DailyProgressEntity> = emptyList(),
    val progressDatesToDelete: List<String> = emptyList(),
    val eventsToUpsert: List<PracticeEventEntity> = emptyList(),
    val eventIdsToDelete: List<String> = emptyList(),
    val sessionsToUpsert: List<PracticeSessionEntity> = emptyList(),
    val sessionIdsToDelete: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = assignmentsToUpsert.isEmpty() &&
            assignmentIdsToDelete.isEmpty() &&
            assignmentHistoryToUpsert.isEmpty() &&
            assignmentHistoryToDelete.isEmpty() &&
            progressToUpsert.isEmpty() &&
            progressDatesToDelete.isEmpty() &&
            eventsToUpsert.isEmpty() &&
            eventIdsToDelete.isEmpty() &&
            sessionsToUpsert.isEmpty() &&
            sessionIdsToDelete.isEmpty()
}

internal fun practicePersistenceChanges(
    previous: PracticeSnapshot,
    next: PracticeSnapshot,
): PracticePersistenceChangeSet {
    val previousAssignments = previous.tasks.associateBy(PracticeTask::id)
    val previousAssignmentOrder = previous.tasks
        .mapIndexed { index, assignment -> assignment.id to index }
        .toMap()
    val nextAssignmentIds = next.tasks.mapTo(mutableSetOf(), PracticeTask::id)
    val assignmentsToUpsert = next.tasks.mapIndexedNotNull { index, assignment ->
        assignment.toEntity(index).takeIf {
            previousAssignments[assignment.id] != assignment ||
                previousAssignmentOrder[assignment.id] != index
        }
    }

    val previousHistory = previous.assignmentHistory.associateBy {
        it.assignmentId to it.revision
    }
    val nextHistory = next.assignmentHistory.associateBy {
        it.assignmentId to it.revision
    }
    val assignmentHistoryToUpsert = nextHistory
        .filter { (key, value) -> previousHistory[key] != value }
        .values
        .map(AssignmentHistory::toEntity)
    val assignmentHistoryToDelete = previousHistory
        .filterKeys { it !in nextHistory }
        .values
        .map(AssignmentHistory::toEntity)

    val previousProgress = previous.progressByDate
    val nextProgressDates = next.progressByDate.keys
    val progressToUpsert = next.progressByDate.values
        .filter { previousProgress[it.date] != it }
        .map(DailyProgress::toEntity)

    val previousEvents = previous.events.associateBy(PracticeEvent::id)
    val nextEventIds = next.events.mapTo(mutableSetOf(), PracticeEvent::id)
    val eventsToUpsert = next.events
        .filter { previousEvents[it.id] != it }
        .map(PracticeEvent::toEntity)

    val previousSessions = previous.sessions.associateBy(PracticeSession::id)
    val nextSessionIds = next.sessions.mapTo(mutableSetOf(), PracticeSession::id)
    val sessionsToUpsert = next.sessions
        .filter { previousSessions[it.id] != it }
        .map(PracticeSession::toEntity)

    return PracticePersistenceChangeSet(
        assignmentsToUpsert = assignmentsToUpsert,
        assignmentIdsToDelete = previousAssignments.keys
            .filterNot(nextAssignmentIds::contains),
        assignmentHistoryToUpsert = assignmentHistoryToUpsert,
        assignmentHistoryToDelete = assignmentHistoryToDelete,
        progressToUpsert = progressToUpsert,
        progressDatesToDelete = previousProgress.keys
            .filterNot(nextProgressDates::contains)
            .map(DateKey::value),
        eventsToUpsert = eventsToUpsert,
        eventIdsToDelete = previousEvents.keys.filterNot(nextEventIds::contains),
        sessionsToUpsert = sessionsToUpsert,
        sessionIdsToDelete = previousSessions.keys.filterNot(nextSessionIds::contains),
    )
}

internal fun practiceSnapshotFromEntities(
    assignments: List<AssignmentEntity>,
    assignmentHistory: List<AssignmentHistoryEntity>,
    progress: List<DailyProgressEntity>,
    events: List<PracticeEventEntity>,
    sessions: List<PracticeSessionEntity>,
): PracticeSnapshot = PracticeSnapshot(
    tasks = assignments.map(AssignmentEntity::toModel),
    assignmentHistory = assignmentHistory.map(AssignmentHistoryEntity::toModel),
    progressByDate = progress.map(DailyProgressEntity::toModel)
        .associateBy(DailyProgress::date),
    events = events.map(PracticeEventEntity::toModel),
    sessions = sessions.map(PracticeSessionEntity::toModel),
)

private inline fun <reified T> decodeOrDefault(
    value: String,
    default: T,
): T = runCatching {
    storeJson.decodeFromString<T>(value)
}.getOrDefault(default)

private inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String,
    default: T,
): T = enumValueOrNull<T>(value) ?: default

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }
