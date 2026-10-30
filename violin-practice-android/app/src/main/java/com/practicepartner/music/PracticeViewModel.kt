package com.practicepartner.music

import android.content.Context
import android.os.SystemClock
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
    private val store: PracticeStore,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val initialDate = currentDateKey()
    private val initialSnapshot = store.load(initialDate)

    var uiState by mutableStateOf(
        PracticeUiState(
            snapshot = initialSnapshot,
            currentDate = initialDate,
            elapsedSeconds = initialSnapshot.progressByDate[initialDate]?.elapsedSeconds ?: 0L,
        ),
    )
        private set

    private var timerBaseSeconds = uiState.elapsedSeconds
    private var timerStartedAtMillis: Long? = null
    private var timerJob: Job? = null

    fun setPage(page: AppPage) {
        if (uiState.activeTaskId == null) uiState = uiState.copy(page = page)
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

    fun refreshDate() {
        val today = currentDateKey()
        if (today == uiState.currentDate) return
        pauseTimer(logEvent = false)
        val elapsed = uiState.snapshot.progressByDate[today]?.elapsedSeconds ?: 0L
        timerBaseSeconds = elapsed
        uiState = uiState.copy(
            currentDate = today,
            activeTaskId = null,
            selectedTaskId = null,
            elapsedSeconds = elapsed,
            running = false,
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
    ): Boolean {
        val title = titleText.trim()
        val source = sourceText.trim()
        val sourceType = sourceTypeFor(source)
        val start = startText.trim().ifBlank { "0" }.toIntOrNull()
        val end = endText.trim().takeIf(String::isNotBlank)?.toIntOrNull()
        val repetitions = repetitionsText.trim().toIntOrNull()
        val error = when {
            title.isBlank() -> "Add a task title."
            sourceType == null -> "Use a valid YouTube link or choose a video from this device."
            start == null || start < 0 -> "Start time must be zero or greater."
            endText.isNotBlank() && end == null -> "End time must be a whole number of seconds."
            end != null && end <= start -> "End time must be after start time."
            repetitions == null || repetitions !in 1..20 -> "Repetitions must be between 1 and 20."
            else -> null
        }
        if (error != null) {
            setMessage(error)
            return false
        }

        val task = PracticeTask(
            id = UUID.randomUUID().toString(),
            assignedDate = uiState.currentDate,
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
        updateSnapshot { it.copy(tasks = it.tasks + task) }
        setMessage("Added “${task.title}” to ${friendlyDate(uiState.currentDate)}.")
        return true
    }

    fun importCsv(text: String) {
        val result = CsvCodec.decode(text, uiState.currentDate)
        if (result.tasks.isNotEmpty()) {
            updateSnapshot { snapshot -> snapshot.copy(tasks = snapshot.tasks + result.tasks) }
        }
        uiState = uiState.copy(
            message = buildString {
                append("Imported ${result.tasks.size} assignment")
                if (result.tasks.size != 1) append('s')
                append('.')
                if (result.errors.isNotEmpty()) {
                    append(" Skipped ${result.errors.size}: ")
                    append(result.errors.take(2).joinToString(" "))
                }
            },
        )
    }

    fun exportTodayCsv(): String = CsvCodec.encode(uiState.todayTasks)

    fun removeTask(task: PracticeTask) {
        if (uiState.recordingActive && task.id == uiState.activeTaskId) {
            setMessage("Stop the recording before removing this assignment.")
            return
        }
        if (task.id == uiState.activeTaskId) finishPractice()
        updateSnapshot { snapshot ->
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

    fun startTimer() {
        refreshDate()
        if (uiState.running) return
        timerBaseSeconds = uiState.todayProgress.elapsedSeconds
        timerStartedAtMillis = elapsedRealtime()
        uiState = uiState.copy(running = true, elapsedSeconds = timerBaseSeconds)
        logEvent(taskFor(uiState.selectedTaskId), "Practice timer started")
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
        uiState = uiState.copy(
            selectedTaskId = task.id,
            activeTaskId = task.id,
        )
        if (!uiState.running) startTimer()
        logEvent(task, "${uiState.mode.label} started")
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

    fun deleteRecording(videoPath: String) {
        val recordingFile = File(videoPath)
        val existed = recordingFile.exists()
        val deleted = runCatching {
            !existed || recordingFile.delete() || !recordingFile.exists()
        }.getOrDefault(false)
        if (!deleted) {
            setMessage("The recording could not be deleted. Its log entry was kept so you can try again.")
            return
        }
        updateSnapshot { snapshot ->
            snapshot.copy(events = snapshot.events.filterNot { it.videoPath == videoPath })
        }
        setMessage(if (existed) "Recording deleted." else "Recording entry removed; the file was already unavailable.")
    }

    fun pauseForBackground() {
        pauseTimer(logEvent = false)
    }

    override fun onCleared() {
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

    private fun updateSnapshot(transform: (PracticeSnapshot) -> PracticeSnapshot) {
        val next = transform(uiState.snapshot)
        store.save(next)
        uiState = uiState.copy(snapshot = next)
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
        updateSnapshot { snapshot -> snapshot.copy(events = (snapshot.events + event).takeLast(500)) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PracticeViewModel(PracticeStore(context.applicationContext)) as T
        }
    }
}

private fun friendlyDate(date: String): String = runCatching {
    java.time.LocalDate.parse(date).format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
}.getOrDefault(date)
