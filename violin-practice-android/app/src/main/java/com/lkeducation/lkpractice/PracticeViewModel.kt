package com.lkeducation.lkpractice

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import java.io.File

class PracticeViewModel(
    private val store: PracticeDataStore,
    private val familyStore: FamilyDataStore,
    private val localWorkbookStore: WorkbookStorage,
    private val recordingManager: RecordingManager,
    private val profileArchiveDestination: ProfileArchiveDestination,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val dateProvider: () -> DateKey = ::currentDateKey,
    coroutineScope: CoroutineScope? = null,
    timerTicker: PracticeTimerTicker = PracticeTimerTicker.Default,
) : ViewModel() {
    private val managerScope = coroutineScope ?: viewModelScope
    private val initialDate = dateProvider()
    private val initialHousehold = familyStore.loadHousehold()
    private val initialProfile = initialHousehold.activeChildProfile
        ?: error("Create a child profile before opening practice.")
    private val initialLoad = store.loadWithStatus(initialProfile, initialDate)
    private val initialSnapshot = initialLoad.snapshot

    var household by mutableStateOf(initialHousehold)
        private set

    var studentProfile by mutableStateOf(initialProfile)
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

    private val adultAccessManager = AdultAccessManager(familyStore, ::setMessage)
    private val profileDataManager = ProfileDataManager(
        scope = managerScope,
        store = store,
        familyStore = familyStore,
        recordingManager = recordingManager,
        archiveDestination = profileArchiveDestination,
        wallClock = wallClock,
        setMessage = ::setMessage,
    )
    private val timerManager = PracticeTimerManager(
        scope = managerScope,
        elapsedRealtime = elapsedRealtime,
        profileProvider = { studentProfile },
        dateProvider = { uiState.currentDate },
        activeTaskProvider = {
            uiState.snapshot.tasks.firstOrNull { it.id == uiState.activeTaskId }
        },
        activeSessionIdProvider = { uiState.activeSessionId },
        updateSnapshot = { transform -> updateSnapshot(transform = transform) },
        onTick = { reading ->
            uiState = uiState.copy(
                elapsedSeconds = reading.elapsedSeconds,
                activeSessionElapsedSeconds = reading.sessionElapsedSeconds,
            )
        },
        ticker = timerTicker,
    )
    private val workbookSyncManager = WorkbookSyncManager(
        scope = managerScope,
        workbookStore = localWorkbookStore,
        settings = familyStore,
        wallClock = wallClock,
        profileProvider = { studentProfile },
        snapshotProvider = { uiState.snapshot },
        currentDateProvider = { uiState.currentDate },
        activeTaskIdProvider = { uiState.activeTaskId },
        recordingActiveProvider = { uiState.recordingActive },
        canReadContentUri = recordingManager::canReadContentUri,
        persistSnapshot = { snapshot ->
            val scoped = prepareSnapshotForPersistence(snapshot)
            store.applyChanges(
                profile = studentProfile,
                previous = uiState.snapshot,
                next = scoped,
            )
            uiState = uiState.copy(snapshot = scoped)
        },
        updateSnapshot = { saveWorkbook, transform ->
            updateSnapshot(saveWorkbook = saveWorkbook, transform = transform)
        },
        updateProfile = { profile, updatedHousehold ->
            household = updatedHousehold
            studentProfile = profile
        },
        clearSelectedTask = { taskId ->
            uiState = uiState.copy(
                selectedTaskId = uiState.selectedTaskId.takeUnless { it == taskId },
            )
        },
        setMessage = ::setMessage,
    )
    private val sessionManager = PracticeSessionManager(
        profileProvider = { studentProfile },
        stateProvider = { uiState },
        wallClock = wallClock,
        canReadContentUri = recordingManager::canReadContentUri,
        updateState = { transform -> uiState = transform(uiState) },
        updateSnapshot = { transform -> updateSnapshot(transform = transform) },
        setMessage = ::setMessage,
        startTimer = ::startTimer,
        pauseTimer = ::pauseTimer,
        prepareSessionTimer = { timerManager.setSessionElapsedBase(0L) },
        resetSessionTimer = { elapsedSeconds -> timerManager.reset(elapsedSeconds) },
        deleteManagedRecordingFile = {
            recordingManager.deleteManagedRecordingFileOnly(it)
        },
    )

    val localFileState: LocalFileUiState get() = workbookSyncManager.state
    val adultPinConfigured: Boolean get() = adultAccessManager.pinConfigured
    val adultUnlocked: Boolean get() = adultAccessManager.unlocked

    init {
        timerManager.reset(uiState.elapsedSeconds, uiState.activeSessionElapsedSeconds)
        workbookSyncManager.activate()
        sessionManager.closeInterruptedSessions()?.let { warning ->
            uiState = uiState.copy(
                message = listOf(uiState.message, warning)
                    .filter(String::isNotBlank)
                    .joinToString(" "),
            )
        }
        recordingManager.cleanupOrphanedRecordings(
            store.referencedRecordingPaths(household.childProfiles),
        )
    }

    fun setPage(page: AppPage) {
        if (uiState.activeTaskId != null) return
        if (page != AppPage.TODAY && !requireAdultAccess()) return
        uiState = uiState.copy(page = page)
    }

    fun createAdultPin(pin: String, confirmation: String): Boolean =
        adultAccessManager.createPin(pin, confirmation)

    fun unlockAdultSpace(pin: String): Boolean = adultAccessManager.unlock(pin)

    fun lockAdultSpace() {
        adultAccessManager.lock()
        if (uiState.activeTaskId == null && uiState.page != AppPage.TODAY) {
            uiState = uiState.copy(page = AppPage.TODAY)
        }
    }

    fun beginAdultExternalAction(): Boolean = adultAccessManager.beginExternalAction()

    fun endAdultExternalAction() = adultAccessManager.endExternalAction()

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
        workbookSyncManager.close()
        household = familyStore.setActiveChildProfile(profile.id)
        studentProfile = profile
        val date = dateProvider()
        val load = store.loadWithStatus(profile, date)
        val snapshot = interruptOpenSessions(load.snapshot, wallClock())
        if (snapshot != load.snapshot) {
            store.applyChanges(profile, load.snapshot, snapshot)
        }
        val elapsedSeconds = snapshot.progressByDate[date]?.elapsedSeconds ?: 0L
        timerManager.reset(elapsedSeconds)
        uiState = PracticeUiState(
            snapshot = snapshot,
            activeChildProfileId = profile.id,
            currentDate = date,
            elapsedSeconds = elapsedSeconds,
            activeSessionElapsedSeconds = 0L,
            message = load.warning.orEmpty(),
        )
        workbookSyncManager.activate()
        return true
    }

    fun addChildProfile(
        nameText: String,
        instrumentText: String,
    ): Boolean {
        if (!requireAdultAccess()) return false
        val result = profileDataManager.addChildProfile(nameText, instrumentText)
        result.error?.let {
            setMessage(it)
            return false
        }
        val added = result.value ?: return false
        household = added.household
        val selected = selectChildProfile(added.profile.id)
        if (selected) setMessage("Added ${added.profile.name}’s practice profile.")
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
        if (uiState.activeTaskId != null) {
            sessionManager.switchMode(mode)
            return
        }
        uiState = uiState.copy(mode = mode)
    }

    fun switchPracticeMode(mode: PracticeMode): Boolean =
        sessionManager.switchMode(mode)

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
        val result = profileDataManager.saveChildProfile(
            currentProfile = studentProfile,
            currentSnapshot = uiState.snapshot,
            nameText = nameText,
            instrumentText = instrumentText,
        )
        result.error?.let {
            setMessage(it)
            return false
        }
        val saved = result.value ?: return false
        household = saved.household
        studentProfile = saved.profile
        uiState = uiState.copy(snapshot = saved.snapshot)
        if (saved.changed) workbookSyncManager.recordLocalMutation()
        setMessage("Profile saved.")
        return true
    }

    fun suggestedProfileArchiveFileName(childProfileId: String): String =
        profileDataManager.suggestedArchiveFileName(
            household = household,
            currentProfile = studentProfile,
            childProfileId = childProfileId,
        )

    fun exportChildProfileArchive(
        childProfileId: String,
        uriText: String,
    ) {
        if (!requireAdultAccess()) return
        profileDataManager.exportChildProfileArchive(household, childProfileId, uriText)
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

        val result = profileDataManager.deleteChildProfile(
            household = household,
            currentProfile = studentProfile,
            childProfileId = childProfileId,
        )
        result.error?.let {
            setMessage(it)
            return false
        }
        val deleted = result.value ?: return false
        household = deleted.household
        val selected = if (deleted.deletingActiveProfile) {
            selectChildProfile(deleted.nextActiveProfileId)
        } else {
            true
        }
        if (!selected) return false
        setMessage(deleted.message)
        return true
    }

    fun suggestedExcelFileName(): String =
        workbookSyncManager.suggestedExcelFileName()

    fun createLocalWorkbook(uriText: String) {
        if (!requireAdultAccess()) return
        workbookSyncManager.createLocalWorkbook(uriText)
    }

    fun connectExistingLocalWorkbook(uriText: String) {
        if (!requireAdultAccess()) return
        workbookSyncManager.connectExistingLocalWorkbook(uriText)
    }

    fun saveLocalWorkbook() {
        if (!requireAdultAccess()) return
        workbookSyncManager.saveLocalWorkbook()
    }

    fun loadAssignmentsFromLocalFile() {
        if (!requireAdultAccess()) return
        workbookSyncManager.loadAssignmentsFromLocalFile()
    }

    fun refreshDate() {
        val today = dateProvider()
        if (today == uiState.currentDate) return
        val wasShowingToday = uiState.summaryDate == uiState.currentDate
        pauseTimer(logEvent = false)
        sessionManager.interruptActiveSession()
        val elapsed = uiState.snapshot.progressByDate[today]?.elapsedSeconds ?: 0L
        timerManager.reset(elapsed)
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
        startDate: DateKey,
        endDate: DateKey,
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
        return workbookSyncManager.saveAssignment(
            taskId = taskId,
            startDate = startDate,
            endDate = endDate,
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
        startDate = uiState.currentDate,
        endDate = uiState.currentDate,
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

    fun importCsv(text: String, defaultDate: DateKey = uiState.currentDate) {
        if (!requireAdultAccess()) return
        workbookSyncManager.importCsv(text, defaultDate)
    }

    fun exportTodayCsv(): String =
        workbookSyncManager.exportCsvForDate(uiState.currentDate)

    fun exportCsvForDate(date: DateKey): String =
        workbookSyncManager.exportCsvForDate(date)

    fun archiveTask(task: PracticeTask) {
        if (!requireAdultAccess()) return
        workbookSyncManager.archiveTask(task)
    }

    fun restoreTask(task: PracticeTask) {
        if (!requireAdultAccess()) return
        workbookSyncManager.restoreTask(task)
    }

    fun removeTask(task: PracticeTask) {
        if (!requireAdultAccess()) return
        workbookSyncManager.removeTask(task)
    }

    fun startTimer(eventMessage: String = "Practice timer started") {
        refreshDate()
        if (uiState.running) return
        val elapsedSeconds = uiState.todayProgress.elapsedSeconds
        val sessionElapsedSeconds = uiState.activeSessionId
            ?.let { sessionId ->
                uiState.snapshot.sessions.firstOrNull { it.id == sessionId }?.elapsedSeconds
            }
            ?: uiState.activeSessionElapsedSeconds
        val reading = timerManager.start(elapsedSeconds, sessionElapsedSeconds)
        uiState = uiState.copy(
            running = true,
            elapsedSeconds = reading.elapsedSeconds,
            activeSessionElapsedSeconds = reading.sessionElapsedSeconds,
        )
        sessionManager.logEvent(
            sessionManager.taskFor(uiState.selectedTaskId),
            eventMessage,
        )
    }

    fun pauseTimer(logEvent: Boolean = true) {
        if (!uiState.running) return
        val reading = timerManager.pause() ?: return
        uiState = uiState.copy(
            running = false,
            elapsedSeconds = reading.elapsedSeconds,
            activeSessionElapsedSeconds = reading.sessionElapsedSeconds,
        )
        if (logEvent) {
            sessionManager.logEvent(
                sessionManager.taskFor(uiState.selectedTaskId),
                "Practice timer paused",
            )
        }
    }

    fun startPractice(task: PracticeTask) {
        refreshDate()
        sessionManager.startPractice(task)
    }

    fun startSelfDirectedPractice(task: PracticeTask) {
        refreshDate()
        sessionManager.startPractice(task, studentInitiated = true)
    }

    fun startSurprisePractice() {
        refreshDate()
        val options = uiState.selfDirectedPracticeOptions
        if (options.isEmpty()) {
            setMessage("Ask a grown-up to add a practice assignment first.")
            return
        }
        sessionManager.startPractice(
            options.random(),
            studentInitiated = true,
        )
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

    fun finishPractice(): Boolean = sessionManager.finishPractice()

    fun setRecordingActive(active: Boolean) {
        uiState = uiState.copy(recordingActive = active)
    }

    fun recordRepetition(task: PracticeTask) = sessionManager.recordRepetition(task)

    fun submitReflection(
        reflection: PracticeReflection?,
        studentConfident: Boolean = false,
    ) = sessionManager.submitReflection(reflection, studentConfident)

    fun toggleCompleted(task: PracticeTask) = sessionManager.toggleCompleted(task)

    fun recordingSaved(task: PracticeTask, recording: RecordingFile) =
        sessionManager.recordingSaved(task, recording)

    fun createRecordingFile(recordingFileName: String): File =
        recordingManager.createRecordingFile(recordingFileName)

    fun deleteRecording(videoPath: String): Boolean {
        if (!requireAdultAccess()) return false
        val result = recordingManager.deleteRecording(videoPath)
        if (!result.isManaged) {
            setMessage("This recording path is outside LK Practice storage and was not deleted.")
            return false
        }
        if (!result.deleted) {
            setMessage("The recording could not be deleted. Its log entry was kept so you can try again.")
            return false
        }
        updateSnapshot { snapshot ->
            snapshot.copy(events = snapshot.events.filterNot { it.videoPath == videoPath })
        }
        setMessage(
            if (result.existed) {
                "Recording deleted."
            } else {
                "Recording entry removed; the file was already unavailable."
            },
        )
        return true
    }

    fun pauseForBackground() {
        pauseTimer(logEvent = false)
        if (!adultAccessManager.externalActionInProgress) {
            lockAdultSpace()
        }
    }

    override fun onCleared() {
        workbookSyncManager.close()
        pauseTimer(logEvent = false)
        timerManager.close()
    }

    private fun updateSnapshot(
        saveWorkbook: Boolean = false,
        transform: (PracticeSnapshot) -> PracticeSnapshot,
    ) {
        val next = prepareSnapshotForPersistence(transform(uiState.snapshot))
        if (next == uiState.snapshot) return
        store.applyChanges(
            profile = studentProfile,
            previous = uiState.snapshot,
            next = next,
        )
        uiState = uiState.copy(snapshot = next)
        if (saveWorkbook) workbookSyncManager.recordLocalMutation()
    }

    private fun prepareSnapshotForPersistence(candidate: PracticeSnapshot): PracticeSnapshot {
        val scopedCandidate = candidate.scopedTo(studentProfile)
        return preserveAssignmentVersionHistory(
            previous = uiState.snapshot,
            candidate = scopedCandidate,
            changedAtMillis = wallClock(),
        ).scopedTo(studentProfile)
    }

    private fun requireAdultAccess(): Boolean = adultAccessManager.requireAccess()

    companion object {
        fun factory(
            context: android.content.Context,
            store: PracticeDataStore = PracticeStore(context.applicationContext),
            familyStore: FamilyDataStore = FamilyStore(context.applicationContext),
            localWorkbookStore: WorkbookStorage = LocalWorkbookStore(context.applicationContext),
            recordingManager: RecordingManager = RecordingManager.from(context.applicationContext),
            profileArchiveDestination: ProfileArchiveDestination =
                androidProfileArchiveDestination(context.applicationContext),
        ) = factory(
            store = store,
            familyStore = familyStore,
            localWorkbookStore = localWorkbookStore,
            recordingManager = recordingManager,
            profileArchiveDestination = profileArchiveDestination,
        )

        fun factory(
            store: PracticeDataStore,
            familyStore: FamilyDataStore,
            localWorkbookStore: WorkbookStorage,
            recordingManager: RecordingManager,
            profileArchiveDestination: ProfileArchiveDestination,
        ) = practiceViewModelFactory(
            store = store,
            familyStore = familyStore,
            localWorkbookStore = localWorkbookStore,
            recordingManager = recordingManager,
            profileArchiveDestination = profileArchiveDestination,
        )
    }
}
