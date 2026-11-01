package com.practicepartner.music

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class FamilyStore(context: Context) : FamilyDataStore {
    private val prefs = context.getSharedPreferences("practice_partner_v2", Context.MODE_PRIVATE)

    override fun loadHousehold(): Household {
        migrateRetiredConnections()
        prefs.getString(HOUSEHOLD_KEY, null)
            ?.let(::decodeHousehold)
            ?.let { household ->
                migrateScopedSettings(household.activeChildProfileId)
                return household
            }

        val defaultLegacyName = "Student"
        val legacyProfile = ChildProfile(
            id = prefs.getString("student_profile_id", null) ?: UUID.randomUUID().toString(),
            name = prefs.getString("student_profile_name", defaultLegacyName)
                .orEmpty()
                .ifBlank { defaultLegacyName },
            instrument = prefs.getString("student_profile_instrument", "Violin")
                .orEmpty()
                .ifBlank { "Violin" },
            excelWorkbookUri = prefs.getString("excel_workbook_uri", null),
            excelWorkbookName = prefs.getString("excel_workbook_name", null),
        )
        val household = Household(
            id = UUID.randomUUID().toString(),
            childProfiles = listOf(legacyProfile),
            activeChildProfileId = legacyProfile.id,
        )
        saveHousehold(household)
        migrateScopedSettings(legacyProfile.id)
        return household
    }

    override fun saveHousehold(household: Household) {
        val normalized = normalize(household)
        val active = normalized.activeChildProfile
            ?: error("A household must contain an active child profile.")
        prefs.edit(commit = true) {
            putString(HOUSEHOLD_KEY, HouseholdJsonCodec.encode(normalized))

            // Keep the previous single-profile keys current so an older app build can still
            // open the active child after a rollback.
            putString("student_profile_id", active.id)
            putString("student_profile_name", active.name)
            putString("student_profile_instrument", active.instrument)
            putString("excel_workbook_uri", active.excelWorkbookUri)
            putString("excel_workbook_name", active.excelWorkbookName)
        }
    }

    override fun saveChildProfile(profile: ChildProfile): Household {
        val household = loadHousehold()
        require(household.childProfiles.any { it.id == profile.id }) {
            "The child profile does not exist."
        }
        val profiles = household.childProfiles.map {
            if (it.id == profile.id) profile else it
        }
        val updated = household.copy(
            childProfiles = profiles,
            activeChildProfileId = profile.id,
        )
        saveHousehold(updated)
        return updated
    }

    override fun addChildProfile(profile: ChildProfile): Household {
        val household = loadHousehold()
        require(household.childProfiles.none { it.id == profile.id }) {
            "A child profile with this ID already exists."
        }
        val updated = household.copy(
            childProfiles = household.childProfiles + profile,
        )
        saveHousehold(updated)
        return updated
    }

    override fun setActiveChildProfile(childProfileId: String): Household {
        val household = loadHousehold()
        require(household.childProfiles.any { it.id == childProfileId }) {
            "The selected child profile does not exist."
        }
        val updated = household.copy(activeChildProfileId = childProfileId)
        saveHousehold(updated)
        return updated
    }

    override fun removeChildProfile(childProfileId: String): Household {
        val household = loadHousehold()
        require(household.childProfiles.size > 1) {
            "The household must keep at least one child profile."
        }
        require(household.childProfiles.any { it.id == childProfileId }) {
            "The child profile does not exist."
        }
        val remaining = household.childProfiles.filterNot { it.id == childProfileId }
        val updated = household.copy(
            childProfiles = remaining,
            activeChildProfileId = household.activeChildProfileId
                .takeUnless { it == childProfileId }
                ?: remaining.first().id,
        )
        saveHousehold(updated)
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

    private fun decodeHousehold(value: String): Household? =
        HouseholdJsonCodec.decode(value)?.let(::normalize)

    private fun normalize(household: Household): Household {
        require(household.childProfiles.isNotEmpty()) {
            "A household must contain at least one child profile."
        }
        val profiles = household.childProfiles
            .map { profile ->
                profile.copy(
                    name = profile.name.trim().ifBlank { "Student" },
                    instrument = profile.instrument.trim().ifBlank { "Instrument" },
                )
            }
            .distinctBy(ChildProfile::id)
        val activeId = household.activeChildProfileId
            .takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        return household.copy(
            name = household.name.trim().ifBlank { "Our Family" },
            childProfiles = profiles,
            activeChildProfileId = activeId,
        )
    }

    private fun scopedKey(prefix: String, childProfileId: String): String =
        "$prefix:$childProfileId"
}

internal fun isValidAdultPin(pin: String): Boolean = pin.matches(Regex("\\d{4}"))

private fun adultPinHash(salt: ByteArray, pin: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray(Charsets.UTF_8))

private const val HOUSEHOLD_KEY = "household_v1"
private const val SCOPED_SETTINGS_MIGRATION_KEY = "child_scoped_settings_migration_v1"
private const val ADULT_PIN_SALT_KEY = "adult_pin_salt_v1"
private const val ADULT_PIN_HASH_KEY = "adult_pin_hash_v1"
