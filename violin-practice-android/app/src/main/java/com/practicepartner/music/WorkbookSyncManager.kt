package com.practicepartner.music

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

interface WorkbookStorage {
    fun describe(uriText: String?): LocalWorkbookFile?

    suspend fun write(
        uriText: String,
        profile: StudentProfile,
        snapshot: PracticeSnapshot,
        updatedAtMillis: Long,
        requireExistingTemplate: Boolean = false,
    ): LocalWorkbookSave

    suspend fun readAssignments(uriText: String): WorkbookAssignmentImportResult
}

interface WorkbookSyncSettings {
    fun loadLocalRevision(childProfileId: String): Long
    fun bumpLocalRevision(childProfileId: String): Long
    fun loadLastFileSaveRevision(childProfileId: String): Long
    fun saveLastFileSaveRevision(childProfileId: String, revision: Long)
    fun loadLastFileSaveAt(childProfileId: String): Long?
    fun saveLastFileSaveAt(childProfileId: String, atMillis: Long)
    fun saveChildProfile(profile: ChildProfile): Household
}

class WorkbookSyncManager(
    private val scope: CoroutineScope,
    private val workbookStore: WorkbookStorage,
    private val settings: WorkbookSyncSettings,
    private val wallClock: () -> Long,
    private val profileProvider: () -> ChildProfile,
    private val snapshotProvider: () -> PracticeSnapshot,
    private val currentDateProvider: () -> String,
    private val activeTaskIdProvider: () -> String?,
    private val recordingActiveProvider: () -> Boolean,
    private val canReadContentUri: (String?) -> Boolean,
    private val persistSnapshot: (PracticeSnapshot) -> Unit,
    private val updateSnapshot: (
        saveWorkbook: Boolean,
        transform: (PracticeSnapshot) -> PracticeSnapshot,
    ) -> Unit,
    private val updateProfile: (ChildProfile, Household) -> Unit,
    private val clearSelectedTask: (String) -> Unit,
    private val setMessage: (String) -> Unit,
    private val waitForAutoSave: suspend () -> Unit = {
        delay(AUTO_SAVE_DELAY_MILLIS)
    },
) {
    var state by mutableStateOf(
        LocalFileUiState(
            status = LocalFileStatus.NOT_CONFIGURED,
            detail = "Create or choose a local Excel file for assignment updates.",
        ),
    )
        private set

    private var localRevision = 0L
    private var lastFileSaveRevision = -1L
    private var autoSaveJob: Job? = null

    fun activate() {
        autoSaveJob?.cancel()
        val profile = profileProvider()
        localRevision = settings.loadLocalRevision(profile.id)
        lastFileSaveRevision = settings.loadLastFileSaveRevision(profile.id)
        state = initialLocalFileState(
            profile = profile,
            lastSavedAtMillis = settings.loadLastFileSaveAt(profile.id),
            file = workbookStore.describe(profile.excelWorkbookUri),
            localRevision = localRevision,
            lastFileSaveRevision = lastFileSaveRevision,
        )
        if (state.status == LocalFileStatus.UNSAVED) {
            scheduleAutomaticFileSave()
        }
    }

    fun close() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    fun suggestedExcelFileName(): String =
        PracticeWorkbookTemplate.fileName(profileProvider())

    fun createLocalWorkbook(uriText: String) {
        val uri = validWorkbookUri(uriText) ?: return
        writeLocalWorkbook(uri, LocalWorkbookWriteMode.CREATE)
    }

    fun connectExistingLocalWorkbook(uriText: String) {
        val uri = validWorkbookUri(uriText) ?: return
        if (state.status == LocalFileStatus.SAVING) return
        autoSaveJob?.cancel()
        state = state.copy(
            status = LocalFileStatus.SAVING,
            detail = "Checking the local file and reading its assignments…",
        )
        scope.launch {
            runCatching {
                workbookStore.readAssignments(uri)
            }.onSuccess { result ->
                val profile = profileProvider()
                val merge = mergeWorkbookAssignments(
                    assignments = result.assignments,
                    profile = profile,
                    snapshot = snapshotProvider(),
                    canReadContentUri = canReadContentUri,
                )
                val nextSnapshot = snapshotProvider().copy(tasks = merge.tasks)
                if (nextSnapshot != snapshotProvider()) {
                    persistSnapshot(nextSnapshot)
                }
                val file = workbookStore.describe(uri)
                val updated = profile.copy(
                    excelWorkbookUri = uri,
                    excelWorkbookName = file?.name ?: PracticeWorkbookTemplate.fileName(profile),
                )
                val household = settings.saveChildProfile(updated)
                updateProfile(updated, household)
                localRevision = settings.bumpLocalRevision(updated.id)
                state = LocalFileUiState(
                    status = LocalFileStatus.UNSAVED,
                    fileName = updated.excelWorkbookName,
                    fileUri = uri,
                    lastSavedAtMillis = settings.loadLastFileSaveAt(updated.id),
                    hasUnsavedChanges = true,
                    detail = "The combined assignment plan is ready to save to this local file.",
                )
                val warnings = result.errors + merge.errors
                val summary = buildString {
                    append("Selected the local file and loaded ${merge.loadedCount} assignment")
                    if (merge.loadedCount != 1) append('s')
                    append(". The combined plan was saved.")
                    if (warnings.isNotEmpty()) {
                        append(" Skipped or adjusted ${warnings.size}: ")
                        append(warnings.take(2).joinToString(" "))
                    }
                }
                writeLocalWorkbook(
                    uri = uri,
                    mode = LocalWorkbookWriteMode.CONNECT_EXISTING,
                    successMessage = summary,
                )
            }.onFailure { error ->
                state = state.copy(
                    status = LocalFileStatus.ERROR,
                    detail = error.message
                        ?: "The selected local Excel file could not be read.",
                )
                setMessage("Could not use that local Excel file.")
            }
        }
    }

    fun saveLocalWorkbook() {
        val uri = profileProvider().excelWorkbookUri
        if (uri.isNullOrBlank()) {
            setMessage("Create or choose a local Excel file first.")
            return
        }
        writeLocalWorkbook(uri, LocalWorkbookWriteMode.SAVE, automatic = false)
    }

    fun loadAssignmentsFromLocalFile() {
        val profile = profileProvider()
        val uri = profile.excelWorkbookUri
        if (uri.isNullOrBlank()) {
            setMessage("Create or choose a local Excel file first.")
            return
        }
        if (localRevision > lastFileSaveRevision) {
            setMessage(
                "Save app assignment or profile changes before loading Excel assignments.",
            )
            return
        }
        if (state.status == LocalFileStatus.SAVING) return
        autoSaveJob?.cancel()
        state = state.copy(
            status = LocalFileStatus.SAVING,
            detail = "Reading assignments from the local Excel file…",
        )
        scope.launch {
            runCatching {
                workbookStore.readAssignments(uri)
            }.onSuccess { result ->
                val merge = mergeWorkbookAssignments(
                    assignments = result.assignments,
                    profile = profileProvider(),
                    snapshot = snapshotProvider(),
                    canReadContentUri = canReadContentUri,
                )
                val assignmentsChanged = merge.tasks != snapshotProvider().tasks
                val needsIdWriteBack = merge.generatedIdCount > 0
                if (assignmentsChanged) {
                    updateSnapshot(needsIdWriteBack) {
                        it.copy(tasks = merge.tasks)
                    }
                }
                if (!needsIdWriteBack) {
                    state = state.copy(
                        status = LocalFileStatus.READY,
                        hasUnsavedChanges = false,
                        detail = localFileReadyDetail(),
                    )
                }
                val warnings = result.errors + merge.errors
                setMessage(
                    buildString {
                        append("Loaded ${merge.loadedCount} assignment")
                        if (merge.loadedCount != 1) append('s')
                        append(" from the local Excel file.")
                        if (merge.generatedIdCount > 0) {
                            append(" Added ${merge.generatedIdCount} missing assignment ID")
                            if (merge.generatedIdCount != 1) append('s')
                            append("; the app will save ")
                            append(if (merge.generatedIdCount == 1) "it" else "them")
                            append(" back to Excel.")
                        }
                        if (warnings.isNotEmpty()) {
                            append(" Skipped ${warnings.size}: ")
                            append(warnings.take(2).joinToString(" "))
                        }
                    },
                )
            }.onFailure { error ->
                state = state.copy(
                    status = LocalFileStatus.ERROR,
                    detail = error.message ?: "The local Excel file could not be read.",
                )
                setMessage("Could not load assignments from the local Excel file.")
            }
        }
    }

    fun saveAssignment(
        taskId: String?,
        assignedDateText: String,
        titleText: String,
        sourceText: String,
        startText: String,
        endText: String,
        repetitionsText: String,
        coachNote: String,
        whyImportant: String,
        sheetMusicUri: String?,
        assignmentType: AssignmentType,
        goalType: PracticeGoalType,
        targetMinutesText: String,
        enabledModes: Set<PracticeMode>,
    ): Boolean {
        val profile = profileProvider()
        val snapshot = snapshotProvider()
        val assignedDate = assignedDateText.trim()
        val title = titleText.trim()
        val source = sourceText.trim()
        val sourceType = sourceTypeFor(source)
        val start = startText.trim().ifBlank { "0" }.toIntOrNull()
        val end = endText.trim().takeIf(String::isNotBlank)?.toIntOrNull()
        val repetitions = repetitionsText.trim().toIntOrNull()
        val targetMinutes = targetMinutesText.trim().toIntOrNull()
        val parsedDate = runCatching { java.time.LocalDate.parse(assignedDate) }.getOrNull()
        val existingTask = taskId?.let { id -> snapshot.tasks.firstOrNull { it.id == id } }
        val error = when {
            parsedDate == null -> "Choose a valid assignment date."
            title.isBlank() -> "Add a task title."
            title.length > 120 -> "Keep the task title under 120 characters."
            sourceType == null -> "Use a valid YouTube link or choose a video from this device."
            sourceType == SourceType.LOCAL && !canReadContentUri(source) ->
                "The selected reference video is no longer available. Choose it again."
            start == null || start < 0 -> "Start time must be zero or greater."
            endText.isNotBlank() && end == null ->
                "End time must be a whole number of seconds."
            end != null && end <= start -> "End time must be after start time."
            goalType == PracticeGoalType.REPETITIONS &&
                (repetitions == null || repetitions !in 1..20) ->
                "Repetitions must be between 1 and 20."
            goalType == PracticeGoalType.TIME &&
                (targetMinutes == null || targetMinutes !in 1..60) ->
                "Practice time must be between 1 and 60 minutes."
            enabledModes.isEmpty() -> "Choose at least one practice step."
            sourceType == SourceType.NONE &&
                enabledModes != setOf(PracticeMode.PLAY_YOURSELF) ->
                "Without a reference video, use Play Yourself as the practice step."
            coachNote.length > 2_000 -> "Keep the coach focus under 2,000 characters."
            whyImportant.length > 2_000 ->
                "Keep why this matters under 2,000 characters."
            sheetMusicUri?.startsWith("content://") == true &&
                !canReadContentUri(sheetMusicUri) ->
                "The attached sheet music is no longer available. Choose it again."
            taskId != null && existingTask == null -> "This assignment no longer exists."
            existingTask != null &&
                existingTask.assignedDate != assignedDate &&
                hasProgressForTask(snapshot, existingTask.id) ->
                "An assignment with recorded progress cannot be moved to another date."
            else -> null
        }
        if (error != null) {
            setMessage(error)
            return false
        }

        var task = PracticeTask(
            id = existingTask?.id ?: UUID.randomUUID().toString(),
            childProfileId = profile.id,
            instrument = profile.instrument,
            assignedDate = assignedDate,
            title = title,
            source = source,
            sourceType = sourceType!!,
            startSeconds = start!!,
            endSeconds = end,
            repetitions = if (goalType == PracticeGoalType.REPETITIONS) {
                repetitions!!
            } else {
                1
            },
            coachNote = coachNote.trim(),
            whyImportant = whyImportant.trim(),
            sheetMusicUri = sheetMusicUri?.takeIf(String::isNotBlank),
            assignmentType = assignmentType,
            goalType = goalType,
            targetSeconds = if (goalType == PracticeGoalType.TIME) {
                targetMinutes!! * 60
            } else {
                0
            },
            enabledModes = enabledModes,
            revision = existingTask?.revision ?: 1,
            archivedAtMillis = existingTask?.archivedAtMillis,
        )
        if (existingTask != null && task != existingTask) {
            task = task.copy(revision = existingTask.revision + 1)
        }
        if (isDuplicateTask(snapshot, task, excludingTaskId = existingTask?.id)) {
            setMessage("This assignment is already on ${friendlyDate(assignedDate)}.")
            return false
        }
        updateSnapshot(true) { current ->
            val tasks = if (existingTask == null) {
                current.tasks + task
            } else {
                current.tasks.map { if (it.id == existingTask.id) task else it }
            }
            current.copy(tasks = tasks)
        }
        setMessage(
            "${if (existingTask == null) "Added" else "Updated"} “${task.title}” for " +
                "${friendlyDate(assignedDate)}.",
        )
        return true
    }

    fun addAssignment(
        titleText: String,
        sourceText: String,
        startText: String,
        endText: String,
        repetitionsText: String,
        coachNote: String,
        whyImportant: String,
        sheetMusicUri: String?,
        assignmentType: AssignmentType = AssignmentType.TECHNIQUE,
        goalType: PracticeGoalType = PracticeGoalType.REPETITIONS,
        targetMinutesText: String = "10",
        enabledModes: Set<PracticeMode> = setOf(PracticeMode.PLAY_YOURSELF),
    ): Boolean = saveAssignment(
        taskId = null,
        assignedDateText = currentDateProvider(),
        titleText = titleText,
        sourceText = sourceText,
        startText = startText,
        endText = endText,
        repetitionsText = repetitionsText,
        coachNote = coachNote,
        whyImportant = whyImportant,
        sheetMusicUri = sheetMusicUri,
        assignmentType = assignmentType,
        goalType = goalType,
        targetMinutesText = targetMinutesText,
        enabledModes = enabledModes,
    )

    fun importCsv(text: String, defaultDate: String = currentDateProvider()) {
        val profile = profileProvider()
        val result = CsvCodec.decode(
            text = text,
            defaultDate = defaultDate,
            childProfileId = profile.id,
            instrument = profile.instrument,
        )
        val snapshot = snapshotProvider()
        val merged = snapshot.tasks.toMutableList()
        val errors = result.errors.toMutableList()
        val seenImportedIds = mutableSetOf<String>()
        var acceptedCount = 0
        result.tasks.forEachIndexed { index, imported ->
            val task = if (
                imported.sheetMusicUri?.startsWith("content://") == true &&
                !canReadContentUri(imported.sheetMusicUri)
            ) {
                errors += "Imported row ${index + 2}: unavailable sheet music was removed."
                imported.copy(sheetMusicUri = null)
            } else {
                imported
            }
            val existingIndex = merged.indexOfFirst { it.id == task.id }
            val existing = merged.getOrNull(existingIndex)
            when {
                !seenImportedIds.add(task.id) ->
                    errors += "Imported row ${index + 2}: duplicate assignment ID."
                task.sourceType == SourceType.LOCAL && !canReadContentUri(task.source) ->
                    errors += "Imported row ${index + 2}: local reference video is unavailable."
                existing != null &&
                    existing.assignedDate != task.assignedDate &&
                    hasProgressForTask(snapshot, existing.id) ->
                    errors +=
                        "Imported row ${index + 2}: an assignment with progress cannot move dates."
                merged.any {
                    it.id != task.id && assignmentKey(it) == assignmentKey(task)
                } ->
                    errors += "Imported row ${index + 2}: duplicate assignment."
                else -> {
                    if (existingIndex >= 0) {
                        merged[existingIndex] = task
                    } else {
                        merged += task
                    }
                    acceptedCount += 1
                }
            }
        }
        if (merged != snapshot.tasks) {
            updateSnapshot(true) { current ->
                current.copy(tasks = merged)
            }
        }
        setMessage(
            buildString {
                append("Imported ${acceptedCount} assignment")
                if (acceptedCount != 1) append('s')
                append('.')
                if (errors.isNotEmpty()) {
                    append(" Skipped or adjusted ${errors.size}: ")
                    append(errors.take(2).joinToString(" "))
                }
            },
        )
    }

    fun exportCsvForDate(date: String): String = CsvCodec.encode(
        snapshotProvider().tasks.filter { it.assignedDate == date },
    )

    fun archiveTask(task: PracticeTask) {
        if (task.archivedAtMillis != null) return
        if (task.id == activeTaskIdProvider()) {
            setMessage("Finish the current practice mission before archiving it.")
            return
        }
        updateSnapshot(true) { snapshot ->
            snapshot.copy(
                tasks = snapshot.tasks.map {
                    if (it.id == task.id) {
                        it.copy(
                            archivedAtMillis = wallClock(),
                            revision = it.revision + 1,
                        )
                    } else {
                        it
                    }
                },
            )
        }
        setMessage("Archived “${task.title}”. Its practice history was kept.")
    }

    fun restoreTask(task: PracticeTask) {
        if (task.archivedAtMillis == null) return
        updateSnapshot(true) { snapshot ->
            snapshot.copy(
                tasks = snapshot.tasks.map {
                    if (it.id == task.id) {
                        it.copy(
                            archivedAtMillis = null,
                            revision = it.revision + 1,
                        )
                    } else {
                        it
                    }
                },
            )
        }
        setMessage("Restored “${task.title}” to the practice plan.")
    }

    fun removeTask(task: PracticeTask) {
        if (recordingActiveProvider() && task.id == activeTaskIdProvider()) {
            setMessage("Stop the recording before removing this assignment.")
            return
        }
        if (task.id == activeTaskIdProvider()) {
            setMessage("Finish the current practice mission before removing this assignment.")
            return
        }
        updateSnapshot(true) { snapshot ->
            snapshot.copy(
                tasks = snapshot.tasks.filterNot { it.id == task.id },
                progressByDate = snapshot.progressByDate.mapValues { (_, progress) ->
                    progress.withoutTask(task.id)
                },
            )
        }
        clearSelectedTask(task.id)
        setMessage("Removed “${task.title}” and its completion state.")
    }

    fun recordLocalMutation() {
        val profile = profileProvider()
        localRevision = settings.bumpLocalRevision(profile.id)
        val uri = profile.excelWorkbookUri
        if (uri.isNullOrBlank()) return
        state = state.copy(
            status = LocalFileStatus.UNSAVED,
            fileName = profile.excelWorkbookName,
            fileUri = uri,
            hasUnsavedChanges = true,
            detail = "Assignment or profile changes are waiting for the next local file save.",
        )
        scheduleAutomaticFileSave()
    }

    private fun validWorkbookUri(uriText: String): String? {
        val uri = uriText.trim()
        if (!uri.startsWith("content://")) {
            setMessage("Choose a local Excel file with the Android file picker.")
            return null
        }
        return uri
    }

    private fun writeLocalWorkbook(
        uri: String,
        mode: LocalWorkbookWriteMode,
        automatic: Boolean = false,
        successMessage: String? = null,
    ) {
        if (state.status == LocalFileStatus.SAVING) return
        autoSaveJob?.cancel()
        val profileToSave = profileProvider()
        val snapshotToSave = snapshotProvider()
        val requestedAt = wallClock()
        val requestedRevision = localRevision
        state = state.copy(
            status = LocalFileStatus.SAVING,
            hasUnsavedChanges = localRevision > lastFileSaveRevision,
            detail = when {
                automatic ->
                    "Automatically saving assignment changes to the local Excel file…"
                mode == LocalWorkbookWriteMode.CREATE ->
                    "Creating and verifying the local Excel file and all column headings…"
                mode == LocalWorkbookWriteMode.CONNECT_EXISTING ->
                    "Checking the selected LK Practice file…"
                else ->
                    "Saving assignments and the current snapshot to the local Excel file…"
            },
        )
        scope.launch {
            runCatching {
                workbookStore.write(
                    uriText = uri,
                    profile = profileToSave,
                    snapshot = snapshotToSave,
                    updatedAtMillis = requestedAt,
                    requireExistingTemplate = mode == LocalWorkbookWriteMode.CONNECT_EXISTING,
                )
            }.onSuccess { workbook ->
                val currentProfile = profileProvider()
                val updated = currentProfile.copy(
                    excelWorkbookUri = workbook.uri,
                    excelWorkbookName = workbook.name,
                )
                val household = settings.saveChildProfile(updated)
                settings.saveLastFileSaveAt(updated.id, workbook.savedAtMillis)
                settings.saveLastFileSaveRevision(updated.id, requestedRevision)
                lastFileSaveRevision = requestedRevision
                updateProfile(updated, household)
                val changedDuringSave = localRevision > requestedRevision
                state = LocalFileUiState(
                    status = if (changedDuringSave) {
                        LocalFileStatus.UNSAVED
                    } else {
                        LocalFileStatus.READY
                    },
                    fileName = workbook.name,
                    fileUri = workbook.uri,
                    lastSavedAtMillis = workbook.savedAtMillis,
                    hasUnsavedChanges = changedDuringSave,
                    detail = if (changedDuringSave) {
                        "New assignment or profile changes are waiting for the next local file save."
                    } else {
                        localFileReadyDetail()
                    },
                )
                if (!automatic) {
                    setMessage(
                        successMessage ?: when (mode) {
                            LocalWorkbookWriteMode.CREATE ->
                                "Created and verified the local LK Practice Excel file."
                            LocalWorkbookWriteMode.CONNECT_EXISTING ->
                                "Selected the local LK Practice Excel file."
                            LocalWorkbookWriteMode.SAVE ->
                                "Saved assignments to the local Excel file."
                        },
                    )
                }
                if (changedDuringSave) scheduleAutomaticFileSave()
            }.onFailure { error ->
                val profile = profileProvider()
                state = LocalFileUiState(
                    status = LocalFileStatus.ERROR,
                    fileName = profile.excelWorkbookName,
                    fileUri = profile.excelWorkbookUri,
                    lastSavedAtMillis = settings.loadLastFileSaveAt(profile.id),
                    hasUnsavedChanges = localRevision > lastFileSaveRevision,
                    detail = error.message
                        ?: "The local Excel file is configured, but the save did not finish.",
                )
                setMessage(
                    if (automatic) {
                        "Automatic local file save needs attention. Open Profile to retry."
                    } else {
                        "Local Excel save failed. Reopen Profile to retry or choose another file."
                    },
                )
            }
        }
    }

    private fun scheduleAutomaticFileSave() {
        val uri = profileProvider().excelWorkbookUri ?: return
        if (state.status != LocalFileStatus.UNSAVED) return
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            waitForAutoSave()
            if (state.status == LocalFileStatus.UNSAVED) {
                writeLocalWorkbook(uri, LocalWorkbookWriteMode.SAVE, automatic = true)
            }
        }
    }
}

internal fun mergeWorkbookAssignments(
    assignments: List<WorkbookAssignment>,
    profile: ChildProfile,
    snapshot: PracticeSnapshot,
    canReadContentUri: (String?) -> Boolean,
): WorkbookAssignmentMerge {
    val merged = snapshot.tasks.toMutableList()
    val errors = mutableListOf<String>()
    var loadedCount = 0
    var generatedIdCount = 0
    assignments.forEachIndexed { index, assignment ->
        if (
            assignment.childProfileId.isNotBlank() &&
            assignment.childProfileId != profile.id
        ) {
            errors += "Excel row ${index + 2}: this assignment belongs to another student."
            return@forEachIndexed
        }
        val existingIndex = merged.indexOfFirst { it.id == assignment.id }
        val existing = merged.getOrNull(existingIndex)
        val source = when {
            assignment.sourceType == SourceType.LOCAL &&
                existing?.sourceType == SourceType.LOCAL &&
                canReadContentUri(existing.source) -> existing.source
            assignment.sourceType == SourceType.LOCAL -> {
                errors +=
                    "Excel row ${index + 2}: local reference needs reattachment on this device."
                ""
            }
            else -> assignment.source
        }
        val sheetMusic = when {
            assignment.sheetMusicUri != null -> assignment.sheetMusicUri
            existing?.sheetMusicUri?.startsWith("content://") == true ->
                existing.sheetMusicUri
            else -> null
        }
        if (assignment.needsSheetMusicReattachment && sheetMusic == null) {
            errors +=
                "Excel row ${index + 2}: local sheet music needs reattachment on this device."
        }
        var candidate = PracticeTask(
            id = assignment.id,
            childProfileId = profile.id,
            instrument = assignment.instrument.ifBlank { profile.instrument },
            assignedDate = assignment.assignedDate,
            title = assignment.title,
            source = source,
            sourceType = assignment.sourceType,
            startSeconds = assignment.startSeconds,
            endSeconds = assignment.endSeconds,
            repetitions = assignment.repetitions,
            coachNote = assignment.coachNote,
            whyImportant = assignment.whyImportant,
            sheetMusicUri = sheetMusic,
            assignmentType = assignment.assignmentType,
            goalType = assignment.goalType,
            targetSeconds = assignment.targetSeconds,
            enabledModes = assignment.enabledModes,
            revision = assignment.revision,
            archivedAtMillis = assignment.archivedAtMillis,
        )
        if (existing != null) {
            val candidateAtCurrentRevision = candidate.copy(revision = existing.revision)
            candidate = if (candidateAtCurrentRevision == existing) {
                existing
            } else {
                candidate.copy(
                    revision = maxOf(existing.revision + 1, assignment.revision),
                )
            }
        }
        val duplicate = merged.any {
            it.id != candidate.id && assignmentKey(it) == assignmentKey(candidate)
        }
        if (duplicate) {
            errors += "Excel row ${index + 2}: duplicate assignment."
            return@forEachIndexed
        }
        if (
            existing != null &&
            existing.assignedDate != candidate.assignedDate &&
            hasProgressForTask(snapshot, existing.id)
        ) {
            errors +=
                "Excel row ${index + 2}: an assignment with progress cannot move dates."
            return@forEachIndexed
        }
        if (existingIndex >= 0) {
            merged[existingIndex] = candidate
        } else {
            merged += candidate
        }
        loadedCount += 1
        if (assignment.generatedId) generatedIdCount += 1
    }
    return WorkbookAssignmentMerge(merged, loadedCount, generatedIdCount, errors)
}

private fun hasProgressForTask(snapshot: PracticeSnapshot, taskId: String): Boolean =
    snapshot.progressByDate.values.any { progress ->
        taskId in progress.completedTaskIds || progress.repetitionsFor(taskId) > 0
    } || snapshot.sessions.any { session ->
        session.assignmentId == taskId &&
            (session.elapsedSeconds > 0L || session.repetitionsCompleted > 0)
    }

private fun isDuplicateTask(
    snapshot: PracticeSnapshot,
    task: PracticeTask,
    excludingTaskId: String? = null,
): Boolean {
    val key = assignmentKey(task)
    return snapshot.tasks.any {
        it.id != excludingTaskId && assignmentKey(it) == key
    }
}

private fun initialLocalFileState(
    profile: StudentProfile,
    lastSavedAtMillis: Long?,
    file: LocalWorkbookFile?,
    localRevision: Long,
    lastFileSaveRevision: Long,
): LocalFileUiState =
    if (profile.excelWorkbookUri.isNullOrBlank()) {
        LocalFileUiState(
            status = LocalFileStatus.NOT_CONFIGURED,
            detail = "Create or choose a local Excel file for assignment updates.",
        )
    } else if (file != null) {
        val unsaved = localRevision > lastFileSaveRevision
        LocalFileUiState(
            status = if (unsaved) LocalFileStatus.UNSAVED else LocalFileStatus.READY,
            fileName = file.name,
            fileUri = file.uri,
            lastSavedAtMillis = lastSavedAtMillis,
            hasUnsavedChanges = unsaved,
            detail = if (unsaved) {
                "Assignment or profile changes are waiting for the next local file save."
            } else {
                localFileReadyDetail()
            },
        )
    } else {
        LocalFileUiState(
            status = LocalFileStatus.ERROR,
            fileName = profile.excelWorkbookName,
            fileUri = profile.excelWorkbookUri,
            lastSavedAtMillis = lastSavedAtMillis,
            hasUnsavedChanges = localRevision > lastFileSaveRevision,
            detail = "The app can no longer access this local Excel file. Choose it again.",
        )
    }

private fun localFileReadyDetail(): String =
    "App assignments save to this local file. Excel assignment edits load back on request. " +
        "Practice history and videos remain local-first."

internal data class WorkbookAssignmentMerge(
    val tasks: List<PracticeTask>,
    val loadedCount: Int,
    val generatedIdCount: Int,
    val errors: List<String>,
)

private enum class LocalWorkbookWriteMode {
    CREATE,
    CONNECT_EXISTING,
    SAVE,
}

private const val AUTO_SAVE_DELAY_MILLIS = 15_000L
