package com.practicepartner.music

import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private val Ink = Color(0xFF1C2B32)
private val Muted = Color(0xFF68777A)
private val Cream = Color(0xFFF7F5EF)
private val Paper = Color(0xFFFFFFFF)
private val Forest = Color(0xFF315F50)
private val Sage = Color(0xFFE5EEE8)
private val Amber = Color(0xFFD39A45)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(247, 245, 239)
        window.navigationBarColor = android.graphics.Color.rgb(247, 245, 239)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Forest,
                onPrimary = Color.White,
                secondary = Amber,
                background = Cream,
                surface = Paper,
                onSurface = Ink,
                onBackground = Ink,
            )) { PracticeApp() }
        }
    }
}

@Composable
private fun PracticeApp() {
    val context = LocalContext.current
    val store = remember { PracticeStore(context) }
    var tasks by remember { mutableStateOf(store.tasks()) }
    var events by remember { mutableStateOf(store.events()) }
    var completed by remember { mutableStateOf(store.completedIds()) }
    var page by remember { mutableStateOf("today") }
    var elapsed by remember { mutableLongStateOf(store.elapsedSeconds()) }
    var running by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<PracticeTask?>(null) }
    var activePractice by remember { mutableStateOf<PracticeTask?>(null) }
    var mode by remember { mutableStateOf("Listen to Teacher") }
    var importMessage by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var taskSource by remember { mutableStateOf("") }
    var startText by remember { mutableStateOf("") }
    var endText by remember { mutableStateOf("") }
    var repsText by remember { mutableStateOf("3") }

    fun log(task: PracticeTask?, message: String, uri: String? = null) {
        events = (events + PracticeEvent(task?.title ?: "Practice session", message, System.currentTimeMillis(), uri)).takeLast(500)
        store.saveEvents(events)
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            taskSource = uri.toString()
            importMessage = "Phone video selected. Add a title and repetition count."
        }
    }
    val importCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read this file.")
                val imported = text.lineSequence().drop(1).mapNotNull { line ->
                    val cols = line.split(',').map(String::trim)
                    if (cols.size < 5 || cols[0].isBlank() || cols[1].isBlank()) null
                    else PracticeTask(
                        UUID.randomUUID().toString(), cols[0], cols[1], if (cols[1].startsWith("content://")) "local" else "youtube",
                        cols.getOrNull(2)?.toIntOrNull() ?: 0, cols.getOrNull(3)?.toIntOrNull(), cols.getOrNull(4)?.toIntOrNull() ?: 1,
                    )
                }.toList()
                tasks = tasks + imported
                store.saveTasks(tasks)
                importMessage = "Imported ${imported.size} task(s). Review today’s plan."
            }.onFailure { importMessage = it.message ?: "Could not import the CSV." }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching {
            val csv = buildString {
                appendLine("title,source,start_seconds,end_seconds,repetitions")
                tasks.forEach { task -> appendLine("${csvCell(task.title)},${csvCell(task.source)},${task.startSeconds},${task.endSeconds ?: ""},${task.repetitions}") }
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
            importMessage = "Assignment CSV exported."
        }.onFailure { importMessage = it.message ?: "Could not export the CSV." }
    }
    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            elapsed += 1
            store.saveElapsedSeconds(elapsed)
        }
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = if (activePractice == null) ({
            NavigationBar(containerColor = Paper) {
                NavigationBarItem(selected = page == "today", onClick = { page = "today" }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Today") })
                NavigationBarItem(selected = page == "assign", onClick = { page = "assign" }, icon = { Icon(Icons.Default.Assignment, null) }, label = { Text("Assign") })
                NavigationBarItem(selected = page == "summary", onClick = { page = "summary" }, icon = { Icon(Icons.Default.BarChart, null) }, label = { Text("Summary") })
            }
        }) else ({}),
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("PRACTICE PARTNER", style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
                    Text(if (activePractice != null) "Practice Session" else when (page) { "assign" -> "Parent Assignment"; "summary" -> "Practice Summary"; else -> "Today’s Practice" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                if (activePractice != null) TextButton(onClick = { activePractice = null; running = false }) { Text("Finish") }
                else Box(Modifier.size(42.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Text("♪", color = Forest, fontWeight = FontWeight.Bold) }
            }
            if (activePractice != null) {
                PracticeSessionPage(
                    task = activePractice!!,
                    mode = mode,
                    onSaved = { uri -> log(activePractice, "Recording saved to phone", uri.toString()); importMessage = "Take saved in Movies / Practice Partner." },
                    onShare = { uri ->
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(share, "Share practice take")) }
                    },
                    onOpenSaved = { uri ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    },
                    message = importMessage,
                    onFinish = { activePractice = null; running = false },
                )
            } else when (page) {
                "assign" -> AssignmentPage(
                    tasks, taskTitle, { taskTitle = it }, taskSource, { taskSource = it }, startText, { startText = it },
                    endText, { endText = it }, repsText, { repsText = it }, importMessage,
                    onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                    onImport = { importCsv.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                    onExport = { exportCsv.launch("practice-assignment.csv") },
                    onAdd = {
                        val title = taskTitle.trim()
                        val source = taskSource.trim()
                        val start = startText.toIntOrNull() ?: 0
                        val end = endText.toIntOrNull()
                        val repetitions = repsText.toIntOrNull() ?: 0
                        if (title.isBlank() || source.isBlank() || start < 0 || (end != null && end <= start) || repetitions !in 1..20) {
                            importMessage = "Add a title and video, valid times, and 1–20 repetitions. Blank times mean the whole video."
                        } else {
                            val type = if (source.startsWith("content://")) "local" else "youtube"
                            val item = PracticeTask(UUID.randomUUID().toString(), title, source, type, start, end, repetitions)
                            tasks = tasks + item
                            store.saveTasks(tasks)
                            taskTitle = ""; taskSource = ""; startText = ""; endText = ""; repsText = "3"
                            importMessage = "Task added to today’s plan."
                        }
                    },
                    onRemove = { task -> tasks = tasks.filterNot { it.id == task.id }; store.saveTasks(tasks) },
                )
                "summary" -> SummaryPage(tasks, completed, events, elapsed, onOpenRecording = { uri ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                })
                else -> TodayPage(
                    tasks = tasks, completed = completed, elapsed = elapsed, running = running, selected = selected, mode = mode,
                    onStart = { running = true; log(selected, "Practice timer started") },
                    onPause = { running = false; log(selected, "Practice timer paused") },
                    onSelectMode = { mode = it },
                    onPractice = { task -> selected = task; activePractice = task; running = true; log(task, "$mode started") },
                    onDone = { task ->
                        completed = if (task.id in completed) completed - task.id else completed + task.id
                        store.saveCompletedIds(completed)
                        log(task, if (task.id in completed) "Task completed" else "Task reopened")
                    },
                )
            }
        }
    }
}

@Composable
private fun TodayPage(
    tasks: List<PracticeTask>, completed: Set<String>, elapsed: Long, running: Boolean, selected: PracticeTask?, mode: String,
    onStart: () -> Unit, onPause: () -> Unit, onSelectMode: (String) -> Unit, onPractice: (PracticeTask) -> Unit,
    onDone: (PracticeTask) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date()), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("A little practice,\na little progress.", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(formatDuration(elapsed), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(if (running) "Practice timer running" else "Total practice time", color = Color.White.copy(alpha = .8f))
                        }
                        FilledTonalButton(onClick = if (running) onPause else onStart, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White, contentColor = Forest)) {
                            Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp)); Text(if (running) "Pause" else "Start")
                        }
                    }
                }
            }
        }
        item {
            Column {
                Text("Choose a practice mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Listen to Teacher", "Listen & Play", "Play Yourself").forEach { option ->
                        FilterChip(selected = mode == option, onClick = { onSelectMode(option) }, label = { Text(option, maxLines = 1) })
                    }
                }
            }
        }
        if (tasks.isEmpty()) item { EmptyCard("No practice tasks yet", "A parent can add a YouTube link or choose a video in Parent Assignment.") }
        items(tasks, key = { it.id }) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${timeLabel(task.startSeconds)}–${task.endSeconds?.let(::timeLabel) ?: "end of video"} · ${task.repetitions} repetitions", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (task.id in completed) Icon(Icons.Default.CheckCircle, "Completed", tint = Forest)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPractice(task) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text(if (selected?.id == task.id) "Continue practice" else "Start practice")
                        }
                        TextButton(onClick = { onDone(task) }) { Text(if (task.id in completed) "Undo done" else "Mark done") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AssignmentPage(
    tasks: List<PracticeTask>, title: String, onTitle: (String) -> Unit, source: String, onSource: (String) -> Unit,
    start: String, onStart: (String) -> Unit, end: String, onEnd: (String) -> Unit, reps: String, onReps: (String) -> Unit,
    message: String, onPickVideo: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, onAdd: () -> Unit, onRemove: (PracticeTask) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Build today’s plan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Add a YouTube reference or choose a video already on this phone.", color = Muted)
                    OutlinedTextField(title, onTitle, label = { Text("Topic or task name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(source, onSource, label = { Text("YouTube link or selected phone video") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.VideoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Choose video from phone") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(start, onStart, label = { Text("Start (sec)") }, placeholder = { Text("0") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(end, onEnd, label = { Text("End (sec)") }, placeholder = { Text("Whole video") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(reps, onReps, label = { Text("Repeats") }, singleLine = true, modifier = Modifier.weight(.8f))
                    }
                    Text("Blank start begins at 0:00; blank end plays to the end.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("Add assignment") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import CSV") }
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export CSV") }
                    }
                    if (message.isNotBlank()) Text(message, color = Forest, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("Today’s tasks · ${tasks.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(tasks, key = { "assignment-${it.id}" }) { task ->
            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold)
                        Text("${if (task.sourceType == "local") "Phone video" else "YouTube"} · ${task.repetitions} reps", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onRemove(task) }) { Text("Remove") }
                }
            }
        }
        item { Text("Spreadsheet support in this build uses CSV, which opens in Excel and Google Sheets.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun PracticeSessionPage(
    task: PracticeTask,
    mode: String,
    onSaved: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onOpenSaved: (Uri) -> Unit,
    message: String,
    onFinish: () -> Unit,
) {
    var savedTake by remember(task.id) { mutableStateOf<Uri?>(null) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("$mode · ${timeLabel(task.startSeconds)}–${task.endSeconds?.let(::timeLabel) ?: "end of video"} · ${task.repetitions} repetitions", color = Color.White.copy(alpha = .82f))
                }
            }
        }
        if (mode != "Play Yourself") {
            item {
                Text("TEACHER REFERENCE", color = Forest, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                ReferenceVideoPanel(task)
            }
        }
        if (mode != "Listen to Teacher") {
            item {
                Text("LINCOLN · CAMERA", color = Forest, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                CameraRecorderPanel(
                    autoStart = savedTake == null,
                    onSaved = { uri -> savedTake = uri; onSaved(uri) },
                )
            }
        }
        if (savedTake != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Take saved to your phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("The recording is in Movies / Practice Partner. It contains Lincoln’s camera and microphone recording; the YouTube reference stays in its player.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { savedTake?.let(onOpenSaved) }, modifier = Modifier.weight(1f)) { Text("Watch take") }
                            Button(onClick = { savedTake?.let(onShare) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Forest)) { Text("Share / upload…") }
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) item { Text(message, color = Forest, style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Finish practice") } }
    }
}

@Composable
private fun ReferenceVideoPanel(task: PracticeTask) {
    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp).background(Color.Black, RoundedCornerShape(12.dp))) {
                if (task.sourceType == "local") LocalVideoPlayer(task)
                else YouTubePlayer(task)
            }
            Text("Tap Play in the teacher player. The camera recording starts separately so you control when each take begins.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun YouTubePlayer(task: PracticeTask) {
    val videoId = remember(task.source) { youtubeVideoId(task.source) }
    if (videoId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This YouTube link could not be opened.", color = Color.White) }
        return
    }
    val endParam = task.endSeconds?.let { "&end=$it" } ?: ""
    val html = remember(videoId, task.startSeconds, endParam) {
        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"></head><body style="margin:0;background:#000"><iframe width="100%" height="100%" src="https://www.youtube.com/embed/$videoId?start=${task.startSeconds}$endParam&playsinline=1&controls=1&rel=0" title="Teacher reference" frameborder="0" allow="encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>"""
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> WebView(ctx).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            tag = html
            loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
        } },
        update = { if (it.tag != html) { it.tag = html; it.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null) } },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}

@Composable
private fun LocalVideoPlayer(task: PracticeTask) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> VideoView(ctx).apply {
            tag = task.source
            setMediaController(MediaController(ctx))
            setVideoURI(Uri.parse(task.source))
            setOnPreparedListener { player -> player.seekTo(task.startSeconds * 1000) }
        } },
        update = { if (it.tag != task.source) { it.tag = task.source; it.setVideoURI(Uri.parse(task.source)) } },
    )
}

@Composable
private fun CameraRecorderPanel(autoStart: Boolean, onSaved: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    var cameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var audioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var permissionAttempted by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingError by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf<Recording?>(null) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        cameraPermission = grants[android.Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        audioPermission = grants[android.Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        permissionAttempted = true
    }
    LaunchedEffect(Unit) {
        if (!cameraPermission && !permissionAttempted) permissionRequest.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO))
    }
    val controller = remember(cameraPermission) {
        if (!cameraPermission) null else LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            bindToLifecycle(lifecycleOwner)
        }
    }
    DisposableEffect(controller) {
        onDispose { recording?.stop(); controller?.unbind() }
    }
    fun startRecording() {
        val camera = controller ?: return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "PracticePartner-${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Practice Partner")
            }
            val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(values).build()
            val audioConfig = if (audioPermission) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
            recording = camera.startRecording(options, audioConfig, ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> { isRecording = true; recordingError = "Recording Lincoln. Tap Stop take when finished." }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        recording = null
                        if (!event.hasError()) onSaved(event.outputResults.outputUri)
                        else recordingError = "The recording could not be saved (error ${event.error}). Try again."
                    }
                }
            }
        } catch (error: Exception) {
            recordingError = error.message ?: "Could not start camera recording."
            isRecording = false
            recording = null
        }
    }
    LaunchedEffect(controller, autoStart) {
        if (autoStart && controller != null && !isRecording && recording == null) startRecording()
    }
    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF172227), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (controller != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx -> PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            this.controller = controller
                        } },
                        update = { it.controller = controller },
                    )
                } else Text("Camera permission is needed to record a take.", color = Color.White)
            }
            if (controller == null) {
                Button(onClick = { permissionRequest.launch(arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)) }, modifier = Modifier.fillMaxWidth()) { Text("Allow camera and microphone") }
            } else if (isRecording) {
                Button(onClick = { recording?.stop() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9A3D32))) { Text("Stop take") }
            } else if (recordingError.startsWith("The recording could not") || recordingError.startsWith("Could not")) {
                Button(onClick = { startRecording() }, modifier = Modifier.fillMaxWidth()) { Text("Record take") }
            } else {
                Button(onClick = { startRecording() }, modifier = Modifier.fillMaxWidth()) { Text("Record another take") }
            }
            if (recordingError.isNotBlank()) Text(recordingError, color = if (isRecording) Forest else Muted, style = MaterialTheme.typography.bodySmall)
            if (!audioPermission && cameraPermission) Text("Microphone access is off; this take will be silent.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun youtubeVideoId(value: String): String? {
    val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val id = when {
        host == "youtu.be" || host == "www.youtu.be" -> uri.lastPathSegment.orEmpty()
        uri.path == "/watch" -> uri.getQueryParameter("v").orEmpty()
        host.endsWith("youtube.com") -> uri.lastPathSegment.orEmpty()
        else -> ""
    }
    return id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}

@Composable
private fun SummaryPage(tasks: List<PracticeTask>, completed: Set<String>, events: List<PracticeEvent>, elapsed: Long, onOpenRecording: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Forest), shape = RoundedCornerShape(22.dp)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("ACTIVE PRACTICE", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium); Text(formatDuration(elapsed), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                    Column(horizontalAlignment = Alignment.End) { Text("${completed.size} / ${tasks.size}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("tasks complete", color = Color.White.copy(alpha = .8f)) }
                }
            }
        }
        item { Text("Practice log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (events.isEmpty()) item { EmptyCard("No practice logged yet", "Starting a task, pausing, completing it, and saving a take will show here.") }
        items(events.reversed(), key = { "${it.atMillis}-${it.taskTitle}-${it.event}" }) { event ->
            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(event.taskTitle, fontWeight = FontWeight.SemiBold)
                        Text("${event.event} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.atMillis))}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (event.videoUri != null) TextButton(onClick = { onOpenRecording(event.videoUri) }) { Text("Play take") }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(title: String, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(detail, color = Muted) }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}

private fun timeLabel(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
private fun csvCell(text: String): String = "\"${text.replace("\"", "\"\"")}\""
