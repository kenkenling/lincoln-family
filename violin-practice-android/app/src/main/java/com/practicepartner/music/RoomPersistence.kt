package com.practicepartner.music

internal fun PracticeDatabase.loadPracticeSnapshot(
    childProfileId: String,
): PracticeSnapshot = practiceSnapshotFromEntities(
    assignments = assignmentDao().loadForProfile(childProfileId),
    assignmentHistory = assignmentHistoryDao().loadForProfile(childProfileId),
    progress = dailyProgressDao().loadForProfile(childProfileId),
    events = practiceEventDao().loadForProfile(childProfileId),
    sessions = practiceSessionDao().loadForProfile(childProfileId),
)

internal fun PracticeDatabase.replacePracticeSnapshot(
    profile: ChildProfile,
    snapshot: PracticeSnapshot,
) {
    runInTransaction {
        replacePracticeSnapshotInTransaction(profile, snapshot)
    }
}

internal fun PracticeDatabase.replacePracticeSnapshotInTransaction(
    profile: ChildProfile,
    snapshot: PracticeSnapshot,
) {
    val scoped = snapshot.scopedTo(profile)
    deletePracticeData(profile.id)
    assignmentDao().upsertIfNotEmpty(
        scoped.tasks.mapIndexed { index, assignment -> assignment.toEntity(index) },
    )
    assignmentHistoryDao().upsertIfNotEmpty(
        scoped.assignmentHistory.map(AssignmentHistory::toEntity),
    )
    dailyProgressDao().upsertIfNotEmpty(
        scoped.progressByDate.values.map(DailyProgress::toEntity),
    )
    practiceEventDao().upsertIfNotEmpty(
        scoped.events.map(PracticeEvent::toEntity),
    )
    practiceSessionDao().upsertIfNotEmpty(
        scoped.sessions.map(PracticeSession::toEntity),
    )
}

internal fun PracticeDatabase.applyPracticeChanges(
    childProfileId: String,
    changes: PracticePersistenceChangeSet,
) {
    if (changes.isEmpty) return
    runInTransaction {
        assignmentHistoryDao().apply {
            upsertIfNotEmpty(changes.assignmentHistoryToUpsert)
            deleteIfNotEmpty(changes.assignmentHistoryToDelete)
        }
        assignmentDao().apply {
            upsertIfNotEmpty(changes.assignmentsToUpsert)
            deleteIfNotEmpty(changes.assignmentIdsToDelete)
        }
        dailyProgressDao().apply {
            upsertIfNotEmpty(changes.progressToUpsert)
            deleteIfNotEmpty(childProfileId, changes.progressDatesToDelete)
        }
        practiceEventDao().apply {
            upsertIfNotEmpty(changes.eventsToUpsert)
            deleteIfNotEmpty(changes.eventIdsToDelete)
        }
        practiceSessionDao().apply {
            upsertIfNotEmpty(changes.sessionsToUpsert)
            deleteIfNotEmpty(changes.sessionIdsToDelete)
        }
    }
}

internal fun PracticeDatabase.deletePracticeData(childProfileId: String) {
    assignmentHistoryDao().deleteForProfile(childProfileId)
    assignmentDao().deleteForProfile(childProfileId)
    dailyProgressDao().deleteForProfile(childProfileId)
    practiceEventDao().deleteForProfile(childProfileId)
    practiceSessionDao().deleteForProfile(childProfileId)
}

private fun AssignmentDao.upsertIfNotEmpty(values: List<AssignmentEntity>) {
    if (values.isNotEmpty()) upsertAll(values)
}

private fun AssignmentDao.deleteIfNotEmpty(ids: List<String>) {
    if (ids.isNotEmpty()) deleteByIds(ids)
}

private fun AssignmentHistoryDao.upsertIfNotEmpty(
    values: List<AssignmentHistoryEntity>,
) {
    if (values.isNotEmpty()) upsertAll(values)
}

private fun AssignmentHistoryDao.deleteIfNotEmpty(
    values: List<AssignmentHistoryEntity>,
) {
    if (values.isNotEmpty()) deleteAll(values)
}

private fun DailyProgressDao.upsertIfNotEmpty(values: List<DailyProgressEntity>) {
    if (values.isNotEmpty()) upsertAll(values)
}

private fun DailyProgressDao.deleteIfNotEmpty(
    childProfileId: String,
    dates: List<String>,
) {
    if (dates.isNotEmpty()) deleteByDates(childProfileId, dates)
}

private fun PracticeEventDao.upsertIfNotEmpty(values: List<PracticeEventEntity>) {
    if (values.isNotEmpty()) upsertAll(values)
}

private fun PracticeEventDao.deleteIfNotEmpty(ids: List<String>) {
    if (ids.isNotEmpty()) deleteByIds(ids)
}

private fun PracticeSessionDao.upsertIfNotEmpty(values: List<PracticeSessionEntity>) {
    if (values.isNotEmpty()) upsertAll(values)
}

private fun PracticeSessionDao.deleteIfNotEmpty(ids: List<String>) {
    if (ids.isNotEmpty()) deleteByIds(ids)
}
