package com.lkeducation.lkpractice

import java.time.Instant

data class WorkbookSheet(
    val name: String,
    val rows: List<List<Any>>,
)

object PracticeWorkbookTemplate {
    const val TEMPLATE_VERSION = "8"

    val profileHeaders = listOf(
        "Key",
        "Value",
    )

    val assignmentHeaders = listOf(
        "Assignment ID",
        "Student ID",
        "Instrument",
        "Start Date",
        "End Date",
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
        "Assignment Type",
        "Goal Type",
        "Target Minutes",
        "Practice Steps",
        "Revision",
        "Archived UTC",
    )

    val dailyProgressHeaders = listOf(
        "Date",
        "Student ID",
        "Instrument",
        "Assignment ID",
        "Assignment Title",
        "Repetitions Completed",
        "Target Repetitions",
        "Practice Seconds",
        "Target Practice Seconds",
        "Completed",
    )

    val dailySummaryHeaders = listOf(
        "Date",
        "Student ID",
        "Instrument",
        "Total Practice Seconds",
        "Assignments Completed",
        "Assignments Planned",
        "Repetitions Completed",
        "Sessions",
        "Reflections",
    )

    val practiceLogHeaders = listOf(
        "Event ID",
        "Date",
        "Student ID",
        "Instrument",
        "Session ID",
        "Assignment ID",
        "Assignment Title",
        "Event",
        "Timestamp UTC",
        "Recording Saved",
        "Recording Filename",
    )

    val practiceSessionHeaders = listOf(
        "Session ID",
        "Date",
        "Student ID",
        "Instrument",
        "Assignment ID",
        "Assignment Revision",
        "Assignment Title",
        "Assignment Type",
        "Goal Type",
        "Target Repetitions",
        "Target Practice Seconds",
        "Mode",
        "Started UTC",
        "Ended UTC",
        "Practice Seconds",
        "Repetitions Completed",
        "Status",
        "Reflection",
        "Student Initiated",
    )

    val requiredSheetNames = listOf(
        "Profile",
        "Assignments",
        "Daily Progress",
        "Daily Summary",
        "Practice Sessions",
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
        "Practice Sessions" to practiceSessionHeaders,
        "Practice Log" to practiceLogHeaders,
    )

    fun title(profile: StudentProfile): String =
        "LK Practice - ${profile.name.trim().ifBlank { "Student" }}"

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
            listOf("Storage", "Local Excel file selected on this device"),
            listOf(
                "Assignment Updates",
                "Two-way through the Assignments sheet. Save app changes before loading edits from the file.",
            ),
            listOf(
                "Practice History",
                "Local-first. Workbook progress and log rows are snapshots and are not loaded into the app yet.",
            ),
            listOf("Last Saved UTC", updatedAt),
        )

        val assignmentRows = buildList {
            add(assignmentHeaders)
            snapshot.tasks.sortedWith(compareBy(PracticeTask::startDate, PracticeTask::title))
                .forEach { task ->
                    add(
                        listOf(
                            task.id,
                            task.childProfileId.ifBlank { profile.id },
                            task.instrument.ifBlank { profile.instrument },
                            task.startDate.value,
                            task.endDate.value,
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
                            task.assignmentType.name,
                            task.goalType.name,
                            if (task.goalType == PracticeGoalType.TIME) {
                                task.targetSeconds / 60
                            } else {
                                ""
                            },
                            PracticeMode.entries
                                .filter(task.enabledModes::contains)
                                .joinToString("|", transform = PracticeMode::name),
                            task.revision,
                            task.archivedAtMillis?.let {
                                Instant.ofEpochMilli(it).toString()
                            }.orEmpty(),
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
                        snapshot.tasks.filter { it.isScheduledOn(progress.date) }.map { it.id }
                    ).toSortedSet()
                if (taskIds.isEmpty()) {
                    add(
                        listOf(
                            progress.date.value,
                            progress.childProfileId.ifBlank { profile.id },
                            profile.instrument,
                            "",
                            "",
                            0,
                            0,
                                0,
                                0,
                            "No",
                        ),
                    )
                } else {
                    taskIds.forEach { taskId ->
                        val task = taskById[taskId]
                        val practiceSeconds = snapshot.sessions
                            .filter { it.date == progress.date && it.assignmentId == taskId }
                            .sumOf(PracticeSession::elapsedSeconds)
                        val completed = taskId in progress.completedTaskIds ||
                            when (task?.goalType) {
                                PracticeGoalType.REPETITIONS ->
                                    progress.repetitionsFor(taskId) >= task.repetitions
                                PracticeGoalType.TIME ->
                                    task.targetSeconds > 0 &&
                                        practiceSeconds >= task.targetSeconds
                                null -> false
                            }
                        add(
                            listOf(
                                progress.date.value,
                                progress.childProfileId.ifBlank { profile.id },
                                task?.instrument?.ifBlank { profile.instrument }
                                    ?: profile.instrument,
                                taskId,
                                task?.title.orEmpty(),
                                progress.repetitionsFor(taskId),
                                if (task?.goalType == PracticeGoalType.REPETITIONS) {
                                    task.repetitions
                                } else {
                                    0
                                },
                                practiceSeconds,
                                if (task?.goalType == PracticeGoalType.TIME) {
                                    task.targetSeconds
                                } else {
                                    0
                                },
                                if (completed) "Yes" else "No",
                            ),
                        )
                    }
                }
            }
        }

        val dailySummaryRows = buildList {
            add(dailySummaryHeaders)
            val dates = (
                snapshot.tasks.flatMap(PracticeTask::scheduledDates) +
                    snapshot.progressByDate.keys
                ).distinct().sorted()
            dates.forEach { date ->
                val progress = snapshot.progressByDate[date] ?: DailyProgress(
                    date = date,
                    childProfileId = profile.id,
                )
                val tasks = snapshot.tasks.filter { it.isScheduledOn(date) }
                val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
                val sessions = snapshot.sessions.filter { it.date == date }
                add(
                    listOf(
                        date.value,
                        profile.id,
                        profile.instrument,
                        progress.elapsedSeconds,
                        progress.completedTaskIds.count { it in taskIds },
                        tasks.size,
                        tasks.sumOf { progress.repetitionsFor(it.id) },
                        sessions.size,
                        sessions.count { it.reflection != null },
                    ),
                )
            }
        }

        val practiceSessionRows = buildList {
            add(practiceSessionHeaders)
            snapshot.sessions.takeLast(MAX_PRACTICE_SESSIONS).forEach { session ->
                add(
                    listOf(
                        session.id,
                        session.date.value,
                        session.childProfileId.ifBlank { profile.id },
                        session.instrument.ifBlank { profile.instrument },
                        session.assignmentId,
                        session.assignmentRevision,
                        session.assignmentTitle,
                        session.assignmentType.name,
                        session.goalType.name,
                        if (session.goalType == PracticeGoalType.REPETITIONS) {
                            session.targetRepetitions
                        } else {
                            0
                        },
                        if (session.goalType == PracticeGoalType.TIME) {
                            session.targetSeconds
                        } else {
                            0
                        },
                        session.mode.name,
                        Instant.ofEpochMilli(session.startedAtMillis).toString(),
                        session.endedAtMillis?.let {
                            Instant.ofEpochMilli(it).toString()
                        }.orEmpty(),
                        session.elapsedSeconds,
                        session.repetitionsCompleted,
                        session.status.name,
                        session.reflection?.name.orEmpty(),
                        if (session.studentInitiated) "Yes" else "No",
                    ),
                )
            }
        }

        val eventRows = buildList {
            add(practiceLogHeaders)
            snapshot.events.takeLast(MAX_PRACTICE_EVENTS).forEach { event ->
                add(
                    listOf(
                        event.id,
                        event.date.value,
                        event.childProfileId.ifBlank { profile.id },
                        taskById[event.taskId]?.instrument?.ifBlank { profile.instrument }
                            ?: profile.instrument,
                        event.sessionId.orEmpty(),
                        event.taskId.orEmpty(),
                        event.taskTitle,
                        event.event,
                        Instant.ofEpochMilli(event.atMillis).toString(),
                        if (event.videoUri != null || event.videoPath != null) "Yes" else "No",
                        event.videoFileName.orEmpty(),
                    ),
                )
            }
        }

        return listOf(
            WorkbookSheet("Profile", profileRows),
            WorkbookSheet("Assignments", assignmentRows),
            WorkbookSheet("Daily Progress", progressRows),
            WorkbookSheet("Daily Summary", dailySummaryRows),
            WorkbookSheet("Practice Sessions", practiceSessionRows),
            WorkbookSheet("Practice Log", eventRows),
        )
    }
}
