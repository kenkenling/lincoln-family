package com.practicepartner.music

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AccessibilityTest {
    @Test
    fun taskCardDescriptionIncludesTitleProgressAndCompletion() {
        assertEquals(
            "Boreas. 2 of 4 repetitions. Not complete yet.",
            taskCardAccessibilityDescription(
                title = "Boreas",
                progress = "2 / 4 repetitions",
                completed = false,
            ),
        )
        assertEquals(
            "Shifting. 10:00 of 10:00. Complete. Archived from Today.",
            taskCardAccessibilityDescription(
                title = "Shifting",
                progress = "10:00 / 10:00",
                completed = true,
                archived = true,
            ),
        )
    }

    @Test
    fun weeklyDescriptionAnnouncesOneSummaryInsteadOfSevenDots() {
        assertEquals(
            "3 of 7 practice days this week.",
            weeklyPracticeAccessibilityDescription(practicedDays = 3, totalDays = 7),
        )
        assertEquals(
            "0 of 7 practice days this week.",
            weeklyPracticeAccessibilityDescription(practicedDays = -2, totalDays = 7),
        )
    }

    @Test
    fun disabledSystemMotionSkipsTheVisualCountdown() {
        assertTrue(shouldSkipRecordingCountdown(0f))
        assertTrue(shouldSkipRecordingCountdown(-1f))
        assertFalse(shouldSkipRecordingCountdown(0.5f))
        assertFalse(shouldSkipRecordingCountdown(1f))
        assertFalse(shouldSkipRecordingCountdown(null))
    }

    @Test
    fun normalTextPalettePairsMeetWcagAaContrast() {
        assertAa("Muted on CloudWhite", Muted, CloudWhite)
        assertAa("Muted on SkyTop", Muted, SkyTop)
        assertAa("Muted on CloudBlue", Muted, CloudBlue)
        assertAa("Paper on DeepSky", Paper, DeepSky)
        assertAa("Paper on SkyBlue", Paper, SkyBlue)
        assertAa("DeepSky on SkyTop", DeepSky, SkyTop)
        assertAa("Forest on Sage", Forest, Sage)
        assertAa("Danger on CloudWhite", Danger, CloudWhite)
        assertAa("Ink on SunYellow", Ink, SunYellow)
        assertNonTextContrast("SkyOutline on CloudBlue", SkyOutline, CloudBlue)
        assertNonTextContrast("SkyOutline on CloudWhite", SkyOutline, CloudWhite)
    }

    private fun assertAa(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast was $ratio, expected at least 4.5", ratio >= 4.5)
    }

    private fun assertNonTextContrast(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast was $ratio, expected at least 3.0", ratio >= 3.0)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearized(color.red) +
            0.7152 * linearized(color.green) +
            0.0722 * linearized(color.blue)

    private fun linearized(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }
}
