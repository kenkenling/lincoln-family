package com.lkeducation.lkpractice

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

internal fun practiceViewModelFactory(
    store: PracticeDataStore,
    familyStore: FamilyDataStore,
    localWorkbookStore: WorkbookStorage,
    recordingManager: RecordingManager,
    profileArchiveDestination: ProfileArchiveDestination,
): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PracticeViewModel(
                store = store,
                familyStore = familyStore,
                localWorkbookStore = localWorkbookStore,
                recordingManager = recordingManager,
                profileArchiveDestination = profileArchiveDestination,
            ) as T
    }

internal fun androidProfileArchiveDestination(
    context: Context,
): ProfileArchiveDestination {
    val appContext = context.applicationContext
    return ProfileArchiveDestination { uriText ->
        appContext.contentResolver.openOutputStream(uriText.toUri(), "wt")
            ?: error("The selected archive file could not be opened.")
    }
}
