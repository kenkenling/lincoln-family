package com.lkeducation.lkpractice

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

internal fun Modifier.accessibilityHeading(): Modifier =
    semantics { heading() }

internal fun Modifier.mergeForAccessibility(): Modifier =
    semantics(mergeDescendants = true) {}

internal fun Modifier.hideFromAccessibility(): Modifier =
    clearAndSetSemantics {}

internal fun Modifier.politeLiveRegion(): Modifier =
    semantics { liveRegion = LiveRegionMode.Polite }

internal fun Modifier.politeMergedStatus(): Modifier =
    semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
    }

internal fun Modifier.assertiveLiveRegion(): Modifier =
    semantics { liveRegion = LiveRegionMode.Assertive }

internal fun taskCardAccessibilityDescription(
    title: String,
    progress: String,
    completed: Boolean,
    archived: Boolean = false,
): String {
    val spokenProgress = progress.replace(" / ", " of ")
    return buildList {
        add(title)
        if (spokenProgress.isNotBlank()) add(spokenProgress)
        add(if (completed) "Complete" else "Not complete yet")
        if (archived) add("Archived from Today")
    }.joinToString(separator = ". ", postfix = ".")
}

internal fun weeklyPracticeAccessibilityDescription(
    practicedDays: Int,
    totalDays: Int,
): String {
    val safeTotal = totalDays.coerceAtLeast(0)
    val safePracticed = practicedDays.coerceIn(0, safeTotal)
    return "$safePracticed of $safeTotal practice days this week."
}

internal fun shouldSkipRecordingCountdown(motionDurationScale: Float?): Boolean =
    motionDurationScale != null && motionDurationScale <= 0f
