package com.practicepartner.music

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class PracticeViewModel(
    private val appContext: Context,
    private val store: PracticeStore,
    private val localWorkbookStore: LocalWorkbookStore,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val initialDate = currentDateKey()
    private val initialLoad = store.loadWithStatus(initialDate)
    private val initialSnapshot = initialLoad.snapshot
    private var localRevision = store.loadLocalRevision()
    private var lastFileSaveRevision = store.loadLastFileSaveRevision()

    var studentProfile by mutableStateOf(store.loadProfile())
        private set

    var localFileState by mutableStateOf(
        initialLocalFileState(
            profile = studentProfile,
            lastSavedAtMillis = store.loadLastFileSaveAt(),
            file = localWorkbookStore.describe(studentProfile.excelWorkbookUri),
            localRevision = localRevision,
            lastFileSaveRevision = lastFileSaveRevision,
        ),
    )
        private set

    var uiState by mutableStateOf(
        PracticeUiState(
            snapshot = initialSnapshot,
            currentDate = initialDate,
            elapsedSeconds = initialSnapshot.progressByDate[initialDate]?.elapsedSeconds ?: 0L,
            message = initialLoad.warning.orEmpty(),
        ),
    )
        private set

    private var timerBaseSeconds = uiState.elapsedSeconds
    private var timerStartedAtMillis: Long? = null
    private var timerJob: Job? = null
    private var autoSaveJob: Job? = null

    init {
        cleanupOrphanedRecordings()
        if (localFileState.status == LocalFileStatus.UNSAVED) {
            scheduleAutomaticFileSave()
        }
    }

    fun setPage(page: AppPage) {
        if (uiState.activeTaskId == null) uiState = uiState.copy(page = page)
    }

    fun showPreviousSummaryDate() {
        val dates = uiState.historyDates
        val previous = dates.lastOrNull { it < uiState.summaryDate } ?: return
        uiState = uiState.copy(summaryDate = previous)
    }

    fun showNextSummaryDate() {
        val dates = uiState.historyDates
        val next = dates.firstOrNull { it > uiState.summaryDate } ?: return
        uiState = uiState.copy(summaryDate = next)
    }

    fun showTodaySummary() {
        uiState = uiState.copy(summaryDate = uiState.currentDate)
    }

    fun setMode(mode: PracticeMode) {
        uiState = uiState.copy(mode = mode)
    }

    fun setMessage(message: String) {
        uiState = uiState.copy(message = message)
    }

    fun clearMessage() {
        if (uiState.message.isNotBlank()) uiState = uiState.copy(message = "")
    }

    fun saveProfile(
        nameText: String,
        instrumentText: String,
    ): Boolean {
        val name = nameText.trim()
        val instrument = instrumentText.trim()
        val error = when {
            name.isBlank() -> "Add the student’s name."
            name.length > 80 -> "Keep the student’s name under 80 characters."
            instrument.isBlank() -> "Add an instrument."
            instrument.length > 80 -> "Keep the instrument under 80 characters."
            else -> null
        }
        if (error != null) {
            setMessage(error)
            return false
        }
        val updated = studentProfile.copy(
            name = name,
            instrument = instrument,
        )
        val changed = updated != studentProfile
        store.saveProfile(updated)
        studentProfile = updated
        if (changed) recordLocalMutation()
        setMessage("Profile saved.")
        return true
    }

    fun suggestedExcelFileName(): String =
        PracticeWorkbookTemplate.fileName(studentProfile)

    fun createLocalWorkbook(uriText: String) {
        val uri = validWorkbookUri(uriText) ?: return
        writeLocalWorkbook(uri, LocalWorkbookWriteMode.CREATE)
    }

    fun connectExistingLocalWorkbook(uriText: String) {
        val uri = validWorkbookUri(uriText) ?: return
        if (localFileState.status == LocalFileStatus.SAVING) return
        autoSaveJob?.cancel()
        localFileState = localFileState.copy(
            status = LocalFileStatus.SAVING,
            detail = "Checking the local file and reading its assignments…",
        )
        viewModelScope.launch {
            runCatching {
                localWorkbookStore.readAssignments(uri)
            }.onSuccess { result ->
                val merge = mergeWorkbookAssignments(result.assignments)
                val nextSnapshot = uiState.snapshot.copy(tasks = merge.tasks)
                if (nextSnapshot != uiState.snapshot) {
                    store.save(nextSnapshot)
                    uiState = uiState.copy(snapshot = nextSnapshot)
                }
                val file = localWorkbookStore.describe(uri)
                val updated = studentProfile.copy(
                    excelWorkbookUri = uri,
                    excelWorkbookName = file?.name ?: PracticeWorkbookTemplate.fileName(
                        studentProfile,
                    ),
                )
                store.saveProfile(updated)
                studentProfile = updated
                localRevision = store.bumpLocalRevision()
                localFileState = LocalFileUiState(
                    status = LocalFileStatus.UNSAVED,
                    fileName = updated.excelWorkbookName,
                    fileUri = uri,
                    lastSavedAtMillis = store.loadLastFileSaveAt(),
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
                localFileState = localFileState.copy(
                    status = LocalFileStatus.ERROR,
                    detail = error.message ?: "The selected local Excel file could not be read.",
                )
                setMessage("Could not use that local Excel file.")
            }
        }
    }

    private fun validWorkbookUri(uriText: String): String? {
        val uri = uriText.trim()
        if (!uri.startsWith("content://")) {
            setMessage("Choose a local Excel file with the Android file picker.")
            return null
        }
        return uri
    }

    fun saveLocalWorkbook() {
        val uri = studentProfile.excelWorkbookUri
        if (uri.isNullOrBlank()) {
            setMessage("Create or choose a local Excel file first.")
            return
        }
        writeLocalWorkbook(uri, LocalWorkbookWriteMode.SAVE, automatic = false)
    }

    fun loadAssignmentsFromLocalFile() {
        val uri = studentProfile.excelWorkbookUri
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
        if (localFileState.status == LocalFileStatus.SAVING) return
        autoSaveJob?.cancel()
        localFileState = localFileState.copy(
            status = LocalFileStatus.SAVING,
            detail = "Reading assignments from the local Excel file…",
        )
        viewModelScope.launch {
            runCatching {
                localWorkbookStore.readAssignments(uri)
            }.onSuccess { result ->
                val merge = mergeWorkbookAssignments(result.assignments)
                val assignmentsChanged = merge.tasks != uiState.snapshot.tasks
                val needsIdWriteBack = merge.generatedIdCount > 0
                if (assignmentsChanged) {
                    updateSnapshot(saveWorkbook = needsIdWriteBack) {
                        it.copy(tasks = merge.tasks)
                    }
                }
                if (!needsIdWriteBack) {
                    localFileState = localFileState.copy(
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
                            append(
                                " Added ${merge.generatedIdCount} missing assignment ID",
                            )
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
                localFileState = localFileState.copy(
                    status = LocalFileStatus.ERROR,
                    detail = error.message ?: "The local Excel file could not be read.",
                )
                setMessage("Could not load assignments from the local Excel file.")
            }
        }
    }

    private fun writeLocalWorkbook(
        uri: String,
        mode: LocalWorkbookWriteMode,
        automatic: Boolean = false,
        successMessage: String? = null,
    ) {
        if (localFileState.status == LocalFileStatus.SAVING) return
        autoSaveJob?.cancel()
        val profileToSave = studentProfile
        val snapshotToSave = uiState.snapshot
        val requestedAt = wallClock()
        val requestedRevision = localRevision
        localFileState = localFileState.copy(
            status = LocalFileStatus.SAVING,
            hasUnsavedChanges = localRevision > lastFileSaveRevision,
            detail = when {
                automatic -> "Automatically saving assignment changes to the local Excel file…"
                mode == LocalWorkbookWriteMode.CREATE ->
                    "Creating and verifying the local Excel file and all column headings…"
                mode == LocalWorkbookWriteMode.CONNECT_EXISTING ->
                    "Checking the selected Practice Partner file…"
                else -> "Saving assignments and the current snapshot to the local Excel file…"
            },
        )
        viewModelScope.launch {
            runCatching {
                localWorkbookStore.write(
                    uriText = uri,
                    profile = profileToSave,
                    snapshot = snapshotToSave,
                    updatedAtMillis = requestedAt,
                    requireExistingTemplate = mode == LocalWorkbookWriteMode.CONNECT_EXISTING,
                )
            }.onSuccess { workbook ->
                val updated = studentProfile.copy(
                    excelWorkbookUri = workbook.uri,
                    excelWorkbookName = workbook.name,
                )
                store.saveProfile(updated)
                store.saveLastFileSaveAt(workbook.savedAtMillis)
                store.saveLastFileSaveRevision(requestedRevision)
                lastFileSaveRevision = requestedRevision
                studentProfile = updated
                val changedDuringSave = localRevision > requestedRevision
                localFileState = LocalFileUiState(
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
                                "Created and verified the local Practice Partner Excel file."
                            LocalWorkbookWriteMode.CONNECT_EXISTING ->
                                "Selected the local Practice Partner Excel file."
                            LocalWorkbookWriteMode.SAVE ->
                                "Saved assignments to the local Excel file."
                        },
                    )
                }
                if (changedDuringSave) scheduleAutomaticFileSave()
            }.onFailure { error ->
                localFileState = LocalFileUiState(
                    status = LocalFileStatus.ERROR,
                    fileName = studentProfile.excelWorkbookName,
                    fileUri = studentProfile.excelWorkbookUri,
                    lastSavedAtMillis = store.loadLastFileSaveAt(),
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

    fun refreshDate() {
        val today = currentDateKey()
        if (today == uiState.currentDate) return
        val wasShowingToday = uiState.summaryDate == uiState.currentDate
        pauseTimer(logEvent = false)
        val elapsed = uiState.snapshot.progressByDate[today]?.elapsedSeconds ?: 0L
        timerBaseSeconds = elapsed
        uiState = uiState.copy(
            currentDate = today,
            summaryDate = if (wasShowingToday) today else uiState.summaryDate,
            activeTaskId = null,
            selectedTaskId = null,
            elapsedSeconds = elapsed,
            running = false,
        )
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
    ): Boolean {
        val assignedDate = assignedDateText.trim()
        val title = titleText.trim()
        val source = sourceText.trim()
        val sourceType = sourceTypeFor(source)
        val start = startText.trim().ifBlank { "0" }.toIntOrNull()
        val end = endText.trim().takeIf(String::isNotBlank)?.toIntOrNull()
        val repetitions = repetitionsText.trim().toIntOrNull()
        val parsedDate = runCatching { java.time.LocalDate.parse(assignedDate) }.getOrNull()
        val existingTask = taskId?.let(::taskFor)
        val error = when {
            parsedDate == null -> "Choose a valid assignment date."
            title.isBlank() -> "Add a task title."
            title.length > 120 -> "Keep the task title under 120 characters."
            sourceType == null -> "Use a valid YouTube link or choose a video from this device."
            sourceType == SourceType.LOCAL && !canReadContentUri(source) ->
                "The selected reference video is no longer available. Choose it again."
            start == null || start < 0 -> "Start time must be zero or greater."
            endText.isNotBlank() && end == null -> "End time must be a whole number of seconds."
            end != null && end <= start -> "End time must be after start time."
            repetitions == null || repetitions !in 1..20 -> "Repetitions must be between 1 and 20."
            coachNote.length > 2_000 -> "Keep the coach focus under 2,000 characters."
            whyImportant.length > 2_000 -> "Keep why this matters under 2,000 characters."
            sheetMusicUri?.startsWith("content://") == true &&
                !canReadContentUri(sheetMusicUri) ->
                "The attached sheet music is no longer available. Choose it again."
            taskId != null && existingTask == null -> "This assignment no longer exists."
            existingTask != null &&
                existingTask.assignedDate != assignedDate &&
                hasProgressForTask(existingTask.id) ->
                "An assignment with recorded progress cannot be moved to another date."
            else -> null
        }
        if (error != null) {
            setMessage(error)
            return false
        }

        val task = PracticeTask(
            id = existingTask?.id ?: UUID.randomUUID().toString(),
            assignedDate = assignedDate,
            title = title,
            source = source,
            sourceType = sourceType!!,
            startSeconds = start!!,
            endSeconds = end,
            repetitions = repetitions!!,
            coachNote = coachNote.trim(),
            whyImportant = whyImportant.trim(),
            sheetMusicUri = sheetMusicUri?.takeIf(String::isNotBlank),
        )
        if (isDuplicateTask(task, excludingTaskId = existingTask?.id)) {
            setMessage("This assignment is already on ${friendlyDate(assignedDate)}.")
            return false
        }
        updateSnapshot(saveWorkbook = true) { snapshot ->
            val tasks = if (existingTask == null) {
                snapshot.tasks + task
            } else {
                snapshot.tasks.map { if (it.id == existingTask.id) task else it }
            }
            snapshot.copy(tasks = tasks)
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
    ): Boolean = saveAssignment(
        taskId = null,
        assignedDateText = uiState.currentDate,
        titleText = titleText,
        sourceText = sourceText,
        startText = startText,
        endText = endText,
        repetitionsText = repetitionsText,
        coachNote = coachNote,
        whyImportant = whyImportant,
        sheetMusicUri = sheetMusicUri,
    )

    fun importCsv(text: String, defaultDate: String = uiState.currentDate) {
        val result = CsvCodec.decode(text, defaultDate)
        val merged = uiState.snapshot.tasks.toMutableList()
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
                    hasProgressForTask(existing.id) ->
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
        if (merged != uiState.snapshot.tasks) {
            updateSnapshot(saveWorkbook = true) { snapshot ->
                snapshot.copy(tasks = merged)
            }
        }
        uiState = uiState.copy(
            message = buildString {
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

    fun exportTodayCsv(): String = CsvCodec.encode(uiState.todayTasks)

    fun exportCsvForDate(date: String): String = CsvCodec.encode(uiState.tasksForDate(date))

    fun removeTask(task: PracticeTask) {
        if (uiState.recordingActive && task.id == uiState.activeTaskId) {
            setMessage("Stop the recording before removing this assignment.")
            return
        }
        if (task.id == uiState.activeTaskId) finishPractice()
        updateSnapshot(saveWorkbook = true) { snapshot ->
            snapshot.copy(
                tasks = snapshot.tasks.filterNot { it.id == task.id },
                progressByDate = snapshot.progressByDate.mapValues { (_, progress) ->
                    progress.withoutTask(task.id)
                },
            )
        }
        uiState = uiState.copy(
            selectedTaskId = uiState.selectedTaskId.takeUnless { it == task.id },
            message = "Removed “${task.title}” and its completion state.",
        )
    }

    fun startTimer(eventMessage: String = "Practice timer started") {
        refreshDate()
        if (uiState.running) return
        timerBaseSeconds = uiState.todayProgress.elapsedSeconds
        timerStartedAtMillis = elapsedRealtime()
        uiState = uiState.copy(running = true, elapsedSeconds = timerBaseSeconds)
        logEvent(taskFor(uiState.selectedTaskId), eventMessage)
        startTicker()
    }

    fun pauseTimer(logEvent: Boolean = true) {
        if (!uiState.running) return
        val elapsed = calculatedElapsedSeconds()
        timerJob?.cancel()
        timerJob = null
        timerStartedAtMillis = null
        timerBaseSeconds = elapsed
        updateDailyProgress { it.copy(elapsedSeconds = elapsed) }
        uiState = uiState.copy(running = false, elapsedSeconds = elapsed)
        if (logEvent) logEvent(taskFor(uiState.selectedTaskId), "Practice timer paused")
    }

    fun startPractice(task: PracticeTask) {
        refreshDate()
        if (task.assignedDate != uiState.currentDate) {
            setMessage("This assignment belongs to ${friendlyDate(task.assignedDate)}, not today.")
            return
        }
        if (
            task.sourceType == SourceType.LOCAL &&
            (task.source.isBlank() || !canReadContentUri(task.source))
        ) {
            setMessage(
                "This assignment’s device video is unavailable. Reattach it from Assign.",
            )
            return
        }
        uiState = uiState.copy(
            selectedTaskId = task.id,
            activeTaskId = task.id,
        )
        if (!uiState.running) startTimer()
        logEvent(task, "${uiState.mode.label} started")
    }

    fun resumeActivePractice() {
        if (
            uiState.activeTaskId != null &&
            !uiState.running &&
            !uiState.recordingActive
        ) {
            startTimer("Practice timer resumed")
        }
    }

    fun finishPractice(): Boolean {
        if (uiState.recordingActive) {
            setMessage("Stop the current recording before finishing practice.")
            return false
        }
        val task = taskFor(uiState.activeTaskId)
        pauseTimer(logEvent = false)
        if (task != null) logEvent(task, "Practice session finished")
        uiState = uiState.copy(activeTaskId = null, recordingActive = false)
        return true
    }

    fun setRecordingActive(active: Boolean) {
        uiState = uiState.copy(recordingActive = active)
    }

    fun recordRepetition(task: PracticeTask) {
        val before = uiState.todayProgress.repetitionsFor(task.id)
        if (before >= task.repetitions) {
            setMessage("All ${task.repetitions} repetitions are already complete.")
            return
        }
        updateDailyProgress { it.recordRepetition(task) }
        val after = uiState.todayProgress.repetitionsFor(task.id)
        logEvent(task, "Repetition $after of ${task.repetitions} completed")
        setMessage(
            if (after >= task.repetitions) {
                "Assignment complete: ${task.repetitions} repetitions."
            } else {
                "Recorded repetition $after of ${task.repetitions}."
            },
        )
    }

    fun toggleCompleted(task: PracticeTask) {
        val wasCompleted = task.id in uiState.todayProgress.completedTaskIds
        updateDailyProgress { it.toggleCompleted(task) }
        logEvent(task, if (wasCompleted) "Assignment reopened" else "Assignment marked complete")
    }

    fun recordingSaved(task: PracticeTask, recording: RecordingFile) {
        logEvent(
            task = task,
            message = "Practice take saved privately",
            videoUri = recording.uri,
            videoPath = recording.path,
        )
        setMessage("Take saved privately inside Practice Partner. Share it only when ready.")
    }

    fun deleteRecording(videoPath: String): Boolean {
        val recordingFile = managedRecordingFile(videoPath)
        if (recordingFile == null) {
            setMessage("This recording path is outside Practice Partner storage and was not deleted.")
            return false
        }
        val existed = recordingFile.exists()
        val deleted = runCatching {
            !existed || recordingFile.delete() || !recordingFile.exists()
        }.getOrDefault(false)
        if (!deleted) {
            setMessage("The recording could not be deleted. Its log entry was kept so you can try again.")
            return false
        }
        updateSnapshot { snapshot ->
            snapshot.copy(events = snapshot.events.filterNot { it.videoPath == videoPath })
        }
        setMessage(if (existed) "Recording deleted." else "Recording entry removed; the file was already unavailable.")
        return true
    }

    fun pauseForBackground() {
        pauseTimer(logEvent = false)
    }

    override fun onCleared() {
        autoSaveJob?.cancel()
        pauseTimer(logEvent = false)
    }

    private fun startTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && uiState.running) {
                val elapsed = calculatedElapsedSeconds()
                uiState = uiState.copy(elapsedSeconds = elapsed)
                if (elapsed % 5L == 0L) {
                    updateDailyProgress { it.copy(elapsedSeconds = elapsed) }
                }
                delay(1_000)
            }
        }
    }

    private fun calculatedElapsedSeconds(): Long {
        val startedAt = timerStartedAtMillis ?: return timerBaseSeconds
        return timerBaseSeconds + ((elapsedRealtime() - startedAt).coerceAtLeast(0L) / 1_000L)
    }

    private fun updateDailyProgress(transform: (DailyProgress) -> DailyProgress) {
        val date = uiState.currentDate
        updateSnapshot { snapshot ->
            val current = snapshot.progressByDate[date] ?: DailyProgress(date)
            snapshot.copy(progressByDate = snapshot.progressByDate + (date to transform(current)))
        }
    }

    private fun updateSnapshot(
        saveWorkbook: Boolean = false,
        transform: (PracticeSnapshot) -> PracticeSnapshot,
    ) {
        val next = transform(uiState.snapshot)
        if (next == uiState.snapshot) return
        store.save(next)
        uiState = uiState.copy(snapshot = next)
        if (saveWorkbook) recordLocalMutation()
    }

    private fun taskFor(id: String?): PracticeTask? = uiState.snapshot.tasks.firstOrNull { it.id == id }

    private fun logEvent(
        task: PracticeTask?,
        message: String,
        videoUri: String? = null,
        videoPath: String? = null,
    ) {
        val now = wallClock()
        val event = PracticeEvent(
            id = UUID.randomUUID().toString(),
            date = dateKeyForMillis(now),
            taskId = task?.id,
            taskTitle = task?.title ?: "Practice session",
            event = message,
            atMillis = now,
            videoUri = videoUri,
            videoPath = videoPath,
        )
        var droppedRecordingPaths = emptySet<String>()
        updateSnapshot { snapshot ->
            val combined = snapshot.events + event
            val retained = combined.takeLast(MAX_EVENTS)
            val retainedPaths = retained.mapNotNull(PracticeEvent::videoPath).toSet()
            droppedRecordingPaths = combined
                .dropLast(retained.size)
                .mapNotNull(PracticeEvent::videoPath)
                .filterNot(retainedPaths::contains)
                .toSet()
            snapshot.copy(events = retained)
        }
        droppedRecordingPaths.forEach(::deleteManagedRecordingFileOnly)
    }

    private fun recordLocalMutation() {
        localRevision = store.bumpLocalRevision()
        val uri = studentProfile.excelWorkbookUri
        if (uri.isNullOrBlank()) return
        localFileState = localFileState.copy(
            status = LocalFileStatus.UNSAVED,
            fileName = studentProfile.excelWorkbookName,
            fileUri = uri,
            hasUnsavedChanges = true,
            detail = "Assignment or profile changes are waiting for the next local file save.",
        )
        scheduleAutomaticFileSave()
    }

    private fun scheduleAutomaticFileSave() {
        val uri = studentProfile.excelWorkbookUri ?: return
        if (localFileState.status != LocalFileStatus.UNSAVED) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MILLIS)
            if (localFileState.status == LocalFileStatus.UNSAVED) {
                writeLocalWorkbook(uri, LocalWorkbookWriteMode.SAVE, automatic = true)
            }
        }
    }

    private fun canReadContentUri(value: String?): Boolean {
        if (value.isNullOrBlank() || !value.startsWith("content://")) return true
        return runCatching {
            appContext.contentResolver.openFileDescriptor(value.toUri(), "r")?.use { true }
                ?: false
        }.getOrDefault(false)
    }

    private fun hasProgressForTask(taskId: String): Boolean =
        uiState.snapshot.progressByDate.values.any { progress ->
            taskId in progress.completedTaskIds || progress.repetitionsFor(taskId) > 0
        }

    private fun isDuplicateTask(
        task: PracticeTask,
        excludingTaskId: String? = null,
        additionalTasks: List<PracticeTask> = emptyList(),
    ): Boolean {
        val key = assignmentKey(task)
        return (uiState.snapshot.tasks + additionalTasks).any {
            it.id != excludingTaskId && assignmentKey(it) == key
        }
    }

    private fun mergeWorkbookAssignments(
        assignments: List<WorkbookAssignment>,
    ): WorkbookAssignmentMerge {
        val merged = uiState.snapshot.tasks.toMutableList()
        val errors = mutableListOf<String>()
        var loadedCount = 0
        var generatedIdCount = 0
        assignments.forEachIndexed { index, assignment ->
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
                existing?.sheetMusicUri?.startsWith("content://") == true -> existing.sheetMusicUri
                else -> null
            }
            if (assignment.needsSheetMusicReattachment && sheetMusic == null) {
                errors +=
                    "Excel row ${index + 2}: local sheet music needs reattachment on this device."
            }
            val candidate = PracticeTask(
                id = assignment.id,
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
            )
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
                hasProgressForTask(existing.id)
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

    private fun managedRecordingDirectories(): List<File> = buildList {
        appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let {
            add(File(it, "Practice Partner"))
        }
        add(File(appContext.filesDir, "Practice Partner"))
    }

    private fun managedRecordingFile(path: String): File? {
        val candidate = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val withinManagedDirectory = managedRecordingDirectories().any { directory ->
            val root = runCatching { directory.canonicalFile }.getOrNull() ?: return@any false
            candidate.path == root.path ||
                candidate.path.startsWith(root.path + File.separator)
        }
        return candidate.takeIf {
            withinManagedDirectory && it.name.startsWith("PracticePartner-") && it.extension == "mp4"
        }
    }

    private fun deleteManagedRecordingFileOnly(path: String) {
        managedRecordingFile(path)?.let { file ->
            runCatching {
                if (file.exists()) file.delete()
            }
        }
    }

    private fun cleanupOrphanedRecordings() {
        val referenced = uiState.snapshot.events.mapNotNull(PracticeEvent::videoPath).toSet()
        managedRecordingDirectories().forEach { directory ->
            directory.listFiles()
                .orEmpty()
                .filter { it.isFile && it.extension == "mp4" }
                .filterNot { it.absolutePath in referenced }
                .forEach { deleteManagedRecordingFileOnly(it.absolutePath) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PracticeViewModel(
                    appContext = context.applicationContext,
                    store = PracticeStore(context.applicationContext),
                    localWorkbookStore = LocalWorkbookStore(context.applicationContext),
                ) as T
        }
    }
}

private enum class LocalWorkbookWriteMode {
    CREATE,
    CONNECT_EXISTING,
    SAVE,
}

private fun friendlyDate(date: String): String = runCatching {
    java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
}.getOrDefault(date)

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

private fun assignmentKey(task: PracticeTask): AssignmentKey =
    AssignmentKey(
        date = task.assignedDate,
        title = task.title.trim().lowercase(),
        source = when (task.sourceType) {
            SourceType.YOUTUBE -> youtubeVideoId(task.source).orEmpty()
            SourceType.LOCAL -> task.source.trim()
        },
        startSeconds = task.startSeconds,
        endSeconds = task.endSeconds,
    )

private data class AssignmentKey(
    val date: String,
    val title: String,
    val source: String,
    val startSeconds: Int,
    val endSeconds: Int?,
)

private data class WorkbookAssignmentMerge(
    val tasks: List<PracticeTask>,
    val loadedCount: Int,
    val generatedIdCount: Int,
    val errors: List<String>,
)

private const val MAX_EVENTS = 500
private const val AUTO_SAVE_DELAY_MILLIS = 15_000L
