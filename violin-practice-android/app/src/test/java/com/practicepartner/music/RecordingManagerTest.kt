package com.practicepartner.music

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingManagerTest {
    @Test
    fun createsUniqueFilesInsideManagedDirectory() {
        withManager { directory, manager ->
            val requested = "2026-11-01 - Lincoln - Scales - Take 01.mp4"
            File(directory, requested).writeText("existing")

            val created = manager.createRecordingFile(requested)

            assertEquals("2026-11-01 - Lincoln - Scales - Take 02.mp4", created.name)
            assertEquals(directory.canonicalPath, created.parentFile?.canonicalPath)
        }
    }

    @Test
    fun rejectsFilesOutsideManagedStorage() {
        withManager { _, manager ->
            val outside = Files.createTempFile("outside-practice-", ".mp4").toFile()
            try {
                assertNull(manager.managedRecordingFile(outside.absolutePath))
                val result = manager.deleteRecording(outside.absolutePath)
                assertFalse(result.isManaged)
                assertFalse(result.deleted)
                assertTrue(outside.exists())
            } finally {
                outside.delete()
            }
        }
    }

    @Test
    fun removesOnlyUnreferencedManagedRecordings() {
        withManager { directory, manager ->
            val retained = managedFile(directory, "Take 01").apply { writeText("keep") }
            val orphan = managedFile(directory, "Take 02").apply { writeText("delete") }
            File(directory, "unrelated.mp4").writeText("not managed")

            val deleted = manager.cleanupOrphanedRecordings(setOf(retained.absolutePath))

            assertEquals(1, deleted)
            assertTrue(retained.exists())
            assertFalse(orphan.exists())
            assertTrue(File(directory, "unrelated.mp4").exists())
        }
    }

    @Test
    fun releasesWorkbookPermissionOnlyWhenNoProfileStillUsesIt() {
        val released = mutableListOf<String>()
        val manager = RecordingManager(
            managedDirectoriesProvider = { emptyList() },
            releaseUriPermission = released::add,
        )
        val deleted = ChildProfile(
            id = "deleted",
            name = "Deleted",
            instrument = "Violin",
            excelWorkbookUri = "content://practice/workbook",
        )

        manager.releaseWorkbookPermissionIfUnused(
            deletedProfile = deleted,
            remainingProfiles = listOf(
                deleted.copy(id = "remaining", name = "Remaining"),
            ),
        )
        assertTrue(released.isEmpty())

        manager.releaseWorkbookPermissionIfUnused(deleted, emptyList())
        assertEquals(listOf("content://practice/workbook"), released)
    }

    private fun withManager(block: (File, RecordingManager) -> Unit) {
        val directory = Files.createTempDirectory("recording-manager-").toFile()
        try {
            val manager = RecordingManager(managedDirectoriesProvider = { listOf(directory) })
            assertNotNull(manager)
            block(directory, manager)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun managedFile(directory: File, take: String): File =
        File(directory, "2026-11-01 - Lincoln - Scales - $take.mp4")
}
