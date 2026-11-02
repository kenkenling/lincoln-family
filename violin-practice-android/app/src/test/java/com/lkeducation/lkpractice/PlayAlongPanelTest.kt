package com.lkeducation.lkpractice

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayAlongPanelTest {
    @Test
    fun orientationSelectsTheExpectedSplitLayout() {
        assertEquals(
            PlayAlongLayout.PORTRAIT,
            playAlongLayoutForOrientation(Configuration.ORIENTATION_PORTRAIT),
        )
        assertEquals(
            PlayAlongLayout.LANDSCAPE,
            playAlongLayoutForOrientation(Configuration.ORIENTATION_LANDSCAPE),
        )
        assertEquals(
            PlayAlongLayout.PORTRAIT,
            playAlongLayoutForOrientation(Configuration.ORIENTATION_UNDEFINED),
        )
    }

    @Test
    fun portraitUsesEqualYouTubeCompatibleMediaHeights() {
        assertEquals(200, PLAY_ALONG_PORTRAIT_MEDIA_HEIGHT_DP)
    }

    @Test
    fun accessibilityCopyDescribesBothVisiblePanelsAndTheSharedAction() {
        assertEquals(
            "Play along: teacher reference on top, your camera below. " +
                "Tap Play and Record to start both.",
            playAlongAccessibilityDescription(PlayAlongLayout.PORTRAIT),
        )
        assertEquals(
            "Play along: teacher reference on the left, your camera on the right. " +
                "Tap Play and Record to start both.",
            playAlongAccessibilityDescription(PlayAlongLayout.LANDSCAPE),
        )
    }

    @Test
    fun playbackControllerStartsAndPausesTheBoundReference() {
        val controller = ReferencePlaybackController()
        val owner = Any()
        var starts = 0
        var pauses = 0

        controller.bind(
            owner = owner,
            playFromStart = { starts += 1 },
            pause = { pauses += 1 },
        )
        controller.playFromStart()
        controller.pause()

        assertEquals(1, starts)
        assertEquals(1, pauses)

        controller.unbind(owner)
        controller.playFromStart()
        controller.pause()

        assertEquals(1, starts)
        assertEquals(1, pauses)
    }

    @Test
    fun playbackStartsWhenTheReferenceFinishesLoadingAfterRecordBegins() {
        val controller = ReferencePlaybackController()
        var starts = 0

        controller.playFromStart()
        controller.bind(
            owner = Any(),
            playFromStart = { starts += 1 },
            pause = {},
        )

        assertEquals(1, starts)
        assertTrue(controller.playbackRequested)
    }

    @Test
    fun staleSurfaceCannotDetachTheCurrentReference() {
        val controller = ReferencePlaybackController()
        val oldOwner = Any()
        val currentOwner = Any()
        var currentStarts = 0

        controller.bind(oldOwner, playFromStart = {}, pause = {})
        controller.bind(
            currentOwner,
            playFromStart = { currentStarts += 1 },
            pause = {},
        )
        controller.unbind(oldOwner)
        controller.playFromStart()

        assertEquals(1, currentStarts)
    }

    @Test
    fun recorderIsBusyDuringCountdownStartupAndRecording() {
        assertFalse(recorderState().isBusy)
        assertTrue(recorderState(countdown = 3).isBusy)
        assertTrue(recorderState(isStarting = true).isBusy)
        assertTrue(recorderState(isRecording = true).isBusy)
    }

    @Test
    fun youtubeErrorsHaveActionableMessages() {
        assertEquals(
            "YouTube rejected the app connection. Please update LK Practice.",
            youtubePlayerErrorMessage(
                com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
                    .PlayerError.REQUEST_MISSING_HTTP_REFERER,
            ),
        )
    }

    private fun recorderState(
        countdown: Int = 0,
        isStarting: Boolean = false,
        isRecording: Boolean = false,
    ): CameraRecorderState = CameraRecorderState(
        cameraPermissionGranted = true,
        audioPermissionGranted = true,
        controller = null,
        controllerError = null,
        isRecording = isRecording,
        isStarting = isStarting,
        countdown = countdown,
        recordingMessage = "",
        recordingFileName = "take.mp4",
        beginCountdown = {},
        stopRecording = {},
        requestCameraAndMicrophone = {},
        requestMicrophone = {},
    )
}
