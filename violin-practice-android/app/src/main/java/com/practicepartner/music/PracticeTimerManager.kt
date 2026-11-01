package com.practicepartner.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PracticeTimerReading(
    val elapsedSeconds: Long = 0L,
    val sessionElapsedSeconds: Long = 0L,
)

fun interface PracticeTimerTicker {
    fun start(scope: CoroutineScope, onTick: () -> Unit): Job

    companion object {
        val Default = PracticeTimerTicker { tickerScope, tick ->
            tickerScope.launch {
                while (isActive) {
                    tick()
                    delay(1_000)
                }
            }
        }
    }
}

class PracticeTimerManager(
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long,
    private val profileProvider: () -> ChildProfile,
    private val dateProvider: () -> String,
    private val activeTaskProvider: () -> PracticeTask?,
    private val activeSessionIdProvider: () -> String?,
    private val updateSnapshot: ((PracticeSnapshot) -> PracticeSnapshot) -> Unit,
    private val onTick: (PracticeTimerReading) -> Unit,
    private val ticker: PracticeTimerTicker = PracticeTimerTicker.Default,
) {
    private val _elapsed = MutableStateFlow(PracticeTimerReading())
    val elapsed: StateFlow<PracticeTimerReading> = _elapsed.asStateFlow()

    private var timerBaseSeconds = 0L
    private var sessionTimerBaseSeconds = 0L
    private var timerStartedAtMillis: Long? = null
    private var timerJob: Job? = null

    val isRunning: Boolean
        get() = timerStartedAtMillis != null

    fun reset(
        elapsedSeconds: Long,
        sessionElapsedSeconds: Long = 0L,
    ) {
        timerJob?.cancel()
        timerJob = null
        timerStartedAtMillis = null
        timerBaseSeconds = elapsedSeconds.coerceAtLeast(0L)
        sessionTimerBaseSeconds = sessionElapsedSeconds.coerceAtLeast(0L)
        _elapsed.value = PracticeTimerReading(
            elapsedSeconds = timerBaseSeconds,
            sessionElapsedSeconds = sessionTimerBaseSeconds,
        )
    }

    fun start(
        elapsedSeconds: Long,
        sessionElapsedSeconds: Long,
    ): PracticeTimerReading {
        if (isRunning) return currentReading()
        timerBaseSeconds = elapsedSeconds.coerceAtLeast(0L)
        sessionTimerBaseSeconds = sessionElapsedSeconds.coerceAtLeast(0L)
        timerStartedAtMillis = elapsedRealtime()
        val reading = currentReading()
        _elapsed.value = reading
        timerJob = ticker.start(scope, ::publishTick)
        return reading
    }

    fun setSessionElapsedBase(sessionElapsedSeconds: Long) {
        sessionTimerBaseSeconds = sessionElapsedSeconds.coerceAtLeast(0L)
        if (!isRunning) {
            _elapsed.value = _elapsed.value.copy(
                sessionElapsedSeconds = sessionTimerBaseSeconds,
            )
        }
    }

    fun pause(): PracticeTimerReading? {
        if (!isRunning) return null
        val reading = currentReading()
        timerJob?.cancel()
        timerJob = null
        timerStartedAtMillis = null
        timerBaseSeconds = reading.elapsedSeconds
        sessionTimerBaseSeconds = reading.sessionElapsedSeconds
        _elapsed.value = reading
        persistTimerProgress(reading)
        return reading
    }

    fun close() {
        if (isRunning) {
            pause()
        } else {
            timerJob?.cancel()
            timerJob = null
        }
    }

    internal fun publishTick() {
        if (!isRunning) return
        val reading = currentReading()
        _elapsed.value = reading
        onTick(reading)
        if (reading.elapsedSeconds % TIMER_PERSIST_INTERVAL_SECONDS == 0L) {
            persistTimerProgress(reading)
        }
    }

    private fun currentReading(): PracticeTimerReading {
        val startedAt = timerStartedAtMillis
            ?: return PracticeTimerReading(timerBaseSeconds, sessionTimerBaseSeconds)
        val deltaSeconds =
            ((elapsedRealtime() - startedAt).coerceAtLeast(0L) / 1_000L)
        return PracticeTimerReading(
            elapsedSeconds = timerBaseSeconds + deltaSeconds,
            sessionElapsedSeconds = sessionTimerBaseSeconds + deltaSeconds,
        )
    }

    private fun persistTimerProgress(reading: PracticeTimerReading) {
        val date = dateProvider()
        val task = activeTaskProvider()
        val sessionId = activeSessionIdProvider()
        val profileId = profileProvider().id
        updateSnapshot { snapshot ->
            val updatedSessions = if (sessionId == null) {
                snapshot.sessions
            } else {
                snapshot.sessions.map { session ->
                    if (session.id == sessionId) {
                        session.copy(elapsedSeconds = reading.sessionElapsedSeconds)
                    } else {
                        session
                    }
                }
            }
            var progress = (
                snapshot.progressByDate[date] ?: DailyProgress(
                    date = date,
                    childProfileId = profileId,
                )
                ).copy(elapsedSeconds = reading.elapsedSeconds)
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
}

private const val TIMER_PERSIST_INTERVAL_SECONDS = 5L
