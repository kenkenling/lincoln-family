package com.practicepartner.music

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private val practiceViewModel by viewModels<PracticeViewModel> {
        PracticeViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.lightColorScheme(
                    primary = Forest,
                    onPrimary = Paper,
                    secondary = Amber,
                    background = Cream,
                    surface = Paper,
                    onSurface = Ink,
                    onBackground = Ink,
                    error = Danger,
                ),
            ) {
                PracticeApp(practiceViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        practiceViewModel.refreshDate()
        practiceViewModel.resumeActivePractice()
    }

    override fun onStop() {
        practiceViewModel.pauseForBackground()
        super.onStop()
    }
}

@Composable
private fun PracticeApp(viewModel: PracticeViewModel) {
    val context = LocalContext.current
    val state = viewModel.uiState
    val snackbarHostState = remember { SnackbarHostState() }
    var form by remember(state.currentDate) {
        mutableStateOf(AssignmentForm(assignedDate = state.currentDate))
    }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var csvDatePending by rememberSaveable { mutableStateOf(state.currentDate) }

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

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (persistReadPermission(uri)) {
                form = form.copy(source = uri.toString())
                viewModel.setMessage("Reference video selected.")
            } else {
                viewModel.setMessage(
                    "Practice Partner could not keep access to that video. Choose it again from Files.",
                )
            }
        }
    }
    val pickSheetMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (persistReadPermission(uri)) {
                form = form.copy(sheetMusicUri = uri.toString())
                viewModel.setMessage("Sheet music attached.")
            } else {
                viewModel.setMessage(
                    "Practice Partner could not keep access to that sheet music. Choose it again from Files.",
                )
            }
        }
    }
    val createExcelWorkbook = rememberLauncherForActivityResult(
        CreateLocalDocument(XLSX_MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            if (persistReadWritePermission(uri)) {
                viewModel.createLocalWorkbook(uri.toString())
            } else {
                viewModel.setMessage(
                    "Practice Partner could not keep permission to update this local file. Choose it again.",
                )
            }
        }
    }
    val chooseExcelWorkbook = rememberLauncherForActivityResult(
        OpenLocalDocument(),
    ) { uri ->
        if (uri != null) {
            if (persistReadWritePermission(uri)) {
                viewModel.connectExistingLocalWorkbook(uri.toString())
            } else {
                viewModel.setMessage(
                    "Practice Partner could not keep permission to update this local file. Choose it again.",
                )
            }
        }
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read this CSV file.")
            }.onSuccess { viewModel.importCsv(it, csvDatePending) }
                .onFailure { viewModel.setMessage(it.message ?: "Could not import the CSV.") }
        }
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
    }
    BackHandler(enabled = state.activeTaskId != null) {
        viewModel.finishPractice()
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.activeTaskId == null) {
                NavigationBar(containerColor = Paper) {
                    NavigationBarItem(
                        selected = state.page == AppPage.TODAY,
                        onClick = { viewModel.setPage(AppPage.TODAY) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Today") },
                        label = { Text("Today") },
                    )
                    NavigationBarItem(
                        selected = state.page == AppPage.ASSIGN,
                        onClick = { viewModel.setPage(AppPage.ASSIGN) },
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Assignment,
                                contentDescription = "Assign",
                            )
                        },
                        label = { Text("Assign") },
                    )
                    NavigationBarItem(
                        selected = state.page == AppPage.SUMMARY,
                        onClick = { viewModel.setPage(AppPage.SUMMARY) },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "Summary") },
                        label = { Text("Summary") },
                    )
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            AppHeader(
                state = state,
                fileState = viewModel.localFileState,
                onOpenProfile = { showProfileDialog = true },
            )
            val activeTask = state.activeTask
            if (activeTask != null) {
                PracticeSessionPage(
                    state = state,
                    task = activeTask,
                    studentName = viewModel.studentProfile.name,
                    onRecordingSaved = { viewModel.recordingSaved(activeTask, it) },
                    onRecordingChanged = viewModel::setRecordingActive,
                    onCompleteRepetition = { viewModel.recordRepetition(activeTask) },
                    onResumeTimer = viewModel::resumeActivePractice,
                    onOpenUri = ::openUri,
                    onShare = ::shareVideo,
                    onDeleteRecording = { recording -> viewModel.deleteRecording(recording.path) },
                    onFinish = { viewModel.finishPractice() },
                )
            } else {
                when (state.page) {
                    AppPage.TODAY -> TodayPage(
                        state = state,
                        onSelectMode = viewModel::setMode,
                        onPractice = viewModel::startPractice,
                        onToggleCompleted = viewModel::toggleCompleted,
                    )
                    AppPage.ASSIGN -> AssignmentPage(
                        state = state,
                        form = form,
                        onFormChanged = { form = it },
                        onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        onPickSheetMusic = {
                            pickSheetMusic.launch(
                                arrayOf("application/pdf", "image/*"),
                            )
                        },
                        onImport = {
                            csvDatePending = form.assignedDate
                            importCsv.launch(
                                arrayOf("text/csv", "text/*", "application/vnd.ms-excel"),
                            )
                        },
                        onExport = {
                            csvDatePending = form.assignedDate
                            exportCsv.launch("practice-assignment-${form.assignedDate}.csv")
                        },
                        onAdd = {
                            val added = viewModel.saveAssignment(
                                taskId = form.editingTaskId,
                                assignedDateText = form.assignedDate,
                                titleText = form.title,
                                sourceText = form.source,
                                startText = form.start,
                                endText = form.end,
                                repetitionsText = form.repetitions,
                                coachNote = form.coachNote,
                                whyImportant = form.whyImportant,
                                sheetMusicUri = form.sheetMusicUri,
                            )
                            if (added) {
                                form = AssignmentForm(assignedDate = form.assignedDate)
                            }
                        },
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

    if (showProfileDialog) {
        LocalProfileDialog(
            profile = viewModel.studentProfile,
            fileState = viewModel.localFileState,
            onDismiss = { showProfileDialog = false },
            onSaveProfile = viewModel::saveProfile,
            onCreateFile = {
                createExcelWorkbook.launch(viewModel.suggestedExcelFileName())
            },
            onChooseExistingFile = {
                chooseExcelWorkbook.launch(arrayOf(XLSX_MIME_TYPE))
            },
            onSaveFile = viewModel::saveLocalWorkbook,
            onLoadFile = viewModel::loadAssignmentsFromLocalFile,
            onOpenFile = { uriText ->
                openUri(uriText.toUri(), XLSX_MIME_TYPE)
            },
        )
    }
}

@Composable
private fun AppHeader(
    state: PracticeUiState,
    fileState: LocalFileUiState,
    onOpenProfile: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                "PRACTICE PARTNER",
                style = MaterialTheme.typography.labelSmall,
                color = Forest,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (state.activeTaskId != null) {
                    "Practice Session"
                } else {
                    when (state.page) {
                        AppPage.ASSIGN -> "Parent Assignment"
                        AppPage.SUMMARY -> "Practice Summary"
                        AppPage.TODAY -> "Today’s Practice"
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Box {
            IconButton(
                onClick = onOpenProfile,
                modifier = Modifier.size(44.dp).background(Sage, CircleShape),
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile and local file",
                    tint = Forest,
                )
            }
            if (!fileState.fileUri.isNullOrBlank()) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(11.dp)
                        .background(
                            when {
                                fileState.status == LocalFileStatus.ERROR -> Danger
                                fileState.status == LocalFileStatus.READY -> Forest
                                else -> Amber
                            },
                            CircleShape,
                        ),
                )
            }
        }
    }
}

private const val XLSX_MIME_TYPE =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

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
