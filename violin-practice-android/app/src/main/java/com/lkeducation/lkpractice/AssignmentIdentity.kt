package com.lkeducation.lkpractice

internal fun friendlyDate(date: DateKey): String =
    date.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))

internal fun assignmentKey(task: PracticeTask): AssignmentKey =
    AssignmentKey(
        startDate = task.startDate,
        endDate = task.endDate,
        title = task.title.trim().lowercase(),
        source = when (task.sourceType) {
            SourceType.NONE -> ""
            SourceType.YOUTUBE -> youtubeVideoId(task.source).orEmpty()
            SourceType.LOCAL -> task.source.trim()
        },
        startSeconds = task.startSeconds,
        endSeconds = task.endSeconds,
    )

internal data class AssignmentKey(
    val startDate: DateKey,
    val endDate: DateKey,
    val title: String,
    val source: String,
    val startSeconds: Int,
    val endSeconds: Int?,
)
