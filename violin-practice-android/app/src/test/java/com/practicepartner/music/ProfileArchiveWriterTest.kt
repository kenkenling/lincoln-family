package com.practicepartner.music

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ProfileArchiveWriterTest {
    @Test
    fun archiveContainsPortableDataAndAvailableRecordingWithoutPrivatePaths() {
        val profile = ChildProfile(
            id = "student",
            name = "Lincoln",
            instrument = "Violin",
        )
        val task = PracticeTask(
            id = "assignment",
            childProfileId = profile.id,
            instrument = profile.instrument,
            assignedDate = DateKey("2026-11-01"),
            title = "Boreas passage",
            source = "content://private/reference",
            sourceType = SourceType.LOCAL,
            startSeconds = 0,
            endSeconds = 20,
            repetitions = 3,
            sheetMusicUri = "content://private/sheet",
        )
        val recordingBytes = "private video bytes".toByteArray()
        val recording = Files.createTempFile("lk-practice-archive-", ".mp4").toFile()
            .apply { writeBytes(recordingBytes) }
        val snapshot = PracticeSnapshot(
            tasks = listOf(task),
            sessions = listOf(
                PracticeSession(
                    id = "session",
                    childProfileId = profile.id,
                    assignmentId = task.id,
                    assignmentTitle = task.title,
                    instrument = profile.instrument,
                    date = task.assignedDate,
                    mode = PracticeMode.PLAY_YOURSELF,
                    modesUsed = setOf(
                        PracticeMode.LISTEN,
                        PracticeMode.PLAY_YOURSELF,
                    ),
                    startedAtMillis = 1_799_999_000_000,
                    endedAtMillis = 1_799_999_060_000,
                    elapsedSeconds = 60,
                    status = PracticeSessionStatus.ENDED,
                    reflection = PracticeReflection.MAKING_PROGRESS,
                    masteryStatus = MasteryStatus.STUDENT_CONFIDENT,
                ),
            ),
            events = listOf(
                PracticeEvent(
                    id = "event",
                    date = task.assignedDate,
                    taskId = task.id,
                    taskTitle = task.title,
                    event = "Practice take saved privately",
                    atMillis = 1_799_999_050_000,
                    videoUri = "content://private/take",
                    videoPath = recording.absolutePath,
                    videoFileName = "2026-11-01 - Lincoln - Boreas passage - Take 01.mp4",
                    childProfileId = profile.id,
                    sessionId = "session",
                ),
            ),
        )
        val output = ByteArrayOutputStream()

        val result = ProfileArchiveWriter.write(
            output = output,
            profile = profile,
            snapshot = snapshot,
            recordingFiles = listOf(recording),
            exportedAtMillis = 1_800_000_000_000,
        )
        val entries = unzip(output.toByteArray())
        recording.delete()

        assertEquals(1, result.recordingCount)
        assertEquals(0, result.skippedRecordingCount)
        assertEquals(
            setOf(
                "README.txt",
                "profile.json",
                "assignments.csv",
                "practice-history.json",
                "recordings/${recording.name}",
            ),
            entries.keys,
        )
        val renderedText = entries
            .filterKeys { !it.startsWith("recordings/") }
            .values
            .joinToString("|") { it.toString(Charsets.UTF_8) }
        assertTrue(renderedText.contains("MAKING_PROGRESS"))
        assertTrue(renderedText.contains("Boreas passage"))
        val archivedModes = JSONObject(
            entries.getValue("practice-history.json").toString(Charsets.UTF_8),
        )
            .getJSONArray("sessions")
            .getJSONObject(0)
            .getJSONArray("modesUsed")
        assertEquals("LISTEN", archivedModes.getString(0))
        assertEquals("PLAY_YOURSELF", archivedModes.getString(1))
        assertTrue(renderedText.contains("STUDENT_CONFIDENT"))
        assertFalse(renderedText.contains("content://private"))
        assertFalse(renderedText.contains(recording.absolutePath))
        assertArrayEquals(recordingBytes, entries.getValue("recordings/${recording.name}"))
    }

    @Test
    fun suggestedArchiveNameIsDateSortedAndFilesystemSafe() {
        val fileName = ProfileArchiveWriter.fileName(
            ChildProfile(id = "student", name = "A/B: C", instrument = "Piano"),
            date = DateKey.from(LocalDate.of(2026, 11, 1)),
        )

        assertEquals("LK Practice - A-B- C - data - 2026-11-01.zip", fileName)
    }

    @Test
    fun unavailableRecordingIsSkippedWithoutDamagingTheArchive() {
        val output = ByteArrayOutputStream()

        val result = ProfileArchiveWriter.write(
            output = output,
            profile = ChildProfile(id = "student", name = "Lincoln", instrument = "Violin"),
            snapshot = PracticeSnapshot(),
            recordingFiles = listOf(File("/definitely-not-a-practice-recording.mp4")),
            exportedAtMillis = 1_800_000_000_000,
        )
        val entries = unzip(output.toByteArray())

        assertEquals(0, result.recordingCount)
        assertEquals(1, result.skippedRecordingCount)
        assertTrue("README.txt" in entries)
        assertFalse(entries.keys.any { it.startsWith("recordings/") })
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                put(entry.name, zip.readBytes())
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
