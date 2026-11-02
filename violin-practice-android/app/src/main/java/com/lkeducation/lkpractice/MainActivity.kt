package com.lkeducation.lkpractice

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

private val DateKeySaver = Saver<DateKey, String>(
    save = { date -> date.value },
    restore = { value -> runCatching { DateKey.parse(value) }.getOrNull() },
)

class MainActivity : ComponentActivity() {
    private val practiceViewModelDelegate = viewModels<PracticeViewModel> {
        PracticeViewModel.factory(applicationContext)
    }
    private val practiceViewModel by practiceViewModelDelegate
    private val privacyConsentStore by lazy(LazyThreadSafetyMode.NONE) {
        PrivacyConsentStore(applicationContext)
    }
    private val appDataDeletionManager by lazy(LazyThreadSafetyMode.NONE) {
        AppDataDeletionManager(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var consentAccepted by remember {
                mutableStateOf(privacyConsentStore.hasAcceptedCurrentPolicy())
            }
            LkPracticeTheme {
                if (consentAccepted) {
                    PracticeApp(
                        viewModel = practiceViewModel,
                        acceptedAgeRange = privacyConsentStore.acceptedAgeRange(),
                        onDeleteAllData = appDataDeletionManager::deleteAllData,
                    )
                } else {
                    PrivacyConsentScreen(
                        onAccept = { ageRange ->
                            privacyConsentStore.accept(
                                ageRange = ageRange,
                                parentConfirmed = true,
                                privacyPolicyAccepted = true,
                            ).also { accepted ->
                                if (accepted) consentAccepted = true
                            }
                        },
                        onExit = ::finish,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (privacyConsentStore.hasAcceptedCurrentPolicy()) {
            practiceViewModel.refreshDate()
            practiceViewModel.resumeActivePractice()
        }
    }

    override fun onStop() {
        if (practiceViewModelDelegate.isInitialized()) {
            practiceViewModel.pauseForBackground()
        }
        super.onStop()
    }
}

@Composable
private fun PracticeApp(
    viewModel: PracticeViewModel,
    acceptedAgeRange: ChildAgeRange?,
    onDeleteAllData: () -> Boolean,
) {
    val context = LocalContext.current
    val state = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    var form by remember(state.currentDate, viewModel.studentProfile.id) {
        mutableStateOf(
            AssignmentForm(
                startDate = state.currentDate,
                endDate = state.currentDate,
            ),
        )
    }
    var showProfileChooser by rememberSaveable { mutableStateOf(true) }
    var showAdultGate by rememberSaveable { mutableStateOf(false) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var showFamilyProfiles by rememberSaveable { mutableStateOf(false) }
    var showGrownUpTools by rememberSaveable { mutableStateOf(false) }
    var showPrivacySettings by rememberSaveable { mutableStateOf(false) }
    var csvDatePending by rememberSaveable(stateSaver = DateKeySaver) {
        mutableStateOf(state.currentDate)
    }
    var profileArchivePendingId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel.adultUnlocked) {
        if (!viewModel.adultUnlocked) {
            showProfileDialog = false
            showFamilyProfiles = false
            showGrownUpTools = false
            showPrivacySettings = false
        }
    }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbarHostState.showSnackbar(state.message)
            viewModel.clearMessage()
        }
    }

    fun persistReadPermission(uri: Uri): Boolean =
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess

    fun persistReadWritePermission(uri: Uri): Boolean =
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess

    fun openUri(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(intent) }
            .onFailure { viewModel.setMessage("No app is available to open this file.") }
    }

    fun shareVideo(uri: Uri, fileName: String?) {
        if (!viewModel.adultUnlocked) {
            viewModel.setMessage("Unlock Parent Space before sharing a practice video.")
            return
        }
        val title = fileName?.removeSuffix(".mp4")?.takeIf(String::isNotBlank)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (title != null) {
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            clipData = ClipData.newUri(
                context.contentResolver,
                fileName ?: "Practice video",
                uri,
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share practice take"))
        }.onFailure {
            viewModel.setMessage("No compatible sharing app is available.")
        }
    }

    fun openParentSpace() {
        if (viewModel.adultUnlocked) {
            showProfileChooser = false
            showGrownUpTools = true
        } else {
            showAdultGate = true
        }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (persistReadPermission(uri)) {
                form = form.copy(source = uri.toString())
                viewModel.setMessage("Reference video selected.")
            } else {
                viewModel.setMessage(
                    "LK Practice could not keep access to that video. Choose it again from Files.",
                )
            }
        }
        viewModel.endAdultExternalAction()
    }
    val pickSheetMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (persistReadPermission(uri)) {
                form = form.copy(sheetMusicUri = uri.toString())
                viewModel.setMessage("Sheet music attached.")
            } else {
                viewModel.setMessage(
                    "LK Practice could not keep access to that sheet music. Choose it again from Files.",
                )
            }
        }
        viewModel.endAdultExternalAction()
    }
    val createExcelWorkbook = rememberLauncherForActivityResult(
        CreateLocalDocument(XLSX_MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            if (persistReadWritePermission(uri)) {
                viewModel.createLocalWorkbook(uri.toString())
            } else {
                viewModel.setMessage(
                    "LK Practice could not keep permission to update this local file. Choose it again.",
                )
            }
        }
        viewModel.endAdultExternalAction()
    }
    val chooseExcelWorkbook = rememberLauncherForActivityResult(
        OpenLocalDocument(),
    ) { uri ->
        if (uri != null) {
            if (persistReadWritePermission(uri)) {
                viewModel.connectExistingLocalWorkbook(uri.toString())
            } else {
                viewModel.setMessage(
                    "LK Practice could not keep permission to update this local file. Choose it again.",
                )
            }
        }
        viewModel.endAdultExternalAction()
    }
    val createProfileArchive = rememberLauncherForActivityResult(
        CreateLocalDocument(ZIP_MIME_TYPE),
    ) { uri ->
        val profileId = profileArchivePendingId
        if (uri != null && profileId != null) {
            // This is a one-time export. The picker grant is enough for the current write;
            // unlike the configured workbook, LK Practice does not retain access afterward.
            viewModel.exportChildProfileArchive(profileId, uri.toString())
        }
        profileArchivePendingId = null
        viewModel.endAdultExternalAction()
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read this CSV file.")
            }.onSuccess { viewModel.importCsv(it, csvDatePending) }
                .onFailure { viewModel.setMessage(it.message ?: "Could not import the CSV.") }
        }
        viewModel.endAdultExternalAction()
    }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open the destination file.")
                output.bufferedWriter().use {
                    it.write(viewModel.exportCsvForDate(csvDatePending))
                }
            }.onSuccess {
                viewModel.setMessage("Assignments for $csvDatePending were exported.")
            }.onFailure {
                viewModel.setMessage(it.message ?: "Could not export the assignments.")
            }
        }
        viewModel.endAdultExternalAction()
    }
    BackHandler(enabled = !showProfileChooser) {
        when {
            state.activeTaskId != null -> viewModel.finishPractice()
            state.page != AppPage.TODAY -> {
                viewModel.setPage(AppPage.TODAY)
                viewModel.lockAdultSpace()
            }
            else -> {
                viewModel.lockAdultSpace()
                showProfileChooser = true
            }
        }
    }

    SkyAppBackground {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { insets ->
            if (showProfileChooser) {
                ProfileChooserScreen(
                    household = viewModel.household,
                    onSelectChild = { profile ->
                        if (viewModel.selectChildProfile(profile.id)) {
                            viewModel.lockAdultSpace()
                            showProfileChooser = false
                        }
                    },
                    onOpenParentSpace = ::openParentSpace,
                    modifier = Modifier.padding(insets),
                )
            } else {
                Column(Modifier.fillMaxSize().padding(insets)) {
                    AppHeader(
                        state = state,
                        onOpenGrownUpTools = ::openParentSpace,
                        onReturnToToday = {
                            viewModel.setPage(AppPage.TODAY)
                            viewModel.lockAdultSpace()
                        },
                        onSwitchProfile = {
                            viewModel.lockAdultSpace()
                            showProfileChooser = true
                        },
                    )
                    val activeTask = state.activeTask
                    if (activeTask != null) {
                        PracticeSessionPage(
                            state = state,
                            task = activeTask,
                            studentName = viewModel.studentProfile.name,
                            createRecordingFile = viewModel::createRecordingFile,
                            onRecordingSaved = { viewModel.recordingSaved(activeTask, it) },
                            onRecordingChanged = viewModel::setRecordingActive,
                            onCompleteRepetition = { viewModel.recordRepetition(activeTask) },
                            onSwitchMode = viewModel::switchPracticeMode,
                            onResumeTimer = viewModel::resumeActivePractice,
                            onOpenUri = ::openUri,
                            onFinish = { viewModel.finishPractice() },
                        )
                    } else {
                        when (state.page) {
                            AppPage.TODAY -> TodayPage(
                                state = state,
                                studentName = viewModel.studentProfile.name,
                                onSelectMode = viewModel::setMode,
                                onPractice = viewModel::startPractice,
                                onSelfDirectedPractice = viewModel::startSelfDirectedPractice,
                                onSurprisePractice = viewModel::startSurprisePractice,
                                onToggleCompleted = viewModel::toggleCompleted,
                            )
                            AppPage.ASSIGN -> AssignmentPage(
                                state = state,
                                studentName = viewModel.studentProfile.name,
                                instrument = viewModel.studentProfile.instrument,
                                form = form,
                                onFormChanged = { form = it },
                                onPickVideo = {
                                    if (viewModel.beginAdultExternalAction()) {
                                        pickVideo.launch(arrayOf("video/*"))
                                    }
                                },
                                onPickSheetMusic = {
                                    if (viewModel.beginAdultExternalAction()) {
                                        pickSheetMusic.launch(
                                            arrayOf("application/pdf", "image/*"),
                                        )
                                    }
                                },
                                onImport = {
                                    csvDatePending = form.startDate
                                    if (viewModel.beginAdultExternalAction()) {
                                        importCsv.launch(
                                            arrayOf(
                                                "text/csv",
                                                "text/*",
                                                "application/vnd.ms-excel",
                                            ),
                                        )
                                    }
                                },
                                onExport = {
                                    csvDatePending = form.startDate
                                    if (viewModel.beginAdultExternalAction()) {
                                        exportCsv.launch(
                                            "practice-assignment-${form.startDate.value}.csv",
                                        )
                                    }
                                },
                                onAdd = {
                                    val added = viewModel.saveAssignment(
                                        taskId = form.editingTaskId,
                                        startDate = form.startDate,
                                        endDate = form.endDate,
                                        titleText = form.title,
                                        sourceText = form.source,
                                        startText = form.start,
                                        endText = form.end,
                                        repetitionsText = form.repetitions,
                                        coachNote = form.coachNote,
                                        whyImportant = form.whyImportant,
                                        sheetMusicUri = form.sheetMusicUri,
                                        assignmentType = form.assignmentType,
                                        goalType = form.goalType,
                                        targetMinutesText = form.targetMinutes,
                                        enabledModes = form.enabledModes,
                                    )
                                    if (added) {
                                        form = AssignmentForm(
                                            startDate = form.startDate,
                                            endDate = form.endDate,
                                        )
                                    }
                                },
                                onArchive = viewModel::archiveTask,
                                onRestore = viewModel::restoreTask,
                                onRemove = viewModel::removeTask,
                            )
                            AppPage.SUMMARY -> SummaryPage(
                                state = state,
                                onPreviousDate = viewModel::showPreviousSummaryDate,
                                onNextDate = viewModel::showNextSummaryDate,
                                onToday = viewModel::showTodaySummary,
                                onOpenUri = ::openUri,
                                onShare = ::shareVideo,
                                onDeleteRecording = { event ->
                                    event.videoPath?.let(viewModel::deleteRecording) ?: false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    state.pendingReflectionSession?.let { session ->
        PracticeReflectionDialog(
            session = session,
            onSubmit = viewModel::submitReflection,
        )
    }

    if (showAdultGate) {
        AdultGateDialog(
            pinConfigured = viewModel.adultPinConfigured,
            onUnlock = viewModel::unlockAdultSpace,
            onCreatePin = viewModel::createAdultPin,
            onGranted = {
                showAdultGate = false
                showProfileChooser = false
                showGrownUpTools = true
            },
            onDismiss = { showAdultGate = false },
        )
    }

    if (showGrownUpTools && viewModel.adultUnlocked) {
        GrownUpToolsDialog(
            currentPage = state.page,
            studentName = viewModel.studentProfile.name,
            historyCapacityWarning = state.historyCapacityWarning,
            onOpenToday = {
                showGrownUpTools = false
                viewModel.setPage(AppPage.TODAY)
                viewModel.lockAdultSpace()
            },
            onOpenAssignments = {
                showGrownUpTools = false
                viewModel.setPage(AppPage.ASSIGN)
            },
            onOpenSummary = {
                showGrownUpTools = false
                viewModel.setPage(AppPage.SUMMARY)
            },
            onManageProfiles = {
                showGrownUpTools = false
                showFamilyProfiles = true
            },
            onOpenProfile = {
                showGrownUpTools = false
                showProfileDialog = true
            },
            onOpenPrivacy = {
                showGrownUpTools = false
                showPrivacySettings = true
            },
            onDismiss = {
                showGrownUpTools = false
                if (state.page == AppPage.TODAY) viewModel.lockAdultSpace()
            },
        )
    }

    if (showFamilyProfiles && viewModel.adultUnlocked) {
        FamilyProfilesDialog(
            profiles = viewModel.household.childProfiles,
            activeProfileId = viewModel.studentProfile.id,
            onSelectProfile = { profile -> viewModel.selectChildProfile(profile.id) },
            onAddProfile = viewModel::addChildProfile,
            onExportProfile = { profile ->
                profileArchivePendingId = profile.id
                if (viewModel.beginAdultExternalAction()) {
                    createProfileArchive.launch(
                        viewModel.suggestedProfileArchiveFileName(profile.id),
                    )
                } else {
                    profileArchivePendingId = null
                }
            },
            onDeleteProfile = { profile ->
                viewModel.deleteChildProfile(profile.id)
            },
            onDismiss = {
                showFamilyProfiles = false
                showGrownUpTools = true
            },
        )
    }

    if (showProfileDialog && viewModel.adultUnlocked) {
        LocalProfileDialog(
            profile = viewModel.studentProfile,
            fileState = viewModel.localFileState,
            onDismiss = {
                showProfileDialog = false
                showGrownUpTools = true
            },
            onSaveProfile = viewModel::saveProfile,
            onCreateFile = {
                if (viewModel.beginAdultExternalAction()) {
                    createExcelWorkbook.launch(viewModel.suggestedExcelFileName())
                }
            },
            onChooseExistingFile = {
                if (viewModel.beginAdultExternalAction()) {
                    chooseExcelWorkbook.launch(arrayOf(XLSX_MIME_TYPE))
                }
            },
            onSaveFile = viewModel::saveLocalWorkbook,
            onLoadFile = viewModel::loadAssignmentsFromLocalFile,
            onOpenFile = { uriText ->
                openUri(uriText.toUri(), XLSX_MIME_TYPE)
            },
        )
    }

    if (showPrivacySettings && viewModel.adultUnlocked) {
        PrivacySettingsDialog(
            acceptedAgeRange = acceptedAgeRange,
            onDeleteAllData = onDeleteAllData,
            onDismiss = {
                showPrivacySettings = false
                showGrownUpTools = true
            },
        )
    }
}

@Composable
private fun AppHeader(
    state: PracticeUiState,
    onOpenGrownUpTools: () -> Unit,
    onReturnToToday: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.activeTaskId == null && state.page != AppPage.TODAY) {
            IconButton(
                onClick = onReturnToToday,
                modifier = Modifier
                    .size(48.dp)
                    .background(CloudWhite.copy(alpha = 0.9f), CircleShape),
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Back to today’s practice",
                    tint = DeepSky,
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 8.dp),
        ) {
            Text(
                "LK Practice",
                style = MaterialTheme.typography.labelLarge,
                color = DeepSky,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    state.activeTaskId != null -> "Let’s make music!"
                    state.page == AppPage.ASSIGN -> "Plan Practice"
                    state.page == AppPage.SUMMARY -> "Practice Story"
                    else -> "Today’s Practice"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.accessibilityHeading(),
            )
        }
        if (state.activeTaskId != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(SunYellow, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Ink,
                )
            }
        } else {
            IconButton(
                onClick = onSwitchProfile,
                modifier = Modifier
                    .size(48.dp)
                    .background(CloudWhite.copy(alpha = 0.94f), CircleShape),
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Switch practice profile",
                    tint = DeepSky,
                )
            }
            IconButton(
                onClick = onOpenGrownUpTools,
                modifier = Modifier
                    .size(48.dp)
                    .background(CloudWhite.copy(alpha = 0.94f), CircleShape),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Grown-up tools",
                    tint = DeepSky,
                )
            }
        }
    }
}

private const val XLSX_MIME_TYPE =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val ZIP_MIME_TYPE = "application/zip"

private class CreateLocalDocument(
    private val mimeType: String,
) : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_TITLE, input)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}

private class OpenLocalDocument : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (input.size == 1) input.single() else "*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == Activity.RESULT_OK }
}
