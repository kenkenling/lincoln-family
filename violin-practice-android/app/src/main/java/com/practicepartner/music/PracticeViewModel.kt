package com.practicepartner.music

import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PracticeViewModel(
    private val appContext: Context,
    private val store: PracticeStore,
    private val familyStore: FamilyStore,
    private val localWorkbookStore: LocalWorkbookStore,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val initialDate = currentDateKey()
    private val initialHousehold = familyStore.loadHousehold()
    private val initialProfile = initialHousehold.activeChildProfile
        ?: error("Create a child profile before opening practice.")
    private val initialLoad = store.loadWithStatus(initialProfile, initialDate)
    private val initialSnapshot = initialLoad.snapshot
    private var localRevision = familyStore.loadLocalRevision(initialProfile.id)
    private var lastFileSaveRevision =
        familyStore.loadLastFileSaveRevision(initialProfile.id)

    var household by mutableStateOf(initialHousehold)
        private set

    var studentProfile by mutableStateOf(initialProfile)
        private set

    var adultPinConfigured by mutableStateOf(familyStore.hasAdultPin())
        private set

    var adultUnlocked by mutableStateOf(false)
        private set

    var localFileState by mutableStateOf(
        initialLocalFileState(
            profile = studentProfile,
            lastSavedAtMillis = familyStore.loadLastFileSaveAt(studentProfile.id),
            file = localWorkbookStore.describe(studentProfile.excelWorkbookUri),
            localRevision = localRevision,
            lastFileSaveRevision = lastFileSaveRevision,
        ),
    )
        private set

    var uiState by mutableStateOf(
        PracticeUiState(
            snapshot = initialSnapshot,
            activeChildProfileId = studentProfile.id,
            currentDate = initialDate,
            elapsedSeconds = initialSnapshot.progressByDate[initialDate]?.elapsedSeconds ?: 0L,
            message = initialLoad.warning.orEmpty(),
        ),
    )
        private set

    private var timerBaseSeconds = uiState.elapsedSeconds
    private var sessionTimerBaseSeconds = uiState.activeSessionElapsedSeconds
    private var timerStartedAtMillis: Long? = null
    private var timerJob: Job? = null
    private var autoSaveJob: Job? = null
    private var adultExternalActionInProgress = false

    init {
        closeInterruptedSessions()
        cleanupOrphanedRecordings()
        if (localFileState.status == LocalFileStatus.UNSAVED) {
            scheduleAutomaticFileSave()
        }
    }

    fun setPage(page: AppPage) {
        if (uiState.activeTaskId != null) return
        if (page != AppPage.TODAY && !requireAdultAccess()) return
        uiState = uiState.copy(page = page)
    }

    fun createAdultPin(pin: String, confirmation: String): Boolean {
        val error = when {
            adultPinConfigured -> "A parent PIN is already configured."
            !isValidAdultPin(pin) -> "Create a 4-digit parent PIN."
            pin != confirmation -> "The two PIN entries do not match."
            else -> null
        }
        if (error != null) {
            setMessage(error)
            return false
        }
        if (!familyStore.setAdultPin(pin)) {
            setMessage("The parent PIN could not be saved.")
            return false
        }
        adultPinConfigured = true
        adultUnlocked = true
        setMessage("Parent Space is protected on this device.")
        return true
    }

    fun unlockAdultSpace(pin: String): Boolean {
        if (!adultPinConfigured) {
            setMessage("Create the parent PIN first.")
            return false
        }
        if (!familyStore.verifyAdultPin(pin)) {
            setMessage("That parent PIN is not correct.")
            return false
        }
        adultUnlocked = true
        return true
    }

    fun lockAdultSpace() {
        adultExternalActionInProgress = false
        adultUnlocked = false
        if (uiState.activeTaskId == null && uiState.page != AppPage.TODAY) {
            uiState = uiState.copy(page = AppPage.TODAY)
        }
    }

    fun beginAdultExternalAction(): Boolean {
        if (!requireAdultAccess()) return false
        adultExternalActionInProgress = true
        return true
    }

    fun endAdultExternalAction() {
        adultExternalActionInProgress = false
    }

    fun selectChildProfile(childProfileId: String): Boolean {
        val profile = household.childProfiles.firstOrNull { it.id == childProfileId }
        if (profile == null) {
            setMessage("That student profile is no longer available.")
            return false
        }
        if (uiState.activeTaskId != null) {
            setMessage("Finish the current practice mission before switching students.")
            return false
        }
        if (uiState.recordingActive) {
            setMessage("Stop the current recording before switching students.")
            return false
        }
        if (localFileState.status == LocalFileStatus.SAVING) {
            setMessage("Wait for the local file save before switching students.")
            return false
        }
        if (profile.id == studentProfile.id) {
            uiState = uiState.copy(page = AppPage.TODAY)
            return true
        }

        pauseTimer(logEvent = false)
        autoSaveJob?.cancel()
        household = familyStore.setActiveChildProfile(profile.id)
        studentProfile = profile
        localRevision = familyStore.loadLocalRevision(profile.id)
        lastFileSaveRevision = familyStore.loadLastFileSaveRevision(profile.id)
        val load = store.loadWithStatus(profile, currentDateKey())
        val snapshot = interruptOpenSessions(load.snapshot, wallClock())
        if (snapshot != load.snapshot) store.save(profile, snapshot)
        val date = currentDateKey()
        timerBaseSeconds = snapshot.progressByDate[date]?.elapsedSeconds ?: 0L
        sessionTimerBaseSeconds = 0L
        timerStartedAtMillis = null
        timerJob?.cancel()
        timerJob = null
        uiState = PracticeUiState(
            snapshot = snapshot,
            activeChildProfileId = profile.id,
            currentDate = date,
            elapsedSeconds = timerBaseSeconds,
            activeSessionElapsedSeconds = 0L,
            message = load.warning.orEmpty(),
        )
        localFileState = initialLocalFileState(
            profile = profile,
            lastSavedAtMillis = familyStore.loadLastFileSaveAt(profile.id),
            file = localWorkbookStore.describe(profile.excelWorkbookUri),
            localRevision = localRevision,
            lastFileSaveRevision = lastFileSaveRevision,
        )
        if (localFileState.status == LocalFileStatus.UNSAVED) {
            scheduleAutomaticFileSave()
        }
        return true
    }

    fun addChildProfile(
        nameText: String,
        instrumentText: String,
    ): Boolean {
        if (!requireAdultAccess()) return false
        val name = nameText.trim()
        val instrument = instrumentText.trim()
        val error = profileValidationError(name, instrument)
        if (error != null) {
            setMessage(error)
            return false
        }
        val profile = ChildProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            instrument = instrument,
        )
        household = familyStore.addChildProfile(profile)
        store.save(profile, PracticeSnapshot())
        val selected = selectChildProfile(profile.id)
        if (selected) setMessage("Added $name’s practice profile.")
        return selected
    }

    fun showPreviousSummaryDate() {
        if (!requireAdultAccess()) return
        val dates = uiState.historyDates
        val previous = dates.lastOrNull { it < uiState.summaryDate } ?: return
        uiState = uiState.copy(summaryDate = previous)
    }

    fun showNextSummaryDate() {
        if (!requireAdultAccess()) return
        val dates = uiState.historyDates
        val next = dates.firstOrNull { it > uiState.summaryDate } ?: return
        uiState = uiState.copy(summaryDate = next)
    }

    fun showTodaySummary() {
        if (!requireAdultAccess()) return
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
        if (!requireAdultAccess()) return false
        val name = nameText.trim()
        val instrument = instrumentText.trim()
        val error = profileValidationError(name, instrument)
        if (error != null) {
            setMessage(error)
            return false
        }
        val updated = studentProfile.copy(
            name = name,
            instrument = instrument,
        )
        val changed = updated != studentProfile
        household = familyStore.saveChildProfile(updated)
        studentProfile = updated
        val scopedSnapshot = uiState.snapshot.scopedTo(updated)
        if (scopedSnapshot != uiState.snapshot) {
            store.save(updated, scopedSnapshot)
            uiState = uiState.copy(snapshot = scopedSnapshot)
        }
        if (changed) recordLocalMutation()
        setMessage("Profile saved.")
        return true
    }

    fun suggestedProfileArchiveFileName(childProfileId: String): String {
        val profile = household.childProfiles.firstOrNull { it.id == childProfileId }
            ?: studentProfile
        return ProfileArchiveWriter.fileName(profile)
    }

    fun exportChildProfileArchive(
        childProfileId: String,
        uriText: String,
    ) {
        if (!requireAdultAccess()) return
        val profile = household.childProfiles.firstOrNull { it.id == childProfileId }
        if (profile == null) {
            setMessage("That student profile is no longer available.")
            return
        }
        val uri = uriText.trim()
        if (!uri.startsWith("content://")) {
            setMessage("Choose a destination with the Android file picker.")
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val snapshot = store.load(profile)
                    val recordingFiles = snapshot.events
                        .mapNotNull(PracticeEvent::videoPath)
                        .distinct()
                        .mapNotNull(::managedRecordingFile)
                    val output = appContext.contentResolver.openOutputStream(uri.toUri(), "wt")
                        ?: error("The selected archive file could not be opened.")
                    output.use {
                        ProfileArchiveWriter.write(
                            output = it,
                            profile = profile,
                            snapshot = snapshot,
                            recordingFiles = recordingFiles,
                            exportedAtMillis = wallClock(),
                        )
                    }
                }
            }.onSuccess { result ->
                setMessage(
                    buildString {
                        append("Exported ${profile.name}’s student data")
                        if (result.recordingCount > 0) {
                            append(" with ${result.recordingCount} recording")
                            if (result.recordingCount != 1) append('s')
                        }
                        append('.')
                        if (result.skippedRecordingCount > 0) {
                            append(" ${result.skippedRecordingCount} unavailable recording")
                            if (result.skippedRecordingCount != 1) append('s')
                            append(" could not be included.")
                        }
                    },
                )
            }.onFailure { error ->
                setMessage(error.message ?: "The student data archive could not be created.")
            }
        }
    }

    fun deleteChildProfile(childProfileId: String): Boolean {
        if (!requireAdultAccess()) return false
        val profile = household.childProfiles.firstOrNull { it.id == childProfileId }
        if (profile == null) {
            setMessage("That student profile is no longer available.")
            return false
        }
        val error = when {
            household.childProfiles.size <= 1 ->
                "Add another child profile before deleting the last profile."
            uiState.activeTaskId != null && profile.id == studentProfile.id ->
                "Finish the current practice mission before deleting this profile."
            uiState.recordingActive && profile.id == studentProfile.id ->
                "Stop the current recording before deleting this profile."
            localFileState.status == LocalFileStatus.SAVING &&
                profile.id == studentProfile.id ->
                "Wait for the local file save before deleting this profile."
            else -> null
        }
        if (error != null) {
            setMessage(error)
            return false
        }

        val snapshot = store.load(profile)
        val otherProfiles = household.childProfiles.filterNot { it.id == profile.id }
        val pathsReferencedElsewhere = store.referencedRecordingPaths(otherProfiles)
        var failedRecordingDeletes = 0
        snapshot.events
            .mapNotNull(PracticeEvent::videoPath)
            .distinct()
            .filterNot(pathsReferencedElsewhere::contains)
            .forEach { path ->
                val file = managedRecordingFile(path) ?: return@forEach
                val deleted = runCatching {
                    !file.exists() || file.delete() || !file.exists()
                }.getOrDefault(false)
                if (!deleted) failedRecordingDeletes += 1
            }
        releaseWorkbookPermissionIfUnused(profile, otherProfiles)

        val deletingActiveProfile = profile.id == studentProfile.id
        store.delete(profile)
        household = familyStore.removeChildProfile(profile.id)
        val selected = if (deletingActiveProfile) {
            selectChildProfile(household.activeChildProfileId)
        } else {
            true
        }
        if (!selected) return false
        setMessage(
            buildString {
                append("Deleted ${profile.name}’s app profile, practice history, and managed recordings.")
                if (!profile.excelWorkbookName.isNullOrBlank()) {
                    append(" The external Excel file was left in place.")
                }
                if (failedRecordingDeletes > 0) {
                    append(" $failedRecordingDeletes recording file")
                    if (failedRecordingDeletes != 1) append('s')
                    append(" could not be removed.")
                }
            },
        )
        return true
    }

    fun suggestedExcelFileName(): String =
        PracticeWorkbookTemplate.fileName(studentProfile)

    fun createLocalWorkbook(uriText: String) {
        if (!requireAdultAccess()) return
        val uri = validWorkbookUri(uriText) ?: return
        writeLocalWorkbook(uri, LocalWorkbookWriteMode.CREATE)
    }

    fun connectExistingLocalWorkbook(uriText: String) {
        if (!requireAdultAccess()) return
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
                    store.save(studentProfile, nextSnapshot)
                    uiState = uiState.copy(snapshot = nextSnapshot)
                }
                val file = localWorkbookStore.describe(uri)
                val updated = studentProfile.copy(
                    excelWorkbookUri = uri,
                    excelWorkbookName = file?.name ?: PracticeWorkbookTemplate.fileName(
                        studentProfile,
                    ),
                )
                household = familyStore.saveChildProfile(updated)
                studentProfile = updated
                localRevision = familyStore.bumpLocalRevision(updated.id)
                localFileState = LocalFileUiState(
                    status = LocalFileStatus.UNSAVED,
                    fileName = updated.excelWorkbookName,
                    fileUri = uri,
                    lastSavedAtMillis = familyStore.loadLastFileSaveAt(updated.id),
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
        if (!requireAdultAccess()) return
        val uri = studentProfile.excelWorkbookUri
        if (uri.isNullOrBlank()) {
            setMessage("Create or choose a local Excel file first.")
            return
        }
        writeLocalWorkbook(uri, LocalWorkbookWriteMode.SAVE, automatic = false)
    }

    fun loadAssignmentsFromLocalFile() {
        if (!requireAdultAccess()) return
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
                    "Checking the selected LK Practice file…"
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
                household = familyStore.saveChildProfile(updated)
                familyStore.saveLastFileSaveAt(updated.id, workbook.savedAtMillis)
                familyStore.saveLastFileSaveRevision(updated.id, requestedRevision)
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
                localFileState = LocalFileUiState(
                    status = LocalFileStatus.ERROR,
                    fileName = studentProfile.excelWorkbookName,
                    fileUri = studentProfile.excelWorkbookUri,
                    lastSavedAtMillis = familyStore.loadLastFileSaveAt(studentProfile.id),
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
        uiState.activeSessionId?.let { sessionId ->
            updateSnapshot { snapshot ->
                snapshot.copy(
                    sessions = snapshot.sessions.map { session ->
                        if (
                            session.id == sessionId &&
                            session.status == PracticeSessionStatus.ACTIVE
                        ) {
                            session.copy(
                                endedAtMillis = wallClock()
                                    .coerceAtLeast(session.startedAtMillis),
                                status = PracticeSessionStatus.INTERRUPTED,
                            )
                        } else {
                            session
                        }
                    },
                )
            }
        }
        val elapsed = uiState.snapshot.progressByDate[today]?.elapsedSeconds ?: 0L
        timerBaseSeconds = elapsed
        sessionTimerBaseSeconds = 0L
        uiState = uiState.copy(
            currentDate = today,
            summaryDate = if (wasShowingToday) today else uiState.summaryDate,
            activeTaskId = null,
            activeSessionId = null,
            selectedTaskId = null,
            elapsedSeconds = elapsed,
            activeSessionElapsedSeconds = 0L,
            running = false,
            recordingActive = false,
            pendingReflectionSessionId = null,
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
        assignmentType: AssignmentType,
        goalType: PracticeGoalType,
        targetMinutesText: String,
        enabledModes: Set<PracticeMode>,
    ): Boolean {
        if (!requireAdultAccess()) return false
        val assignedDate = assignedDateText.trim()
        val title = titleText.trim()
        val source = sourceText.trim()
        val sourceType = sourceTypeFor(source)
        val start = startText.trim().ifBlank { "0" }.toIntOrNull()
        val end = endText.trim().takeIf(String::isNotBlank)?.toIntOrNull()
        val repetitions = repetitionsText.trim().toIntOrNull()
        val targetMinutes = targetMinutesText.trim().toIntOrNull()
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

        var task = PracticeTask(
            id = existingTask?.id ?: UUID.randomUUID().toString(),
            childProfileId = studentProfile.id,
            instrument = studentProfile.instrument,
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
        assignmentType: AssignmentType = AssignmentType.TECHNIQUE,
        goalType: PracticeGoalType = PracticeGoalType.REPETITIONS,
        targetMinutesText: String = "10",
        enabledModes: Set<PracticeMode> = setOf(PracticeMode.PLAY_YOURSELF),
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
        assignmentType = assignmentType,
        goalType = goalType,
        targetMinutesText = targetMinutesText,
        enabledModes = enabledModes,
    )

    fun importCsv(text: String, defaultDate: String = uiState.currentDate) {
        if (!requireAdultAccess()) return
        val result = CsvCodec.decode(
            text = text,
            defaultDate = defaultDate,
            childProfileId = studentProfile.id,
            instrument = studentProfile.instrument,
        )
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

    fun exportCsvForDate(date: String): String = CsvCodec.encode(
        uiState.snapshot.tasks.filter { it.assignedDate == date },
    )

    fun archiveTask(task: PracticeTask) {
        if (!requireAdultAccess()) return
        if (task.archivedAtMillis != null) return
        if (task.id == uiState.activeTaskId) {
            setMessage("Finish the current practice mission before archiving it.")
            return
        }
        updateSnapshot(saveWorkbook = true) { snapshot ->
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
        if (!requireAdultAccess()) return
        if (task.archivedAtMillis == null) return
        updateSnapshot(saveWorkbook = true) { snapshot ->
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
        if (!requireAdultAccess()) return
        if (uiState.recordingActive && task.id == uiState.activeTaskId) {
            setMessage("Stop the recording before removing this assignment.")
            return
        }
        if (task.id == uiState.activeTaskId) {
            setMessage("Finish the current practice mission before removing this assignment.")
            return
        }
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
        sessionTimerBaseSeconds = uiState.activeSessionId
            ?.let { sessionId ->
                uiState.snapshot.sessions.firstOrNull { it.id == sessionId }?.elapsedSeconds
            }
            ?: uiState.activeSessionElapsedSeconds
        timerStartedAtMillis = elapsedRealtime()
        uiState = uiState.copy(
            running = true,
            elapsedSeconds = timerBaseSeconds,
            activeSessionElapsedSeconds = sessionTimerBaseSeconds,
        )
        logEvent(taskFor(uiState.selectedTaskId), eventMessage)
        startTicker()
    }

    fun pauseTimer(logEvent: Boolean = true) {
        if (!uiState.running) return
        val elapsed = calculatedElapsedSeconds()
        val sessionElapsed = calculatedSessionElapsedSeconds()
        timerJob?.cancel()
        timerJob = null
        timerStartedAtMillis = null
        timerBaseSeconds = elapsed
        sessionTimerBaseSeconds = sessionElapsed
        persistTimerProgress(elapsed, sessionElapsed)
        uiState = uiState.copy(
            running = false,
            elapsedSeconds = elapsed,
            activeSessionElapsedSeconds = sessionElapsed,
        )
        if (logEvent) logEvent(taskFor(uiState.selectedTaskId), "Practice timer paused")
    }

    fun startPractice(task: PracticeTask) {
        refreshDate()
        if (uiState.activeTaskId != null) {
            setMessage("Finish the current practice mission before starting another one.")
            return
        }
        if (task.archivedAtMillis != null) {
            setMessage("This assignment is archived. A grown-up can restore it in Parent Space.")
            return
        }
        if (
            task.childProfileId.isNotBlank() &&
            task.childProfileId != studentProfile.id
        ) {
            setMessage("This assignment belongs to another student profile.")
            return
        }
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
        val resolvedMode = when {
            task.sourceType == SourceType.NONE -> PracticeMode.PLAY_YOURSELF
            uiState.mode in task.enabledModes -> uiState.mode
            else -> task.enabledModes.firstOrNull() ?: PracticeMode.PLAY_YOURSELF
        }
        val session = PracticeSession(
            id = UUID.randomUUID().toString(),
            childProfileId = studentProfile.id,
            assignmentId = task.id,
            assignmentTitle = task.title,
            instrument = task.instrument.ifBlank { studentProfile.instrument },
            date = uiState.currentDate,
            mode = resolvedMode,
            startedAtMillis = wallClock(),
            repetitionsCompleted = 0,
            assignmentRevision = task.revision,
            assignmentType = task.assignmentType,
            goalType = task.goalType,
            targetRepetitions = task.repetitions,
            targetSeconds = task.targetSeconds,
            elapsedSeconds = 0L,
        )
        sessionTimerBaseSeconds = 0L
        uiState = uiState.copy(
            selectedTaskId = task.id,
            activeTaskId = task.id,
            activeSessionId = session.id,
            mode = resolvedMode,
            activeSessionElapsedSeconds = 0L,
            pendingReflectionSessionId = null,
        )
        updateSnapshot { snapshot ->
            snapshot.copy(sessions = (snapshot.sessions + session).takeLast(MAX_SESSIONS))
        }
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
        val sessionId = uiState.activeSessionId
        pauseTimer(logEvent = false)
        val goalMet = task?.let { uiState.goalMet(it, uiState.currentDate) } == true
        if (sessionId != null) {
            updateSnapshot { snapshot ->
                snapshot.copy(
                    sessions = snapshot.sessions.map { session ->
                        if (session.id == sessionId) {
                            session.copy(
                                endedAtMillis = wallClock()
                                    .coerceAtLeast(session.startedAtMillis),
                                status = if (goalMet) {
                                    PracticeSessionStatus.COMPLETED
                                } else {
                                    PracticeSessionStatus.ENDED
                                },
                            )
                        } else {
                            session
                        }
                    },
                )
            }
        }
        if (task != null) logEvent(task, "Practice session finished")
        uiState = uiState.copy(
            activeTaskId = null,
            activeSessionId = null,
            activeSessionElapsedSeconds = 0L,
            recordingActive = false,
            pendingReflectionSessionId = sessionId,
        )
        sessionTimerBaseSeconds = 0L
        return true
    }

    fun setRecordingActive(active: Boolean) {
        uiState = uiState.copy(recordingActive = active)
    }

    fun recordRepetition(task: PracticeTask) {
        if (task.goalType != PracticeGoalType.REPETITIONS) {
            setMessage("This mission uses a practice-time goal.")
            return
        }
        val before = uiState.todayProgress.repetitionsFor(task.id)
        if (before >= task.repetitions) {
            setMessage("All ${task.repetitions} repetitions are already complete.")
            return
        }
        updateDailyProgress { it.recordRepetition(task) }
        val after = uiState.todayProgress.repetitionsFor(task.id)
        uiState.activeSessionId?.let { sessionId ->
            updateSnapshot { snapshot ->
                snapshot.copy(
                    sessions = snapshot.sessions.map { session ->
                        if (session.id == sessionId) {
                            session.copy(
                                repetitionsCompleted =
                                    session.repetitionsCompleted + (after - before),
                            )
                        } else {
                            session
                        }
                    },
                )
            }
        }
        logEvent(task, "Repetition $after of ${task.repetitions} completed")
        setMessage(
            if (after >= task.repetitions) {
                "Assignment complete: ${task.repetitions} repetitions."
            } else {
                "Recorded repetition $after of ${task.repetitions}."
            },
        )
    }

    fun submitReflection(reflection: PracticeReflection?) {
        val sessionId = uiState.pendingReflectionSessionId ?: return
        val session = uiState.snapshot.sessions.firstOrNull { it.id == sessionId }
        updateSnapshot { snapshot ->
            snapshot.copy(
                sessions = snapshot.sessions.map {
                    if (it.id == sessionId) it.copy(reflection = reflection) else it
                },
            )
        }
        if (reflection != null) {
            logEvent(
                task = taskFor(session?.assignmentId),
                message = "Reflection: ${reflection.summaryLabel}",
                sessionIdOverride = sessionId,
            )
        }
        uiState = uiState.copy(
            pendingReflectionSessionId = null,
            message = when (reflection) {
                PracticeReflection.PROUD -> "Nice work noticing what went well."
                PracticeReflection.MAKING_PROGRESS -> "Small steps count. Keep building."
                PracticeReflection.NEED_HELP ->
                    "Good noticing. A grown-up can review this with you."
                null -> "Practice saved."
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
            videoFileName = recording.fileName,
        )
        setMessage("Saved ${recording.fileName}. A grown-up can review it in Practice Summary.")
    }

    fun deleteRecording(videoPath: String): Boolean {
        if (!requireAdultAccess()) return false
        val recordingFile = managedRecordingFile(videoPath)
        if (recordingFile == null) {
            setMessage("This recording path is outside LK Practice storage and was not deleted.")
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
        if (!adultExternalActionInProgress) {
            lockAdultSpace()
        }
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
                val sessionElapsed = calculatedSessionElapsedSeconds()
                uiState = uiState.copy(
                    elapsedSeconds = elapsed,
                    activeSessionElapsedSeconds = sessionElapsed,
                )
                if (elapsed % 5L == 0L) {
                    persistTimerProgress(elapsed, sessionElapsed)
                }
                delay(1_000)
            }
        }
    }

    private fun calculatedElapsedSeconds(): Long {
        val startedAt = timerStartedAtMillis ?: return timerBaseSeconds
        return timerBaseSeconds + ((elapsedRealtime() - startedAt).coerceAtLeast(0L) / 1_000L)
    }

    private fun calculatedSessionElapsedSeconds(): Long {
        val startedAt = timerStartedAtMillis ?: return sessionTimerBaseSeconds
        return sessionTimerBaseSeconds +
            ((elapsedRealtime() - startedAt).coerceAtLeast(0L) / 1_000L)
    }

    private fun persistTimerProgress(
        elapsedSeconds: Long,
        sessionElapsedSeconds: Long,
    ) {
        val date = uiState.currentDate
        val task = taskFor(uiState.activeTaskId)
        val sessionId = uiState.activeSessionId
        updateSnapshot { snapshot ->
            val updatedSessions = if (sessionId == null) {
                snapshot.sessions
            } else {
                snapshot.sessions.map { session ->
                    if (session.id == sessionId) {
                        session.copy(elapsedSeconds = sessionElapsedSeconds)
                    } else {
                        session
                    }
                }
            }
            var progress = (
                snapshot.progressByDate[date] ?: DailyProgress(
                    date = date,
                    childProfileId = studentProfile.id,
                )
                ).copy(elapsedSeconds = elapsedSeconds)
            if (
                task?.goalType == PracticeGoalType.TIME &&
                task.targetSeconds > 0 &&
                updatedSessions
                    .filter { it.assignmentId == task.id && it.date == date }
                    .sumOf(PracticeSession::elapsedSeconds) >= task.targetSeconds
            ) {
                progress = progress.copy(
                    completedTaskIds = progress.completedTaskIds + task.id,
                )
            }
            snapshot.copy(
                progressByDate = snapshot.progressByDate + (date to progress),
                sessions = updatedSessions,
            )
        }
    }

    private fun updateDailyProgress(transform: (DailyProgress) -> DailyProgress) {
        val date = uiState.currentDate
        updateSnapshot { snapshot ->
            val current = snapshot.progressByDate[date] ?: DailyProgress(
                date = date,
                childProfileId = studentProfile.id,
            )
            snapshot.copy(progressByDate = snapshot.progressByDate + (date to transform(current)))
        }
    }

    private fun updateSnapshot(
        saveWorkbook: Boolean = false,
        transform: (PracticeSnapshot) -> PracticeSnapshot,
    ) {
        val next = transform(uiState.snapshot).scopedTo(studentProfile)
        if (next == uiState.snapshot) return
        store.save(studentProfile, next)
        uiState = uiState.copy(snapshot = next)
        if (saveWorkbook) recordLocalMutation()
    }

    private fun taskFor(id: String?): PracticeTask? = uiState.snapshot.tasks.firstOrNull { it.id == id }

    private fun logEvent(
        task: PracticeTask?,
        message: String,
        videoUri: String? = null,
        videoPath: String? = null,
        videoFileName: String? = null,
        sessionIdOverride: String? = null,
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
            videoFileName = videoFileName,
            childProfileId = studentProfile.id,
            sessionId = sessionIdOverride ?: uiState.activeSessionId,
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
        localRevision = familyStore.bumpLocalRevision(studentProfile.id)
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

    private fun requireAdultAccess(): Boolean {
        if (adultUnlocked) return true
        setMessage("Open Parent Space and enter the parent PIN first.")
        return false
    }

    private fun closeInterruptedSessions() {
        val interrupted = interruptOpenSessions(uiState.snapshot, wallClock())
        if (interrupted == uiState.snapshot) return
        store.save(studentProfile, interrupted)
        uiState = uiState.copy(
            snapshot = interrupted,
            message = listOf(
                uiState.message,
                "A practice session interrupted by the previous app exit was closed safely.",
            ).filter(String::isNotBlank).joinToString(" "),
        )
    }

    private fun hasProgressForTask(taskId: String): Boolean =
        uiState.snapshot.progressByDate.values.any { progress ->
            taskId in progress.completedTaskIds || progress.repetitionsFor(taskId) > 0
        } || uiState.snapshot.sessions.any { session ->
            session.assignmentId == taskId &&
                (session.elapsedSeconds > 0L || session.repetitionsCompleted > 0)
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
            if (
                assignment.childProfileId.isNotBlank() &&
                assignment.childProfileId != studentProfile.id
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
                existing?.sheetMusicUri?.startsWith("content://") == true -> existing.sheetMusicUri
                else -> null
            }
            if (assignment.needsSheetMusicReattachment && sheetMusic == null) {
                errors +=
                    "Excel row ${index + 2}: local sheet music needs reattachment on this device."
            }
            var candidate = PracticeTask(
                id = assignment.id,
                childProfileId = studentProfile.id,
                instrument = assignment.instrument.ifBlank { studentProfile.instrument },
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
            withinManagedDirectory && isManagedPracticeRecordingFileName(it.name)
        }
    }

    private fun releaseWorkbookPermissionIfUnused(
        deletedProfile: ChildProfile,
        remainingProfiles: List<ChildProfile>,
    ) {
        val uriText = deletedProfile.excelWorkbookUri?.takeIf(String::isNotBlank) ?: return
        if (remainingProfiles.any { it.excelWorkbookUri == uriText }) return
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                uriText.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
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
        val referenced = store.referencedRecordingPaths(household.childProfiles)
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
                    familyStore = FamilyStore(context.applicationContext),
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

private fun profileValidationError(name: String, instrument: String): String? = when {
    name.isBlank() -> "Add the student’s name."
    name.length > 80 -> "Keep the student’s name under 80 characters."
    instrument.isBlank() -> "Add an instrument."
    instrument.length > 80 -> "Keep the instrument under 80 characters."
    else -> null
}

private fun interruptOpenSessions(
    snapshot: PracticeSnapshot,
    atMillis: Long,
): PracticeSnapshot {
    val interrupted = snapshot.sessions.map { session ->
        if (session.status == PracticeSessionStatus.ACTIVE) {
            session.copy(
                endedAtMillis = atMillis.coerceAtLeast(session.startedAtMillis),
                status = PracticeSessionStatus.INTERRUPTED,
            )
        } else {
            session
        }
    }
    return if (interrupted == snapshot.sessions) snapshot else snapshot.copy(sessions = interrupted)
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

private fun assignmentKey(task: PracticeTask): AssignmentKey =
    AssignmentKey(
        date = task.assignedDate,
        title = task.title.trim().lowercase(),
        source = when (task.sourceType) {
            SourceType.NONE -> ""
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
private const val MAX_SESSIONS = 500
private const val AUTO_SAVE_DELAY_MILLIS = 15_000L
