package com.practicepartner.music

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom

class FamilyStore private constructor(
    private val database: PracticeDatabase,
    private val runner: DatabaseRunner,
    private val prefs: SharedPreferences,
    private val migration: LegacyRoomMigration,
) : FamilyDataStore {
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
        prefs = context.getSharedPreferences(
            PRACTICE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ),
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
        prefs: SharedPreferences,
        migration: LegacyRoomMigration,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit,
    ) : this(database, runner, prefs, migration)

    override fun loadHousehold(): Household = runner.run(::readyHousehold)

    private fun readyHousehold(): Household {
        migrateRetiredConnections()
        val household = migration.migrateAllIfNeeded()
        migrateScopedSettings(household.activeChildProfileId)
        return household
    }

    override fun saveHousehold(household: Household) {
        val normalized = normalizeHousehold(household)
        runner.run {
            database.runInTransaction {
                database.householdDao().upsert(normalized.toEntity())
                database.childProfileDao().upsertAll(
                    normalized.childProfiles.mapIndexed { index, profile ->
                        profile.toEntity(normalized.id, index)
                    },
                )
                database.childProfileDao().deleteExcept(
                    normalized.id,
                    normalized.childProfiles.map(ChildProfile::id),
                )
            }
        }
    }

    override fun saveChildProfile(profile: ChildProfile): Household = runner.run {
        val household = readyHousehold()
        val profileIndex = household.childProfiles.indexOfFirst { it.id == profile.id }
        require(profileIndex >= 0) { "The child profile does not exist." }
        val updated = normalizeHousehold(
            household.copy(
                childProfiles = household.childProfiles.map {
                    if (it.id == profile.id) profile else it
                },
                activeChildProfileId = profile.id,
            ),
        )
        database.runInTransaction {
            database.childProfileDao().upsert(
                updated.childProfiles[profileIndex].toEntity(updated.id, profileIndex),
            )
            database.householdDao().updateActiveChild(updated.id, profile.id)
        }
        updated
    }

    override fun addChildProfile(profile: ChildProfile): Household = runner.run {
        val household = readyHousehold()
        require(household.childProfiles.none { it.id == profile.id }) {
            "A child profile with this ID already exists."
        }
        val updated = normalizeHousehold(
            household.copy(childProfiles = household.childProfiles + profile),
        )
        database.childProfileDao().upsert(
            updated.childProfiles.last().toEntity(
                householdId = updated.id,
                sortOrder = updated.childProfiles.lastIndex,
            ),
        )
        updated
    }

    override fun setActiveChildProfile(childProfileId: String): Household = runner.run {
        val household = readyHousehold()
        require(household.childProfiles.any { it.id == childProfileId }) {
            "The selected child profile does not exist."
        }
        database.householdDao().updateActiveChild(household.id, childProfileId)
        household.copy(activeChildProfileId = childProfileId)
    }

    override fun removeChildProfile(childProfileId: String): Household {
        val updated = runner.run {
            val household = readyHousehold()
            require(household.childProfiles.size > 1) {
                "The household must keep at least one child profile."
            }
            require(household.childProfiles.any { it.id == childProfileId }) {
                "The child profile does not exist."
            }
            val remaining = household.childProfiles.filterNot { it.id == childProfileId }
            val next = household.copy(
                childProfiles = remaining,
                activeChildProfileId = household.activeChildProfileId
                    .takeUnless { it == childProfileId }
                    ?: remaining.first().id,
            )
            database.runInTransaction {
                database.childProfileDao().delete(childProfileId)
                database.householdDao().updateActiveChild(
                    next.id,
                    next.activeChildProfileId,
                )
                database.childProfileDao().upsertAll(
                    remaining.mapIndexed { index, profile ->
                        profile.toEntity(next.id, index)
                    },
                )
            }
            next
        }
        prefs.edit(commit = true) {
            remove(scopedKey("local_data_revision", childProfileId))
            remove(scopedKey("last_local_file_revision", childProfileId))
            remove(scopedKey("last_local_file_save_at", childProfileId))
        }
        return updated
    }

    override fun loadLocalRevision(childProfileId: String): Long =
        prefs.getLong(scopedKey("local_data_revision", childProfileId), 0L).coerceAtLeast(0L)

    override fun bumpLocalRevision(childProfileId: String): Long {
        val next = loadLocalRevision(childProfileId) + 1L
        prefs.edit(commit = true) {
            putLong(scopedKey("local_data_revision", childProfileId), next)
        }
        return next
    }

    override fun loadLastFileSaveRevision(childProfileId: String): Long =
        prefs.getLong(scopedKey("last_local_file_revision", childProfileId), -1L)
            .coerceAtLeast(-1L)

    override fun saveLastFileSaveRevision(childProfileId: String, revision: Long) {
        prefs.edit(commit = true) {
            putLong(
                scopedKey("last_local_file_revision", childProfileId),
                revision.coerceAtLeast(0L),
            )
        }
    }

    override fun loadLastFileSaveAt(childProfileId: String): Long? =
        prefs.getLong(scopedKey("last_local_file_save_at", childProfileId), 0L)
            .takeIf { it > 0L }

    override fun saveLastFileSaveAt(childProfileId: String, atMillis: Long) {
        prefs.edit(commit = true) {
            putLong(scopedKey("last_local_file_save_at", childProfileId), atMillis)
        }
    }

    override fun hasAdultPin(): Boolean =
        !prefs.getString(ADULT_PIN_SALT_KEY, null).isNullOrBlank() &&
            !prefs.getString(ADULT_PIN_HASH_KEY, null).isNullOrBlank()

    override fun setAdultPin(pin: String): Boolean {
        if (!isValidAdultPin(pin)) return false
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = adultPinHash(salt, pin)
        prefs.edit(commit = true) {
            putString(ADULT_PIN_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(ADULT_PIN_HASH_KEY, Base64.encodeToString(hash, Base64.NO_WRAP))
        }
        return true
    }

    override fun verifyAdultPin(pin: String): Boolean {
        if (!isValidAdultPin(pin)) return false
        val salt = prefs.getString(ADULT_PIN_SALT_KEY, null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?: return false
        val expected = prefs.getString(ADULT_PIN_HASH_KEY, null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?: return false
        return MessageDigest.isEqual(expected, adultPinHash(salt, pin))
    }

    private fun migrateScopedSettings(childProfileId: String) {
        if (prefs.getBoolean(SCOPED_SETTINGS_MIGRATION_KEY, false)) return
        prefs.edit(commit = true) {
            putLong(
                scopedKey("local_data_revision", childProfileId),
                prefs.getLong("local_data_revision", 0L),
            )
            putLong(
                scopedKey("last_local_file_revision", childProfileId),
                prefs.getLong("last_local_file_revision", -1L),
            )
            putLong(
                scopedKey("last_local_file_save_at", childProfileId),
                prefs.getLong("last_local_file_save_at", 0L),
            )
            putBoolean(SCOPED_SETTINGS_MIGRATION_KEY, true)
        }
    }

    private fun migrateRetiredConnections() {
        if (prefs.getBoolean(LOCAL_ONLY_MIGRATION_KEY, false)) return
        prefs.edit(commit = true) {
            RETIRED_CONNECTION_KEYS.forEach(::remove)
            remove("excel_workbook_uri")
            remove("excel_workbook_name")
            remove("last_cloud_sync_at")
            remove("last_synced_revision")
            putBoolean(LOCAL_ONLY_MIGRATION_KEY, true)
        }
    }

    private fun scopedKey(prefix: String, childProfileId: String): String =
        "$prefix:$childProfileId"
}

internal fun isValidAdultPin(pin: String): Boolean = pin.matches(Regex("\\d{4}"))

private fun adultPinHash(salt: ByteArray, pin: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray(Charsets.UTF_8))

private const val SCOPED_SETTINGS_MIGRATION_KEY = "child_scoped_settings_migration_v1"
private const val ADULT_PIN_SALT_KEY = "adult_pin_salt_v1"
private const val ADULT_PIN_HASH_KEY = "adult_pin_hash_v1"
