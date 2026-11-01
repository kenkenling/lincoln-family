package com.practicepartner.music

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal class ProfileDataManager(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val store: PracticeStore,
    private val familyStore: FamilyStore,
    private val recordingManager: RecordingManager,
    private val wallClock: () -> Long,
    private val setMessage: (String) -> Unit,
) {
    fun addChildProfile(
        nameText: String,
        instrumentText: String,
    ): ProfileDataResult<AddedChildProfile> {
        val name = nameText.trim()
        val instrument = instrumentText.trim()
        profileValidationError(name, instrument)?.let {
            return ProfileDataResult(error = it)
        }
        val profile = ChildProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            instrument = instrument,
        )
        val household = familyStore.addChildProfile(profile)
        store.save(profile, PracticeSnapshot())
        return ProfileDataResult(AddedChildProfile(household, profile))
    }

    fun saveChildProfile(
        currentProfile: ChildProfile,
        currentSnapshot: PracticeSnapshot,
        nameText: String,
        instrumentText: String,
    ): ProfileDataResult<SavedChildProfile> {
        val name = nameText.trim()
        val instrument = instrumentText.trim()
        profileValidationError(name, instrument)?.let {
            return ProfileDataResult(error = it)
        }
        val profile = currentProfile.copy(
            name = name,
            instrument = instrument,
        )
        val household = familyStore.saveChildProfile(profile)
        val snapshot = currentSnapshot.scopedTo(profile)
        if (snapshot != currentSnapshot) {
            store.save(profile, snapshot)
        }
        return ProfileDataResult(
            SavedChildProfile(
                household = household,
                profile = profile,
                snapshot = snapshot,
                changed = profile != currentProfile,
            ),
        )
    }

    fun suggestedArchiveFileName(
        household: Household,
        currentProfile: ChildProfile,
        childProfileId: String,
    ): String {
        val profile = household.childProfiles.firstOrNull { it.id == childProfileId }
            ?: currentProfile
        return ProfileArchiveWriter.fileName(profile)
    }

    fun exportChildProfileArchive(
        household: Household,
        childProfileId: String,
        uriText: String,
    ) {
        val profile = household.childProfiles.firstOrNull { it.id == childProfileId }
        if (profile == null) {
            setMessage("That student profile is no longer available.")
            return
        }
        val uri = uriText.trim()
        if (!uri.startsWith("content://")) {
            setMessage("Choose a destination with the Android file picker.")
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val snapshot = store.load(profile)
                    val recordingFiles = snapshot.events
                        .mapNotNull(PracticeEvent::videoPath)
                        .distinct()
                        .mapNotNull(recordingManager::managedRecordingFile)
                    val output = appContext.contentResolver.openOutputStream(uri.toUri(), "wt")
                        ?: error("The selected archive file could not be opened.")
                    output.use {
                        ProfileArchiveWriter.write(
                            output = it,
                            profile = profile,
                            snapshot = snapshot,
                            recordingFiles = recordingFiles,
                            exportedAtMillis = wallClock(),
                        )
                    }
                }
            }.onSuccess { result ->
                setMessage(profileArchiveSuccessMessage(profile, result))
            }.onFailure { error ->
                setMessage(error.message ?: "The student data archive could not be created.")
            }
        }
    }

    fun deleteChildProfile(
        household: Household,
        currentProfile: ChildProfile,
        childProfileId: String,
    ): ProfileDataResult<DeletedChildProfile> {
        val profile = household.childProfiles.firstOrNull { it.id == childProfileId }
            ?: return ProfileDataResult(error = "That student profile is no longer available.")
        val snapshot = store.load(profile)
        val otherProfiles = household.childProfiles.filterNot { it.id == profile.id }
        val failedRecordingDeletes = recordingManager.deleteUnsharedRecordings(
            recordingPaths = snapshot.events.mapNotNull(PracticeEvent::videoPath),
            pathsReferencedElsewhere = store.referencedRecordingPaths(otherProfiles),
        )
        recordingManager.releaseWorkbookPermissionIfUnused(profile, otherProfiles)

        val deletingActiveProfile = profile.id == currentProfile.id
        store.delete(profile)
        val updatedHousehold = familyStore.removeChildProfile(profile.id)
        return ProfileDataResult(
            DeletedChildProfile(
                household = updatedHousehold,
                nextActiveProfileId = updatedHousehold.activeChildProfileId,
                deletingActiveProfile = deletingActiveProfile,
                message = profileDeletedMessage(profile, failedRecordingDeletes),
            ),
        )
    }
}

internal data class AddedChildProfile(
    val household: Household,
    val profile: ChildProfile,
)

internal data class SavedChildProfile(
    val household: Household,
    val profile: ChildProfile,
    val snapshot: PracticeSnapshot,
    val changed: Boolean,
)

internal data class DeletedChildProfile(
    val household: Household,
    val nextActiveProfileId: String,
    val deletingActiveProfile: Boolean,
    val message: String,
)

internal data class ProfileDataResult<T>(
    val value: T? = null,
    val error: String? = null,
)

private fun profileValidationError(name: String, instrument: String): String? = when {
    name.isBlank() -> "Add the student’s name."
    name.length > 80 -> "Keep the student’s name under 80 characters."
    instrument.isBlank() -> "Add an instrument."
    instrument.length > 80 -> "Keep the instrument under 80 characters."
    else -> null
}

private fun profileArchiveSuccessMessage(
    profile: ChildProfile,
    result: ProfileArchiveResult,
): String = buildString {
    append("Exported ${profile.name}’s student data")
    if (result.recordingCount > 0) {
        append(" with ${result.recordingCount} recording")
        if (result.recordingCount != 1) append('s')
    }
    append('.')
    if (result.skippedRecordingCount > 0) {
        append(" ${result.skippedRecordingCount} unavailable recording")
        if (result.skippedRecordingCount != 1) append('s')
        append(" could not be included.")
    }
}

private fun profileDeletedMessage(
    profile: ChildProfile,
    failedRecordingDeletes: Int,
): String = buildString {
    append("Deleted ${profile.name}’s app profile, practice history, and managed recordings.")
    if (!profile.excelWorkbookName.isNullOrBlank()) {
        append(" The external Excel file was left in place.")
    }
    if (failedRecordingDeletes > 0) {
        append(" $failedRecordingDeletes recording file")
        if (failedRecordingDeletes != 1) append('s')
        append(" could not be removed.")
    }
}
