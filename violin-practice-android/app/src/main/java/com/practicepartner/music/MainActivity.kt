package com.practicepartner.music

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    var form by remember(state.currentDate) { mutableStateOf(AssignmentForm()) }

    LaunchedEffect(state.message) {
        if (state.message.isNotBlank()) {
            snackbarHostState.showSnackbar(state.message)
            viewModel.clearMessage()
        }
    }

    fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun openUri(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(intent) }
            .onFailure { viewModel.setMessage("No app is available to open this file.") }
    }

    fun shareVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
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
            persistReadPermission(uri)
            form = form.copy(source = uri.toString())
            viewModel.setMessage("Reference video selected.")
        }
    }
    val pickSheetMusic = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            form = form.copy(sheetMusicUri = uri.toString())
            viewModel.setMessage("Sheet music attached.")
        }
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read this CSV file.")
            }.onSuccess(viewModel::importCsv)
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
                output.bufferedWriter().use { it.write(viewModel.exportTodayCsv()) }
            }.onSuccess {
                viewModel.setMessage("Today’s assignments were exported.")
            }.onFailure {
                viewModel.setMessage(it.message ?: "Could not export today’s assignments.")
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
            AppHeader(state)
            val activeTask = state.activeTask
            if (activeTask != null) {
                PracticeSessionPage(
                    state = state,
                    task = activeTask,
                    onRecordingSaved = { viewModel.recordingSaved(activeTask, it) },
                    onRecordingChanged = viewModel::setRecordingActive,
                    onCompleteRepetition = { viewModel.recordRepetition(activeTask) },
                    onOpenUri = ::openUri,
                    onShare = ::shareVideo,
                    onDeleteRecording = { recording -> viewModel.deleteRecording(recording.path) },
                    onFinish = { viewModel.finishPractice() },
                )
            } else {
                when (state.page) {
                    AppPage.TODAY -> TodayPage(
                        state = state,
                        onStartTimer = viewModel::startTimer,
                        onPauseTimer = { viewModel.pauseTimer() },
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
                            importCsv.launch(
                                arrayOf("text/csv", "text/*", "application/vnd.ms-excel"),
                            )
                        },
                        onExport = { exportCsv.launch("practice-assignment-${state.currentDate}.csv") },
                        onAdd = {
                            val added = viewModel.addAssignment(
                                titleText = form.title,
                                sourceText = form.source,
                                startText = form.start,
                                endText = form.end,
                                repetitionsText = form.repetitions,
                                coachNote = form.coachNote,
                                whyImportant = form.whyImportant,
                                sheetMusicUri = form.sheetMusicUri,
                            )
                            if (added) form = AssignmentForm()
                        },
                        onRemove = viewModel::removeTask,
                    )
                    AppPage.SUMMARY -> SummaryPage(
                        state = state,
                        onOpenUri = ::openUri,
                        onShare = ::shareVideo,
                        onDeleteRecording = { event ->
                            event.videoPath?.let(viewModel::deleteRecording)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(state: PracticeUiState) {
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
        Box(
            Modifier.size(42.dp).background(Sage, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", color = Forest, fontWeight = FontWeight.Bold)
        }
    }
}
