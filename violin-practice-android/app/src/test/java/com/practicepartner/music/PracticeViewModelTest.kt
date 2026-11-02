package com.practicepartner.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class PracticeViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelScopes() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun injectedFactoryCreatesViewModelWithoutAndroidContext() {
        val profile = profile()
        val practiceStore = ViewModelPracticeStoreFake(emptyMap())
        val familyStore = ViewModelFamilyStoreFake(
            Household(
                id = "household",
                childProfiles = listOf(profile),
                activeChildProfileId = profile.id,
            ),
        )
        val recordingDirectory = temporaryFolder.newFolder("factory-recordings")

        val factory = PracticeViewModel.factory(
            store = practiceStore,
            familyStore = familyStore,
            localWorkbookStore = ViewModelWorkbookStoreFake(),
            recordingManager = RecordingManager(
                managedDirectoriesProvider = { listOf(recordingDirectory) },
            ),
            profileArchiveDestination = ProfileArchiveDestination {
                ByteArrayOutputStream()
            },
        )

        val viewModel = factory.create(PracticeViewModel::class.java)

        assertEquals(profile.id, viewModel.studentProfile.id)
        assertEquals(profile.id, viewModel.uiState.activeChildProfileId)
    }

    @Test
    fun startPracticeRecordRepetitionsAndFinishCompletesSession() {
        val profile = profile()
        val task = task(profile = profile, repetitions = 2)
        val fixture = fixture(
            profiles = listOf(profile),
            snapshots = mapOf(profile.id to PracticeSnapshot(tasks = listOf(task))),
        )

        fixture.viewModel.startPractice(task)
        fixture.viewModel.recordRepetition(task)
        fixture.viewModel.recordRepetition(task)
        val finished = fixture.viewModel.finishPractice()

        val state = fixture.viewModel.uiState
        val session = state.snapshot.sessions.single()
        assertTrue(finished)
        assertEquals(2, state.todayProgress.repetitionsFor(task.id))
        assertTrue(task.id in state.todayProgress.completedTaskIds)
        assertEquals(2, session.repetitionsCompleted)
        assertEquals(PracticeSessionStatus.COMPLETED, session.status)
        assertNotNull(session.endedAtMillis)
        assertNull(state.activeTaskId)
        assertNull(state.activeSessionId)
        assertEquals(session.id, state.pendingReflectionSessionId)
    }

    @Test
    fun switchPracticeModeKeepsTimerAndProgressAndTracksEveryStepUsed() {
        val profile = profile()
        val task = task(profile = profile, repetitions = 3).copy(
            source = "https://youtu.be/VZIWCt-jW6g",
            sourceType = SourceType.YOUTUBE,
            enabledModes = PracticeMode.entries.toSet(),
        )
        val fixture = fixture(
            profiles = listOf(profile),
            snapshots = mapOf(profile.id to PracticeSnapshot(tasks = listOf(task))),
        )

        fixture.viewModel.setMode(PracticeMode.LISTEN)
        fixture.viewModel.startPractice(task)
        fixture.viewModel.recordRepetition(task)
        fixture.clock.elapsedRealtimeMillis += 5_000L
        fixture.ticker.tick()
        val stateBeforeSwitch = fixture.viewModel.uiState

        assertTrue(fixture.viewModel.switchPracticeMode(PracticeMode.LISTEN_AND_PLAY))
        assertTrue(fixture.viewModel.switchPracticeMode(PracticeMode.PLAY_YOURSELF))

        val state = fixture.viewModel.uiState
        val session = state.snapshot.sessions.single()
        assertEquals(stateBeforeSwitch.activeTaskId, state.activeTaskId)
        assertEquals(stateBeforeSwitch.activeSessionId, state.activeSessionId)
        assertTrue(state.running)
        assertEquals(stateBeforeSwitch.elapsedSeconds, state.elapsedSeconds)
        assertEquals(
            stateBeforeSwitch.activeSessionElapsedSeconds,
            state.activeSessionElapsedSeconds,
        )
        assertEquals(1, state.todayProgress.repetitionsFor(task.id))
        assertEquals(1, session.repetitionsCompleted)
        assertEquals(PracticeMode.PLAY_YOURSELF, state.mode)
        assertEquals(PracticeMode.PLAY_YOURSELF, session.mode)
        assertEquals(PracticeMode.entries.toSet(), session.modesUsed)
        assertEquals(PracticeMode.entries.toSet(), state.activeSessionModesUsed)
        assertEquals(
            listOf("Switched to Play along", "Switched to Play solo"),
            state.snapshot.events
                .map(PracticeEvent::event)
                .filter { it.startsWith("Switched to") },
        )
    }

    @Test
    fun switchProfilesKeepsSnapshotsIsolated() {
        val first = profile(id = "child-a", name = "Avery")
        val second = profile(id = "child-b", name = "Bailey")
        val firstTask = task(profile = first, id = "task-a", title = "A scales")
        val secondTask = task(profile = second, id = "task-b", title = "B scales")
        val fixture = fixture(
            profiles = listOf(first, second),
            snapshots = mapOf(
                first.id to PracticeSnapshot(tasks = listOf(firstTask)),
                second.id to PracticeSnapshot(tasks = listOf(secondTask)),
            ),
        )

        assertEquals(listOf(firstTask), fixture.viewModel.uiState.todayTasks)
        assertTrue(fixture.viewModel.selectChildProfile(second.id))
        assertEquals(second.id, fixture.viewModel.studentProfile.id)
        assertEquals(listOf(secondTask), fixture.viewModel.uiState.todayTasks)

        assertTrue(fixture.viewModel.selectChildProfile(first.id))
        assertEquals(first.id, fixture.viewModel.studentProfile.id)
        assertEquals(listOf(firstTask), fixture.viewModel.uiState.todayTasks)
        assertEquals(listOf(firstTask), fixture.practiceStore.snapshot(first.id).tasks)
        assertEquals(listOf(secondTask), fixture.practiceStore.snapshot(second.id).tasks)
    }

    @Test
    fun timerTickAndDateRolloverInterruptActiveSession() {
        val profile = profile()
        val task = task(profile = profile, repetitions = 3)
        val fixture = fixture(
            profiles = listOf(profile),
            snapshots = mapOf(profile.id to PracticeSnapshot(tasks = listOf(task))),
        )

        fixture.viewModel.startPractice(task)
        fixture.viewModel.recordRepetition(task)
        fixture.clock.elapsedRealtimeMillis += 7_000L
        fixture.ticker.tick()
        fixture.clock.currentDate = DateKey("2026-11-02")
        fixture.clock.wallClockMillis += 7_000L

        fixture.viewModel.refreshDate()

        val state = fixture.viewModel.uiState
        val oldProgress = state.snapshot.progressByDate.getValue(DateKey("2026-11-01"))
        val session = state.snapshot.sessions.single()
        assertEquals(DateKey("2026-11-02"), state.currentDate)
        assertFalse(state.running)
        assertNull(state.activeTaskId)
        assertNull(state.activeSessionId)
        assertEquals(0L, state.elapsedSeconds)
        assertTrue(state.todayTasks.isEmpty())
        assertEquals(7L, oldProgress.elapsedSeconds)
        assertEquals(1, oldProgress.repetitionsFor(task.id))
        assertEquals(7L, session.elapsedSeconds)
        assertEquals(PracticeSessionStatus.INTERRUPTED, session.status)
    }

    @Test
    fun saveThenEditAssignmentBumpsTaskAndLocalRevision() {
        val profile = profile()
        val fixture = fixture(profiles = listOf(profile))
        unlockAdultSpace(fixture.viewModel)

        assertTrue(
            fixture.viewModel.saveAssignment(
                taskId = null,
                assignedDate = DateKey("2026-11-01"),
                titleText = "Open strings",
                sourceText = "",
                startText = "0",
                endText = "",
                repetitionsText = "3",
                coachNote = "Keep the bow straight",
                whyImportant = "Build a clear tone",
                sheetMusicUri = null,
                assignmentType = AssignmentType.TECHNIQUE,
                goalType = PracticeGoalType.REPETITIONS,
                targetMinutesText = "10",
                enabledModes = setOf(PracticeMode.PLAY_YOURSELF),
            ),
        )
        val created = fixture.viewModel.uiState.snapshot.tasks.single()
        assertEquals(1, created.revision)
        assertEquals(1L, fixture.familyStore.localRevision(profile.id))

        assertTrue(
            fixture.viewModel.saveAssignment(
                taskId = created.id,
                assignedDate = created.assignedDate,
                titleText = "Open strings slowly",
                sourceText = created.source,
                startText = created.startSeconds.toString(),
                endText = "",
                repetitionsText = created.repetitions.toString(),
                coachNote = created.coachNote,
                whyImportant = created.whyImportant,
                sheetMusicUri = created.sheetMusicUri,
                assignmentType = created.assignmentType,
                goalType = created.goalType,
                targetMinutesText = "10",
                enabledModes = created.enabledModes,
            ),
        )

        val edited = fixture.viewModel.uiState.snapshot.tasks.single()
        assertEquals(created.id, edited.id)
        assertEquals("Open strings slowly", edited.title)
        assertEquals(2, edited.revision)
        assertEquals(2L, fixture.familyStore.localRevision(profile.id))
        val history = fixture.viewModel.uiState.assignmentHistoryFor(created.id).single()
        assertEquals(1, history.revision)
        assertEquals(fixture.clock.wallClockMillis, history.changedAtMillis)
        assertEquals(setOf("title"), history.changedFields)
        assertEquals(created, history.previousAssignment)
        assertEquals(
            created,
            fixture.viewModel.uiState.assignmentAtRevision(created.id, created.revision),
        )
    }

    @Test
    fun archiveAssignmentHidesTodayButPreservesHistory() {
        val profile = profile()
        val task = task(profile = profile)
        val progress = DailyProgress(
            date = task.assignedDate,
            childProfileId = profile.id,
            completedTaskIds = setOf(task.id),
            repetitionsByTask = mapOf(task.id to task.repetitions),
        )
        val session = session(profile, task, status = PracticeSessionStatus.COMPLETED)
        val fixture = fixture(
            profiles = listOf(profile),
            snapshots = mapOf(
                profile.id to PracticeSnapshot(
                    tasks = listOf(task),
                    progressByDate = mapOf(task.assignedDate to progress),
                    sessions = listOf(session),
                ),
            ),
        )
        unlockAdultSpace(fixture.viewModel)

        fixture.viewModel.archiveTask(task)

        val state = fixture.viewModel.uiState
        val archived = state.snapshot.tasks.single()
        assertTrue(state.todayTasks.isEmpty())
        assertEquals(listOf(archived), state.summaryTasks)
        assertEquals(listOf(archived), state.archivedTasksForDate(task.assignedDate))
        assertNotNull(archived.archivedAtMillis)
        assertEquals(task.revision + 1, archived.revision)
        assertEquals(
            task,
            state.assignmentAtRevision(task.id, task.revision),
        )
        assertEquals(progress, state.snapshot.progressByDate.getValue(task.assignedDate))
        assertEquals(listOf(session), state.snapshot.sessions)
    }

    @Test
    fun deleteProfileRemovesStoredDataAndManagedRecordings() {
        val first = profile(id = "child-a", name = "Avery")
        val second = profile(id = "child-b", name = "Bailey")
        val recordingDirectory = temporaryFolder.newFolder("managed-recordings")
        val recordingFile = File(
            recordingDirectory,
            practiceRecordingFileName(
                practiceDate = DateKey("2026-11-01"),
                studentName = second.name,
                assignmentTitle = "Boreas",
                takeNumber = 1,
            ),
        )
        recordingFile.writeBytes(byteArrayOf(1, 2, 3))
        val secondSnapshot = PracticeSnapshot(
            events = listOf(
                PracticeEvent(
                    id = "recording-event",
                    date = DateKey("2026-11-01"),
                    taskId = "boreas",
                    taskTitle = "Boreas",
                    event = "Practice take saved privately",
                    atMillis = 1_000L,
                    videoPath = recordingFile.absolutePath,
                    videoFileName = recordingFile.name,
                    childProfileId = second.id,
                ),
            ),
        )
        val fixture = fixture(
            profiles = listOf(first, second),
            snapshots = mapOf(
                first.id to PracticeSnapshot(),
                second.id to secondSnapshot,
            ),
            recordingDirectory = recordingDirectory,
        )
        unlockAdultSpace(fixture.viewModel)

        assertTrue(fixture.viewModel.deleteChildProfile(second.id))

        assertFalse(recordingFile.exists())
        assertTrue(second.id in fixture.practiceStore.deletedProfileIds)
        assertFalse(fixture.practiceStore.hasSnapshot(second.id))
        assertEquals(listOf(first), fixture.viewModel.household.childProfiles)
        assertTrue(fixture.viewModel.uiState.message.contains("Deleted Bailey"))
    }

    @Test
    fun adultGateLocksAndUnlocksParentPages() {
        val fixture = fixture(profiles = listOf(profile()))

        fixture.viewModel.setPage(AppPage.ASSIGN)
        assertEquals(AppPage.TODAY, fixture.viewModel.uiState.page)
        assertTrue(fixture.viewModel.uiState.message.contains("parent PIN", ignoreCase = true))

        assertTrue(fixture.viewModel.createAdultPin("0319", "0319"))
        assertTrue(fixture.viewModel.adultUnlocked)
        fixture.viewModel.setPage(AppPage.ASSIGN)
        assertEquals(AppPage.ASSIGN, fixture.viewModel.uiState.page)

        fixture.viewModel.lockAdultSpace()
        assertFalse(fixture.viewModel.adultUnlocked)
        assertEquals(AppPage.TODAY, fixture.viewModel.uiState.page)
        assertFalse(fixture.viewModel.unlockAdultSpace("0000"))
        assertTrue(fixture.viewModel.unlockAdultSpace("0319"))
        fixture.viewModel.setPage(AppPage.SUMMARY)
        assertEquals(AppPage.SUMMARY, fixture.viewModel.uiState.page)
    }

    @Test
    fun excelMergeReportsDuplicateWithoutAddingAnotherTask() {
        val profile = profile()
        val existing = task(profile = profile, id = "existing", title = "Tremolo")
        val imported = task(profile = profile, id = "imported", title = "Shifting")
        val workbookStore = ViewModelWorkbookStoreFake(
            readResult = WorkbookAssignmentImportResult(
                assignments = listOf(
                    workbookAssignment(imported, id = imported.id),
                    workbookAssignment(existing, id = "excel-copy"),
                ),
                errors = emptyList(),
            ),
        )
        val fixture = fixture(
            profiles = listOf(profile),
            snapshots = mapOf(
                profile.id to PracticeSnapshot(tasks = listOf(existing)),
            ),
            workbookStore = workbookStore,
        )
        unlockAdultSpace(fixture.viewModel)

        fixture.viewModel.connectExistingLocalWorkbook("content://practice/plan")

        assertEquals(1, workbookStore.readCount)
        assertEquals(1, workbookStore.writeCount)
        assertEquals(
            setOf(existing.id, imported.id),
            fixture.viewModel.uiState.snapshot.tasks.mapTo(mutableSetOf(), PracticeTask::id),
        )
        assertTrue(
            fixture.viewModel.uiState.message.contains(
                "duplicate assignment",
                ignoreCase = true,
            ),
        )
    }

    @Test
    fun profileManagementValidatesSavesExportsAndDeletesActiveProfile() {
        val original = profile(id = "child-a", name = "Avery")
        val fixture = fixture(profiles = listOf(original))
        unlockAdultSpace(fixture.viewModel)

        assertTrue(fixture.viewModel.adultPinConfigured)
        assertFalse(fixture.viewModel.addChildProfile("", "Violin"))
        assertTrue(fixture.viewModel.uiState.message.contains("name", ignoreCase = true))

        assertTrue(fixture.viewModel.addChildProfile("Jordan", "Cello"))
        val addedId = fixture.viewModel.studentProfile.id
        assertEquals("Jordan", fixture.viewModel.studentProfile.name)
        assertTrue(fixture.practiceStore.hasSnapshot(addedId))

        assertFalse(fixture.viewModel.saveProfile("", "Viola"))
        assertTrue(fixture.viewModel.saveProfile("Jordan Lee", "Viola"))
        assertEquals("Jordan Lee", fixture.viewModel.studentProfile.name)
        assertEquals("Viola", fixture.viewModel.studentProfile.instrument)
        assertEquals(1L, fixture.familyStore.localRevision(addedId))
        assertTrue(
            fixture.viewModel.suggestedProfileArchiveFileName(addedId).endsWith(".zip"),
        )

        fixture.viewModel.exportChildProfileArchive(addedId, "not-a-content-uri")
        assertTrue(fixture.viewModel.uiState.message.contains("file picker"))

        assertTrue(fixture.viewModel.deleteChildProfile(addedId))
        assertEquals(original.id, fixture.viewModel.studentProfile.id)
        assertTrue(addedId in fixture.practiceStore.deletedProfileIds)
    }

    @Test
    fun summaryControlsMessagesAndExternalActionLockingWorkTogether() {
        val profile = profile()
        val oldTask = task(
            profile = profile,
            id = "old-task",
            title = "Old scales",
            date = DateKey("2026-10-30"),
        )
        val todayTask = task(profile = profile, id = "today-task")
        val fixture = fixture(
            profiles = listOf(profile),
            snapshots = mapOf(
                profile.id to PracticeSnapshot(tasks = listOf(oldTask, todayTask)),
            ),
        )
        unlockAdultSpace(fixture.viewModel)

        fixture.viewModel.showPreviousSummaryDate()
        assertEquals(DateKey("2026-10-30"), fixture.viewModel.uiState.summaryDate)
        fixture.viewModel.showNextSummaryDate()
        assertEquals(DateKey("2026-11-01"), fixture.viewModel.uiState.summaryDate)
        fixture.viewModel.showPreviousSummaryDate()
        fixture.viewModel.showTodaySummary()
        assertEquals(DateKey("2026-11-01"), fixture.viewModel.uiState.summaryDate)

        fixture.viewModel.setMode(PracticeMode.PLAY_YOURSELF)
        assertEquals(PracticeMode.PLAY_YOURSELF, fixture.viewModel.uiState.mode)
        fixture.viewModel.setMessage("Ready")
        fixture.viewModel.clearMessage()
        fixture.viewModel.clearMessage()
        assertTrue(fixture.viewModel.uiState.message.isBlank())

        assertTrue(fixture.viewModel.beginAdultExternalAction())
        fixture.viewModel.setPage(AppPage.ASSIGN)
        fixture.viewModel.pauseForBackground()
        assertTrue(fixture.viewModel.adultUnlocked)
        assertEquals(AppPage.ASSIGN, fixture.viewModel.uiState.page)

        fixture.viewModel.endAdultExternalAction()
        fixture.viewModel.pauseForBackground()
        assertFalse(fixture.viewModel.adultUnlocked)
        assertEquals(AppPage.TODAY, fixture.viewModel.uiState.page)
    }

    @Test
    fun assignmentConvenienceCsvRestoreAndRemovePreserveSessionHistory() {
        val profile = profile()
        val fixture = fixture(profiles = listOf(profile))
        unlockAdultSpace(fixture.viewModel)

        assertTrue(
            fixture.viewModel.addAssignment(
                titleText = "Bow hold",
                sourceText = "",
                startText = "0",
                endText = "",
                repetitionsText = "2",
                coachNote = "Relax the thumb",
                whyImportant = "Keep the hand flexible",
                sheetMusicUri = null,
            ),
        )
        val created = fixture.viewModel.uiState.snapshot.tasks.single()
        val csv = fixture.viewModel.exportTodayCsv()
        assertEquals(csv, fixture.viewModel.exportCsvForDate(DateKey("2026-11-01")))
        assertTrue(csv.contains("Bow hold"))

        fixture.viewModel.archiveTask(created)
        val archived = fixture.viewModel.uiState.snapshot.tasks.single()
        fixture.viewModel.restoreTask(archived)
        val restored = fixture.viewModel.uiState.snapshot.tasks.single()
        assertNull(restored.archivedAtMillis)

        fixture.viewModel.startPractice(restored)
        assertTrue(fixture.viewModel.finishPractice())
        fixture.viewModel.removeTask(restored)
        assertTrue(fixture.viewModel.uiState.snapshot.tasks.isEmpty())
        assertNull(fixture.viewModel.uiState.selectedTaskId)
        assertEquals(1, fixture.viewModel.uiState.snapshot.sessions.size)

        fixture.viewModel.importCsv(csv)
        assertEquals("Bow hold", fixture.viewModel.uiState.snapshot.tasks.single().title)
        assertEquals(1, fixture.viewModel.uiState.snapshot.sessions.size)
    }

    @Test
    fun localWorkbookCreateSaveAndLoadFlowsUseInjectedStorage() {
        val profile = profile()
        val workbookStore = ViewModelWorkbookStoreFake()
        val fixture = fixture(
            profiles = listOf(profile),
            workbookStore = workbookStore,
        )
        unlockAdultSpace(fixture.viewModel)

        assertTrue(fixture.viewModel.suggestedExcelFileName().endsWith(".xlsx"))
        fixture.viewModel.createLocalWorkbook("content://practice/new-plan")
        assertEquals(LocalFileStatus.READY, fixture.viewModel.localFileState.status)
        assertEquals(1, workbookStore.writeCount)

        fixture.viewModel.saveLocalWorkbook()
        assertEquals(2, workbookStore.writeCount)

        val imported = task(
            profile = profile,
            id = "excel-assignment",
            title = "Seating excerpt",
        )
        workbookStore.readResult = WorkbookAssignmentImportResult(
            assignments = listOf(workbookAssignment(imported, imported.id)),
            errors = emptyList(),
        )
        fixture.viewModel.loadAssignmentsFromLocalFile()

        assertEquals(1, workbookStore.readCount)
        assertEquals(listOf(imported), fixture.viewModel.uiState.snapshot.tasks)
        assertTrue(fixture.viewModel.uiState.message.contains("Loaded 1 assignment"))
    }

    @Test
    fun timerReflectionAndRecordingLifecycleAreOrchestratedByViewModel() {
        val profile = profile()
        val task = task(profile = profile, repetitions = 2)
        val fixture = fixture(
            profiles = listOf(profile),
            snapshots = mapOf(profile.id to PracticeSnapshot(tasks = listOf(task))),
        )
        unlockAdultSpace(fixture.viewModel)

        fixture.viewModel.setMode(PracticeMode.PLAY_YOURSELF)
        fixture.viewModel.startPractice(task)
        fixture.clock.elapsedRealtimeMillis += 2_000L
        fixture.viewModel.pauseTimer()
        assertFalse(fixture.viewModel.uiState.running)
        assertTrue(
            fixture.viewModel.uiState.snapshot.events.any {
                it.event == "Practice timer paused"
            },
        )

        fixture.viewModel.resumeActivePractice()
        assertTrue(fixture.viewModel.uiState.running)
        fixture.viewModel.setRecordingActive(true)
        assertFalse(fixture.viewModel.finishPractice())
        fixture.viewModel.setRecordingActive(false)

        val recordingName = practiceRecordingFileName(
            practiceDate = DateKey("2026-11-01"),
            studentName = profile.name,
            assignmentTitle = task.title,
            takeNumber = 1,
        )
        val recordingFile = fixture.viewModel.createRecordingFile(recordingName)
        recordingFile.writeBytes(byteArrayOf(1, 2, 3))
        fixture.viewModel.recordingSaved(
            task,
            RecordingFile(
                uri = recordingFile.toURI().toString(),
                path = recordingFile.absolutePath,
                fileName = recordingFile.name,
            ),
        )
        fixture.viewModel.toggleCompleted(task)
        assertTrue(fixture.viewModel.finishPractice())
        fixture.viewModel.submitReflection(
            PracticeReflection.PROUD,
            studentConfident = true,
        )

        val session = fixture.viewModel.uiState.snapshot.sessions.single()
        assertEquals(PracticeReflection.PROUD, session.reflection)
        assertEquals(MasteryStatus.STUDENT_CONFIDENT, session.masteryStatus)
        val outsideRecording = temporaryFolder.newFile("outside-recording.mp4")
        assertFalse(
            fixture.viewModel.deleteRecording(outsideRecording.absolutePath),
        )
        assertTrue(fixture.viewModel.deleteRecording(recordingFile.absolutePath))
        assertFalse(recordingFile.exists())
        assertTrue(fixture.viewModel.deleteRecording(recordingFile.absolutePath))
        assertTrue(fixture.viewModel.uiState.message.contains("already unavailable"))

        val undeletable = File(
            recordingFile.parentFile,
            practiceRecordingFileName(
                practiceDate = DateKey("2026-11-01"),
                studentName = profile.name,
                assignmentTitle = task.title,
                takeNumber = 2,
            ),
        )
        assertTrue(undeletable.mkdir())
        File(undeletable, "child").writeText("keep directory non-empty")
        assertFalse(fixture.viewModel.deleteRecording(undeletable.absolutePath))
        assertTrue(fixture.viewModel.uiState.message.contains("could not be deleted"))
    }

    @Test
    fun initializationAndProfileSwitchGuardsCloseInterruptedWork() {
        val first = profile(id = "child-a", name = "Avery")
        val second = profile(id = "child-b", name = "Bailey")
        val task = task(profile = first, id = "active-task")
        val openSession = session(
            profile = first,
            task = task,
            status = PracticeSessionStatus.ACTIVE,
        ).copy(endedAtMillis = null)
        val fixture = fixture(
            profiles = listOf(first, second),
            snapshots = mapOf(
                first.id to PracticeSnapshot(
                    tasks = listOf(task),
                    sessions = listOf(openSession),
                ),
                second.id to PracticeSnapshot(),
            ),
        )

        assertEquals(
            PracticeSessionStatus.INTERRUPTED,
            fixture.viewModel.uiState.snapshot.sessions.single().status,
        )
        assertTrue(fixture.viewModel.uiState.message.contains("closed safely"))
        assertFalse(fixture.viewModel.selectChildProfile("missing"))
        assertTrue(fixture.viewModel.selectChildProfile(first.id))

        fixture.viewModel.startPractice(task)
        fixture.viewModel.setPage(AppPage.ASSIGN)
        assertEquals(AppPage.TODAY, fixture.viewModel.uiState.page)
        assertFalse(fixture.viewModel.selectChildProfile(second.id))
        assertTrue(fixture.viewModel.finishPractice())

        fixture.viewModel.setRecordingActive(true)
        assertFalse(fixture.viewModel.selectChildProfile(second.id))
        fixture.viewModel.setRecordingActive(false)
        assertTrue(fixture.viewModel.selectChildProfile(second.id))
    }

    private fun fixture(
        profiles: List<ChildProfile>,
        activeProfileId: String = profiles.first().id,
        snapshots: Map<String, PracticeSnapshot> = emptyMap(),
        workbookStore: ViewModelWorkbookStoreFake = ViewModelWorkbookStoreFake(),
        recordingDirectory: File = temporaryFolder.newFolder(
            "recordings-${UUID.randomUUID()}",
        ),
    ): ViewModelFixture {
        val household = Household(
            id = "household",
            childProfiles = profiles,
            activeChildProfileId = activeProfileId,
        )
        val practiceStore = ViewModelPracticeStoreFake(snapshots)
        val familyStore = ViewModelFamilyStoreFake(household)
        val clock = ViewModelClock()
        val ticker = ViewModelTimerTicker()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        val recordingManager = RecordingManager(
            managedDirectoriesProvider = { listOf(recordingDirectory) },
            contentUriReadable = { true },
        )
        val viewModel = PracticeViewModel(
            store = practiceStore,
            familyStore = familyStore,
            localWorkbookStore = workbookStore,
            recordingManager = recordingManager,
            profileArchiveDestination = ProfileArchiveDestination {
                ByteArrayOutputStream()
            },
            elapsedRealtime = { clock.elapsedRealtimeMillis },
            wallClock = { clock.wallClockMillis },
            dateProvider = { clock.currentDate },
            coroutineScope = scope,
            timerTicker = ticker,
        )
        return ViewModelFixture(
            viewModel = viewModel,
            practiceStore = practiceStore,
            familyStore = familyStore,
            workbookStore = workbookStore,
            clock = clock,
            ticker = ticker,
        )
    }

    private fun unlockAdultSpace(viewModel: PracticeViewModel) {
        assertTrue(viewModel.createAdultPin("0319", "0319"))
    }

    private fun profile(
        id: String = "child",
        name: String = "Student",
    ): ChildProfile = ChildProfile(
        id = id,
        name = name,
        instrument = "Violin",
    )

    private fun task(
        profile: ChildProfile,
        id: String = "assignment",
        title: String = "Scales",
        date: DateKey = DateKey("2026-11-01"),
        repetitions: Int = 2,
    ): PracticeTask = PracticeTask(
        id = id,
        childProfileId = profile.id,
        instrument = profile.instrument,
        assignedDate = date,
        title = title,
        source = "",
        sourceType = SourceType.NONE,
        startSeconds = 0,
        endSeconds = null,
        repetitions = repetitions,
        enabledModes = setOf(PracticeMode.PLAY_YOURSELF),
    )

    private fun session(
        profile: ChildProfile,
        task: PracticeTask,
        status: PracticeSessionStatus,
    ): PracticeSession = PracticeSession(
        id = "session-${task.id}",
        childProfileId = profile.id,
        assignmentId = task.id,
        assignmentTitle = task.title,
        instrument = profile.instrument,
        date = task.assignedDate,
        mode = PracticeMode.PLAY_YOURSELF,
        startedAtMillis = 500L,
        endedAtMillis = 1_000L,
        repetitionsCompleted = task.repetitions,
        status = status,
        targetRepetitions = task.repetitions,
    )

    private fun workbookAssignment(
        task: PracticeTask,
        id: String,
    ): WorkbookAssignment = WorkbookAssignment(
        id = id,
        childProfileId = task.childProfileId,
        instrument = task.instrument,
        assignedDate = task.assignedDate,
        title = task.title,
        sourceType = task.sourceType,
        source = task.source,
        startSeconds = task.startSeconds,
        endSeconds = task.endSeconds,
        repetitions = task.repetitions,
        coachNote = task.coachNote,
        whyImportant = task.whyImportant,
        sheetMusicUri = task.sheetMusicUri,
        needsSheetMusicReattachment = false,
        assignmentType = task.assignmentType,
        goalType = task.goalType,
        targetSeconds = task.targetSeconds,
        enabledModes = task.enabledModes,
        revision = task.revision,
        archivedAtMillis = task.archivedAtMillis,
    )
}

private data class ViewModelFixture(
    val viewModel: PracticeViewModel,
    val practiceStore: ViewModelPracticeStoreFake,
    val familyStore: ViewModelFamilyStoreFake,
    val workbookStore: ViewModelWorkbookStoreFake,
    val clock: ViewModelClock,
    val ticker: ViewModelTimerTicker,
)

private class ViewModelClock(
    var currentDate: DateKey = DateKey("2026-11-01"),
    var elapsedRealtimeMillis: Long = 1_000L,
    var wallClockMillis: Long = 1_000_000L,
)

private class ViewModelTimerTicker : PracticeTimerTicker {
    private var job: Job? = null
    private var onTick: (() -> Unit)? = null

    override fun start(scope: CoroutineScope, onTick: () -> Unit): Job {
        this.onTick = onTick
        return Job().also { job = it }
    }

    fun tick() {
        if (job?.isActive == true) onTick?.invoke()
    }
}

private class ViewModelPracticeStoreFake(
    snapshots: Map<String, PracticeSnapshot>,
) : PracticeDataStore {
    private val snapshotsByProfile = snapshots.toMutableMap()
    val deletedProfileIds = mutableSetOf<String>()

    override fun load(
        profile: ChildProfile,
        currentDate: DateKey,
    ): PracticeSnapshot = snapshotsByProfile[profile.id] ?: PracticeSnapshot()

    override fun loadWithStatus(
        profile: ChildProfile,
        currentDate: DateKey,
    ): PracticeLoadResult = PracticeLoadResult(load(profile, currentDate))

    override fun replace(profile: ChildProfile, snapshot: PracticeSnapshot) {
        snapshotsByProfile[profile.id] = snapshot.scopedTo(profile)
    }

    override fun applyChanges(
        profile: ChildProfile,
        previous: PracticeSnapshot,
        next: PracticeSnapshot,
    ) {
        snapshotsByProfile[profile.id] = next.scopedTo(profile)
    }

    override fun delete(profile: ChildProfile) {
        snapshotsByProfile.remove(profile.id)
        deletedProfileIds += profile.id
    }

    override fun referencedRecordingPaths(profiles: List<ChildProfile>): Set<String> =
        profiles.flatMap { profile ->
            snapshotsByProfile[profile.id]
                ?.events
                .orEmpty()
                .mapNotNull(PracticeEvent::videoPath)
        }.toSet()

    fun snapshot(childProfileId: String): PracticeSnapshot =
        snapshotsByProfile[childProfileId] ?: PracticeSnapshot()

    fun hasSnapshot(childProfileId: String): Boolean =
        snapshotsByProfile.containsKey(childProfileId)
}

private class ViewModelFamilyStoreFake(
    initialHousehold: Household,
) : FamilyDataStore {
    private var household = initialHousehold
    private var adultPin: String? = null
    private val localRevisions = mutableMapOf<String, Long>()
    private val lastFileSaveRevisions = mutableMapOf<String, Long>()
    private val lastFileSaveTimes = mutableMapOf<String, Long>()

    override fun loadHousehold(): Household = household

    override fun saveHousehold(household: Household) {
        this.household = household
    }

    override fun saveChildProfile(profile: ChildProfile): Household {
        household = household.copy(
            childProfiles = household.childProfiles.map {
                if (it.id == profile.id) profile else it
            },
            activeChildProfileId = profile.id,
        )
        return household
    }

    override fun addChildProfile(profile: ChildProfile): Household {
        household = household.copy(childProfiles = household.childProfiles + profile)
        return household
    }

    override fun setActiveChildProfile(childProfileId: String): Household {
        household = household.copy(activeChildProfileId = childProfileId)
        return household
    }

    override fun removeChildProfile(childProfileId: String): Household {
        val remaining = household.childProfiles.filterNot { it.id == childProfileId }
        household = household.copy(
            childProfiles = remaining,
            activeChildProfileId = household.activeChildProfileId
                .takeUnless { it == childProfileId }
                ?: remaining.first().id,
        )
        localRevisions.remove(childProfileId)
        lastFileSaveRevisions.remove(childProfileId)
        lastFileSaveTimes.remove(childProfileId)
        return household
    }

    override fun hasAdultPin(): Boolean = adultPin != null

    override fun setAdultPin(pin: String): Boolean {
        if (!isValidAdultPin(pin)) return false
        adultPin = pin
        return true
    }

    override fun verifyAdultPin(pin: String): Boolean = pin == adultPin

    override fun loadLocalRevision(childProfileId: String): Long =
        localRevisions[childProfileId] ?: 0L

    override fun bumpLocalRevision(childProfileId: String): Long {
        val next = loadLocalRevision(childProfileId) + 1L
        localRevisions[childProfileId] = next
        return next
    }

    override fun loadLastFileSaveRevision(childProfileId: String): Long =
        lastFileSaveRevisions[childProfileId] ?: -1L

    override fun saveLastFileSaveRevision(childProfileId: String, revision: Long) {
        lastFileSaveRevisions[childProfileId] = revision
    }

    override fun loadLastFileSaveAt(childProfileId: String): Long? =
        lastFileSaveTimes[childProfileId]

    override fun saveLastFileSaveAt(childProfileId: String, atMillis: Long) {
        lastFileSaveTimes[childProfileId] = atMillis
    }

    fun localRevision(childProfileId: String): Long =
        loadLocalRevision(childProfileId)
}

private class ViewModelWorkbookStoreFake(
    var readResult: WorkbookAssignmentImportResult =
        WorkbookAssignmentImportResult(emptyList(), emptyList()),
) : WorkbookStorage {
    var readCount = 0
    var writeCount = 0

    override fun describe(uriText: String?): LocalWorkbookFile? =
        uriText?.let { LocalWorkbookFile(uri = it, name = "LK Practice.xlsx") }

    override suspend fun write(
        uriText: String,
        profile: StudentProfile,
        snapshot: PracticeSnapshot,
        updatedAtMillis: Long,
        requireExistingTemplate: Boolean,
    ): LocalWorkbookSave {
        writeCount += 1
        return LocalWorkbookSave(
            uri = uriText,
            name = "LK Practice.xlsx",
            savedAtMillis = updatedAtMillis,
        )
    }

    override suspend fun readAssignments(
        uriText: String,
    ): WorkbookAssignmentImportResult {
        readCount += 1
        return readResult
    }
}
