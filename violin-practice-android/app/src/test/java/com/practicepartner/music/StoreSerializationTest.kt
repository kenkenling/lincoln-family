package com.practicepartner.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StoreSerializationTest {
    private val profile = ChildProfile(
        id = "child-1",
        name = "Student",
        instrument = "Violin",
    )

    @Test
    fun snapshotCodecMatchesVersionSevenFormatAndRoundTrips() {
        val expectedJson = storeJson.parseToJsonElement(PREVIOUS_SNAPSHOT_JSON)
        val decoded = PracticeSnapshotJsonCodec.decode(PREVIOUS_SNAPSHOT_JSON, profile)

        assertNotNull(decoded)
        assertEquals(0, decoded?.skippedRecords)
        assertEquals(expectedJson, storeJson.parseToJsonElement(
            PracticeSnapshotJsonCodec.encode(decoded!!.snapshot),
        ))
        assertEquals("task-1", decoded.snapshot.tasks.single().id)
        assertEquals(300, decoded.snapshot.tasks.single().targetSeconds)
        assertEquals(120L, decoded.snapshot.sessions.single().elapsedSeconds)
    }

    @Test
    fun snapshotCodecIgnoresUnknownFieldsAndSkipsOnlyDamagedRows() {
        val withFutureAndDamagedFields = PREVIOUS_SNAPSHOT_JSON
            .replace(
                "\"tasks\": [",
                "\"futureRootField\":{\"value\":1},\"tasks\": [{\"id\":\"damaged\"},",
            )
            .replace(
                "\"title\": \"Boreas\"",
                "\"title\": \"Boreas\",\"futureTaskField\":true",
            )

        val decoded = PracticeSnapshotJsonCodec.decode(withFutureAndDamagedFields, profile)

        assertNotNull(decoded)
        assertEquals(1, decoded?.skippedRecords)
        assertEquals(listOf("task-1"), decoded?.snapshot?.tasks?.map(PracticeTask::id))
    }

    @Test
    fun olderSnapshotWithoutFamilyOrModeFieldsStillMigrates() {
        val decoded = PracticeSnapshotJsonCodec.decode(OLDER_SNAPSHOT_JSON, profile)

        assertNotNull(decoded)
        val task = decoded!!.snapshot.tasks.single()
        assertEquals(profile.id, task.childProfileId)
        assertEquals(profile.instrument, task.instrument)
        assertEquals(PracticeMode.entries.toSet(), task.enabledModes)
        assertEquals(emptyList<PracticeSession>(), decoded.snapshot.sessions)
    }

    @Test
    fun householdCodecMatchesVersionOneFormatAndRoundTrips() {
        val expectedJson = storeJson.parseToJsonElement(PREVIOUS_HOUSEHOLD_JSON)
        val decoded = HouseholdJsonCodec.decode(
            PREVIOUS_HOUSEHOLD_JSON.replace(
                "\"childProfiles\"",
                "\"futureHouseholdField\":true,\"childProfiles\"",
            ),
        )

        assertNotNull(decoded)
        assertEquals("child-1", decoded?.activeChildProfileId)
        assertEquals(expectedJson, storeJson.parseToJsonElement(
            HouseholdJsonCodec.encode(decoded!!),
        ))
    }
}

private val PREVIOUS_SNAPSHOT_JSON = """
    {
      "version": 7,
      "tasks": [
        {
          "id": "task-1",
          "childProfileId": "child-1",
          "instrument": "Violin",
          "assignedDate": "2026-11-01",
          "title": "Boreas",
          "source": "https://youtu.be/VZIWCt-jW6g",
          "sourceType": "YOUTUBE",
          "startSeconds": 5,
          "endSeconds": 25,
          "repetitions": 4,
          "coachNote": "Use a slow bow.",
          "whyImportant": "Build an even sound.",
          "sheetMusicUri": "content://sheet",
          "assignmentType": "REPERTOIRE",
          "goalType": "TIME",
          "targetSeconds": 300,
          "enabledModes": ["LISTEN", "PLAY_YOURSELF"],
          "revision": 3,
          "archivedAtMillis": null
        }
      ],
      "progress": [
        {
          "date": "2026-11-01",
          "childProfileId": "child-1",
          "completedTaskIds": ["task-1"],
          "repetitionsByTask": {"task-1": 4},
          "elapsedSeconds": 300
        }
      ],
      "events": [
        {
          "id": "event-1",
          "date": "2026-11-01",
          "childProfileId": "child-1",
          "sessionId": "session-1",
          "taskId": "task-1",
          "taskTitle": "Boreas",
          "event": "Practice completed",
          "atMillis": 1000,
          "videoUri": "content://video",
          "videoPath": "/practice/video.mp4",
          "videoFileName": "video.mp4"
        }
      ],
      "sessions": [
        {
          "id": "session-1",
          "childProfileId": "child-1",
          "assignmentId": "task-1",
          "assignmentTitle": "Boreas",
          "instrument": "Violin",
          "date": "2026-11-01",
          "mode": "PLAY_YOURSELF",
          "startedAtMillis": 100,
          "endedAtMillis": 220,
          "repetitionsCompleted": 4,
          "status": "COMPLETED",
          "assignmentRevision": 3,
          "assignmentType": "REPERTOIRE",
          "goalType": "TIME",
          "targetRepetitions": 4,
          "targetSeconds": 300,
          "elapsedSeconds": 120,
          "reflection": "PROUD"
        }
      ]
    }
""".trimIndent()

private val PREVIOUS_HOUSEHOLD_JSON = """
    {
      "version": 1,
      "id": "household-1",
      "name": "Our Family",
      "activeChildProfileId": "child-1",
      "childProfiles": [
        {
          "id": "child-1",
          "name": "Student",
          "instrument": "Violin",
          "excelWorkbookUri": null,
          "excelWorkbookName": null
        }
      ]
    }
""".trimIndent()

private val OLDER_SNAPSHOT_JSON = """
    {
      "version": 3,
      "tasks": [
        {
          "id": "older-task",
          "assignedDate": "2026-10-01",
          "title": "Older assignment",
          "source": "https://youtu.be/VZIWCt-jW6g",
          "sourceType": "YOUTUBE",
          "startSeconds": 0,
          "endSeconds": null,
          "repetitions": 3,
          "coachNote": "",
          "whyImportant": "",
          "sheetMusicUri": null
        }
      ],
      "progress": [
        {
          "date": "2026-10-01",
          "completedTaskIds": [],
          "repetitionsByTask": {"older-task": 1},
          "elapsedSeconds": 60
        }
      ],
      "events": [
        {
          "id": "older-event",
          "date": "2026-10-01",
          "taskId": "older-task",
          "taskTitle": "Older assignment",
          "event": "Practiced",
          "atMillis": 1000,
          "videoUri": null,
          "videoPath": null
        }
      ]
    }
""".trimIndent()
