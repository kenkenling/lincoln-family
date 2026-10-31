package com.practicepartner.music

import java.time.Instant

data class WorkbookSheet(
    val name: String,
    val rows: List<List<Any>>,
)

object PracticeWorkbookTemplate {
    const val TEMPLATE_VERSION = "2"

    val profileHeaders = listOf(
        "Key",
        "Value",
    )

    val assignmentHeaders = listOf(
        "Assignment ID",
        "Assigned Date",
        "Title",
        "Reference Type",
        "Reference Link",
        "Clip Start Seconds",
        "Clip End Seconds",
        "Target Repetitions",
        "Teacher Note",
        "Why Important",
        "Sheet Music Link",
        "Has Local Sheet Music",
    )

    val dailyProgressHeaders = listOf(
        "Date",
        "Assignment ID",
        "Assignment Title",
        "Repetitions Completed",
        "Target Repetitions",
        "Completed",
    )

    val dailySummaryHeaders = listOf(
        "Date",
        "Total Practice Seconds",
        "Assignments Completed",
        "Assignments Planned",
        "Repetitions Completed",
    )

    val practiceLogHeaders = listOf(
        "Event ID",
        "Date",
        "Assignment ID",
        "Assignment Title",
        "Event",
        "Timestamp UTC",
        "Recording Saved",
    )

    val requiredSheetNames = listOf(
        "Profile",
        "Assignments",
        "Daily Progress",
        "Daily Summary",
        "Practice Log",
    )

    val legacyRequiredSheetNames = listOf(
        "Profile",
        "Assignments",
        "Daily Progress",
        "Practice Log",
    )

    val headersBySheet = linkedMapOf(
        "Profile" to profileHeaders,
        "Assignments" to assignmentHeaders,
        "Daily Progress" to dailyProgressHeaders,
        "Daily Summary" to dailySummaryHeaders,
        "Practice Log" to practiceLogHeaders,
    )

    fun title(profile: StudentProfile): String =
        "Practice Partner - ${profile.name.trim().ifBlank { "Student" }}"

    fun fileName(profile: StudentProfile): String = "${title(profile)}.xlsx"

    fun sheets(
        profile: StudentProfile,
        snapshot: PracticeSnapshot,
        updatedAtMillis: Long,
    ): List<WorkbookSheet> {
        val updatedAt = Instant.ofEpochMilli(updatedAtMillis).toString()
        val taskById = snapshot.tasks.associateBy { it.id }

        val profileRows = listOf(
            profileHeaders,
            listOf("Template Version", TEMPLATE_VERSION),
            listOf("Student ID", profile.id),
            listOf("Student Name", profile.name),
            listOf("Instrument", profile.instrument),
            listOf("YouTube Channel Name", profile.youtubeChannelTitle.orEmpty()),
            listOf("YouTube Channel URL", profile.youtubeChannelUrl),
            listOf(
                "Assignment Sync",
                "Two-way through the Assignments sheet. Load Excel edits before making competing app edits.",
            ),
            listOf(
                "Practice History",
                "Local-first. Workbook progress and log rows are snapshots and are not loaded into the app yet.",
            ),
            listOf("Last Synced UTC", updatedAt),
        )

        val assignmentRows = buildList {
            add(assignmentHeaders)
            snapshot.tasks.sortedWith(compareBy(PracticeTask::assignedDate, PracticeTask::title))
                .forEach { task ->
                    add(
                        listOf(
                            task.id,
                            task.assignedDate,
                            task.title,
                            task.sourceType.name,
                            task.source.takeIf {
                                task.sourceType == SourceType.YOUTUBE &&
                                    it.startsWith("https://")
                            }.orEmpty(),
                            task.startSeconds,
                            task.endSeconds ?: "",
                            task.repetitions,
                            task.coachNote,
                            task.whyImportant,
                            task.sheetMusicUri?.takeIf { it.startsWith("https://") }.orEmpty(),
                            if (task.sheetMusicUri?.startsWith("content://") == true) "Yes" else "No",
                        ),
                    )
                }
        }

        val progressRows = buildList {
            add(dailyProgressHeaders)
            snapshot.progressByDate.values.sortedBy { it.date }.forEach { progress ->
                val taskIds = (
                    progress.repetitionsByTask.keys +
                        progress.completedTaskIds +
                        snapshot.tasks.filter { it.assignedDate == progress.date }.map { it.id }
                    ).toSortedSet()
                if (taskIds.isEmpty()) {
                    add(
                        listOf(
                            progress.date,
                            "",
                            "",
                            0,
                            0,
                            "No",
                        ),
                    )
                } else {
                    taskIds.forEach { taskId ->
                        val task = taskById[taskId]
                        add(
                            listOf(
                                progress.date,
                                taskId,
                                task?.title.orEmpty(),
                                progress.repetitionsFor(taskId),
                                task?.repetitions ?: 0,
                                if (taskId in progress.completedTaskIds) "Yes" else "No",
                            ),
                        )
                    }
                }
            }
        }

        val dailySummaryRows = buildList {
            add(dailySummaryHeaders)
            val dates = (
                snapshot.tasks.map(PracticeTask::assignedDate) +
                    snapshot.progressByDate.keys
                ).distinct().sorted()
            dates.forEach { date ->
                val progress = snapshot.progressByDate[date] ?: DailyProgress(date)
                val tasks = snapshot.tasks.filter { it.assignedDate == date }
                val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
                add(
                    listOf(
                        date,
                        progress.elapsedSeconds,
                        progress.completedTaskIds.count { it in taskIds },
                        tasks.size,
                        tasks.sumOf { progress.repetitionsFor(it.id) },
                    ),
                )
            }
        }

        val eventRows = buildList {
            add(practiceLogHeaders)
            snapshot.events.takeLast(500).forEach { event ->
                add(
                    listOf(
                        event.id,
                        event.date,
                        event.taskId.orEmpty(),
                        event.taskTitle,
                        event.event,
                        Instant.ofEpochMilli(event.atMillis).toString(),
                        if (event.videoUri != null || event.videoPath != null) "Yes" else "No",
                    ),
                )
            }
        }

        return listOf(
            WorkbookSheet("Profile", profileRows),
            WorkbookSheet("Assignments", assignmentRows),
            WorkbookSheet("Daily Progress", progressRows),
            WorkbookSheet("Daily Summary", dailySummaryRows),
            WorkbookSheet("Practice Log", eventRows),
        )
    }
}
