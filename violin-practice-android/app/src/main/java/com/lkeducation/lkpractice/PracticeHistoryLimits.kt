package com.lkeducation.lkpractice

internal const val MAX_PRACTICE_EVENTS = 2_000
internal const val MAX_PRACTICE_SESSIONS = 2_000
internal const val PRACTICE_EVENT_WARNING_COUNT = MAX_PRACTICE_EVENTS * 80 / 100
internal const val PRACTICE_SESSION_WARNING_COUNT = MAX_PRACTICE_SESSIONS * 80 / 100

internal fun practiceHistoryCapacityWarning(
    eventCount: Int,
    sessionCount: Int,
): String? {
    val eventsNearLimit = eventCount >= PRACTICE_EVENT_WARNING_COUNT
    val sessionsNearLimit = sessionCount >= PRACTICE_SESSION_WARNING_COUNT
    if (!eventsNearLimit && !sessionsNearLimit) return null

    val atLimit =
        eventCount >= MAX_PRACTICE_EVENTS ||
            sessionCount >= MAX_PRACTICE_SESSIONS
    val status = if (atLimit) {
        "Practice history storage is at its current limit."
    } else {
        "Practice history storage is nearing its current limit."
    }
    return "$status Events: ${eventCount.coerceAtMost(MAX_PRACTICE_EVENTS)} of " +
        "$MAX_PRACTICE_EVENTS. Sessions: " +
        "${sessionCount.coerceAtMost(MAX_PRACTICE_SESSIONS)} of $MAX_PRACTICE_SESSIONS. " +
        "Export a profile archive soon. If a limit is reached, LK Practice keeps the newest records."
}
