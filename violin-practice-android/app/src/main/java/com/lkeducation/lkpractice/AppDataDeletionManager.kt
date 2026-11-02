package com.lkeducation.lkpractice

import android.app.ActivityManager
import android.content.Context

internal class AppDataDeletionManager(
    private val clearApplicationUserData: () -> Boolean,
) {
    // The platform wipe is intentionally used instead of clearing individual stores while a
    // ViewModel is alive; it covers current and future preferences, databases, and managed files
    // atomically, then terminates the app process so in-memory state cannot recreate deleted data.
    constructor(context: Context) : this(
        clearApplicationUserData = {
            context.applicationContext
                .getSystemService(ActivityManager::class.java)
                ?.clearApplicationUserData() == true
        },
    )

    fun deleteAllData(): Boolean = clearApplicationUserData()
}
