package com.practicepartner.music

import android.content.Context

data class PracticeLoadResult(
    val snapshot: PracticeSnapshot,
    val warning: String? = null,
)

class PracticeStore private constructor(
    private val database: PracticeDatabase,
    private val runner: DatabaseRunner,
    private val migration: LegacyRoomMigration,
) : PracticeDataStore {
    constructor(context: Context) : this(
        dependencies = PracticeDatabaseProvider.get(context),
        context = context.applicationContext,
    )

    private constructor(
        dependencies: RoomStoreDependencies,
        context: Context,
    ) : this(
        database = dependencies.database,
        runner = dependencies.runner,
        migration = LegacyRoomMigration(
            database = dependencies.database,
            prefs = context.getSharedPreferences(
                PRACTICE_PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ),
            legacyPrefs = context.getSharedPreferences(
                LEGACY_PRACTICE_PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ),
        ),
    )

    internal constructor(
        database: PracticeDatabase,
        runner: DatabaseRunner,
        migration: LegacyRoomMigration,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit,
    ) : this(database, runner, migration)

    override fun load(
        profile: ChildProfile,
        currentDate: DateKey,
    ): PracticeSnapshot = loadWithStatus(profile, currentDate).snapshot

    override fun loadWithStatus(
        profile: ChildProfile,
        currentDate: DateKey,
    ): PracticeLoadResult = runner.run {
        requireProfileExists(profile)
        migration.migrateProfileIfNeeded(profile, currentDate)
        PracticeLoadResult(
            snapshot = database.loadPracticeSnapshot(profile.id).scopedTo(profile),
            warning = migration.consumeWarning(profile.id),
        )
    }

    override fun replace(
        profile: ChildProfile,
        snapshot: PracticeSnapshot,
    ) = runner.run {
        requireProfileExists(profile)
        migration.migrateProfileIfNeeded(profile, currentDateKey())
        database.replacePracticeSnapshot(profile, snapshot)
    }

    override fun applyChanges(
        profile: ChildProfile,
        previous: PracticeSnapshot,
        next: PracticeSnapshot,
    ) = runner.run {
        requireProfileExists(profile)
        migration.migrateProfileIfNeeded(profile, currentDateKey())
        val previousScoped = previous.scopedTo(profile)
        val nextScoped = next.scopedTo(profile)
        database.applyPracticeChanges(
            childProfileId = profile.id,
            changes = practicePersistenceChanges(previousScoped, nextScoped),
        )
    }

    override fun delete(profile: ChildProfile) = runner.run {
        database.runInTransaction {
            database.deletePracticeData(profile.id)
        }
        migration.clearProfileLegacyData(profile.id)
    }

    override fun referencedRecordingPaths(
        profiles: List<ChildProfile>,
    ): Set<String> = runner.run {
        val profileIds = profiles.mapTo(mutableSetOf(), ChildProfile::id)
        database.practiceEventDao()
            .loadRecordingReferences()
            .asSequence()
            .filter { it.childProfileId in profileIds }
            .mapNotNull(RecordingPathReference::videoPath)
            .toSet()
    }

    private fun requireProfileExists(profile: ChildProfile) {
        require(database.childProfileDao().find(profile.id) != null) {
            "Create the child profile before loading or saving practice data."
        }
    }
}

internal const val PRACTICE_PREFERENCES_NAME = "practice_partner_v2"
internal const val LEGACY_PRACTICE_PREFERENCES_NAME = "practice_partner_v1"
