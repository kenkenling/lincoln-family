package com.practicepartner.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

internal fun practiceViewModelFactory(context: Context): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PracticeViewModel(
                appContext = context.applicationContext,
                store = PracticeStore(context.applicationContext),
                familyStore = FamilyStore(context.applicationContext),
                localWorkbookStore = LocalWorkbookStore(context.applicationContext),
            ) as T
    }
