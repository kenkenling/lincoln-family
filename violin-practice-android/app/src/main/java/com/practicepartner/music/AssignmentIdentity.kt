package com.practicepartner.music

internal fun friendlyDate(date: DateKey): String =
    date.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))

internal fun assignmentKey(task: PracticeTask): AssignmentKey =
    AssignmentKey(
        date = task.assignedDate,
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
    val date: DateKey,
    val title: String,
    val source: String,
    val startSeconds: Int,
    val endSeconds: Int?,
)
