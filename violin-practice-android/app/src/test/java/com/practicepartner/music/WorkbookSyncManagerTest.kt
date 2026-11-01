package com.practicepartner.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkbookSyncManagerTest {
    @Test
    fun activateReportsUnsavedRevisions() {
        var profile = profile(workbookUri = "content://practice/plan")
        val storage = FakeWorkbookStorage()
        val settings = FakeWorkbookSyncSettings(
            localRevision = 3L,
            lastFileSaveRevision = 2L,
        )
        val manager = manager(
            storage = storage,
            settings = settings,
            profile = { profile },
            updateProfile = { updated -> profile = updated },
        )

        manager.activate()

        assertEquals(LocalFileStatus.UNSAVED, manager.state.status)
        assertTrue(manager.state.hasUnsavedChanges)
        assertEquals("LK Practice.xlsx", manager.state.fileName)
    }

    @Test
    fun createWorkbookUpdatesProfileAndMarksFileReady() {
        var profile = profile()
        val storage = FakeWorkbookStorage()
        val settings = FakeWorkbookSyncSettings()
        val messages = mutableListOf<String>()
        val manager = manager(
            storage = storage,
            settings = settings,
            profile = { profile },
            updateProfile = { updated -> profile = updated },
            setMessage = messages::add,
        )
        manager.activate()

        manager.createLocalWorkbook("content://practice/new-plan")

        assertEquals(1, storage.writeCount)
        assertEquals("content://practice/new-plan", profile.excelWorkbookUri)
        assertEquals(LocalFileStatus.READY, manager.state.status)
        assertFalse(manager.state.hasUnsavedChanges)
        assertEquals(1_234L, manager.state.lastSavedAtMillis)
        assertEquals(
            "Created and verified the local LK Practice Excel file.",
            messages.last(),
        )
    }

    @Test
    fun localMutationUsesDebouncedAutoSavePath() {
        var profile = profile(workbookUri = "content://practice/plan")
        val storage = FakeWorkbookStorage()
        val settings = FakeWorkbookSyncSettings(lastFileSaveRevision = 0L)
        val manager = manager(
            storage = storage,
            settings = settings,
            profile = { profile },
            updateProfile = { updated -> profile = updated },
            waitForAutoSave = {},
        )
        manager.activate()

        manager.recordLocalMutation()

        assertEquals(1, storage.writeCount)
        assertEquals(1L, settings.localRevision)
        assertEquals(1L, settings.lastFileSaveRevision)
        assertEquals(LocalFileStatus.READY, manager.state.status)
    }

    @Test
    fun workbookMergeDoesNotMoveAssignmentThatHasProgress() {
        val existing = assignment(id = "assignment", assignedDate = "2026-11-01")
        val snapshot = PracticeSnapshot(
            tasks = listOf(existing),
            progressByDate = mapOf(
                "2026-11-01" to DailyProgress(
                    date = "2026-11-01",
                    childProfileId = "child",
                    completedTaskIds = setOf(existing.id),
                ),
            ),
        )
        val imported = workbookAssignment(
            id = existing.id,
            assignedDate = "2026-11-02",
        )

        val result = mergeWorkbookAssignments(
            assignments = listOf(imported),
            profile = profile(),
            snapshot = snapshot,
            canReadContentUri = { true },
        )

        assertEquals(listOf(existing), result.tasks)
        assertEquals(0, result.loadedCount)
        assertTrue(result.errors.single().contains("cannot move dates"))
    }

    private fun manager(
        storage: FakeWorkbookStorage,
        settings: FakeWorkbookSyncSettings,
        profile: () -> ChildProfile,
        updateProfile: (ChildProfile) -> Unit,
        setMessage: (String) -> Unit = {},
        waitForAutoSave: suspend () -> Unit = {
            kotlinx.coroutines.awaitCancellation()
        },
    ): WorkbookSyncManager {
        var snapshot = PracticeSnapshot()
        return WorkbookSyncManager(
            scope = CoroutineScope(Dispatchers.Unconfined),
            workbookStore = storage,
            settings = settings,
            wallClock = { 1_234L },
            profileProvider = profile,
            snapshotProvider = { snapshot },
            currentDateProvider = { "2026-11-01" },
            activeTaskIdProvider = { null },
            recordingActiveProvider = { false },
            canReadContentUri = { true },
            persistSnapshot = { snapshot = it },
            updateSnapshot = { _, transform -> snapshot = transform(snapshot) },
            updateProfile = { updated, _ -> updateProfile(updated) },
            clearSelectedTask = {},
            setMessage = setMessage,
            waitForAutoSave = waitForAutoSave,
        )
    }

    private fun profile(workbookUri: String? = null): ChildProfile =
        ChildProfile(
            id = "child",
            name = "Lincoln",
            instrument = "Violin",
            excelWorkbookUri = workbookUri,
            excelWorkbookName = workbookUri?.let { "LK Practice.xlsx" },
        )

    private fun assignment(
        id: String,
        assignedDate: String,
    ): PracticeTask = PracticeTask(
        id = id,
        childProfileId = "child",
        instrument = "Violin",
        assignedDate = assignedDate,
        title = "Scales",
        source = "",
        sourceType = SourceType.NONE,
        startSeconds = 0,
        endSeconds = null,
        repetitions = 3,
    )

    private fun workbookAssignment(
        id: String,
        assignedDate: String,
    ): WorkbookAssignment = WorkbookAssignment(
        id = id,
        childProfileId = "child",
        instrument = "Violin",
        assignedDate = assignedDate,
        title = "Scales",
        sourceType = SourceType.NONE,
        source = "",
        startSeconds = 0,
        endSeconds = null,
        repetitions = 3,
        coachNote = "",
        whyImportant = "",
        sheetMusicUri = null,
        needsSheetMusicReattachment = false,
        assignmentType = AssignmentType.TECHNIQUE,
        goalType = PracticeGoalType.REPETITIONS,
        targetSeconds = 0,
        enabledModes = setOf(PracticeMode.PLAY_YOURSELF),
        revision = 1,
        archivedAtMillis = null,
    )
}

private class FakeWorkbookStorage : WorkbookStorage {
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
    ): WorkbookAssignmentImportResult =
        WorkbookAssignmentImportResult(emptyList(), emptyList())
}

private class FakeWorkbookSyncSettings(
    var localRevision: Long = 0L,
    var lastFileSaveRevision: Long = -1L,
) : WorkbookSyncSettings {
    private var lastFileSaveAt: Long? = null
    private var household = Household(
        id = "household",
        childProfiles = listOf(
            ChildProfile(id = "child", name = "Lincoln", instrument = "Violin"),
        ),
        activeChildProfileId = "child",
    )

    override fun loadLocalRevision(childProfileId: String): Long = localRevision

    override fun bumpLocalRevision(childProfileId: String): Long {
        localRevision += 1L
        return localRevision
    }

    override fun loadLastFileSaveRevision(childProfileId: String): Long =
        lastFileSaveRevision

    override fun saveLastFileSaveRevision(childProfileId: String, revision: Long) {
        lastFileSaveRevision = revision
    }

    override fun loadLastFileSaveAt(childProfileId: String): Long? = lastFileSaveAt

    override fun saveLastFileSaveAt(childProfileId: String, atMillis: Long) {
        lastFileSaveAt = atMillis
    }

    override fun saveChildProfile(profile: ChildProfile): Household {
        household = household.copy(childProfiles = listOf(profile))
        return household
    }
}
