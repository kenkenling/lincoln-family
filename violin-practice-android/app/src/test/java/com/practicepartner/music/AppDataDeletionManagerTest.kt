package com.practicepartner.music

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataDeletionManagerTest {
    @Test
    fun reportsWhenAndroidStartsTheApplicationDataWipe() {
        var calls = 0
        val manager = AppDataDeletionManager {
            calls += 1
            true
        }

        assertTrue(manager.deleteAllData())
        assertTrue(calls == 1)
    }

    @Test
    fun reportsFailureWithoutPretendingDataWasDeleted() {
        val manager = AppDataDeletionManager { false }

        assertFalse(manager.deleteAllData())
    }
}
