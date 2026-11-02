package com.lkeducation.lkpractice

import java.time.DayOfWeek
import java.time.temporal.ChronoUnit

internal const val MAX_ASSIGNMENT_RANGE_DAYS = 30

internal fun assignmentRangeDayCount(
    startDate: DateKey,
    endDate: DateKey,
): Int = (
    ChronoUnit.DAYS.between(startDate.toLocalDate(), endDate.toLocalDate()) + 1L
    ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun isValidAssignmentDateRange(
    startDate: DateKey,
    endDate: DateKey,
): Boolean {
    val dayCount = assignmentRangeDayCount(startDate, endDate)
    return dayCount in 1..MAX_ASSIGNMENT_RANGE_DAYS
}

internal fun PracticeTask.isScheduledOn(date: DateKey): Boolean =
    date >= startDate && date <= endDate

internal fun PracticeTask.scheduledDates(): List<DateKey> {
    val start = startDate.toLocalDate()
    return (0 until assignmentRangeDayCount(startDate, endDate)).map { offset ->
        DateKey.from(start.plusDays(offset.toLong()))
    }
}

internal fun assignmentDateRangeLabel(
    startDate: DateKey,
    endDate: DateKey,
): String =
    if (startDate == endDate) {
        friendlyFullDate(startDate)
    } else {
        "${friendlyFullDate(startDate)} – ${friendlyFullDate(endDate)}"
    }

internal fun thisWeekAssignmentRange(anchorDate: DateKey): Pair<DateKey, DateKey> {
    val anchor = anchorDate.toLocalDate()
    val monday = anchor.minusDays((anchor.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return DateKey.from(monday) to DateKey.from(monday.plusDays(6))
}

internal fun nextSevenDaysAssignmentRange(anchorDate: DateKey): Pair<DateKey, DateKey> {
    val start = anchorDate.toLocalDate()
    return anchorDate to DateKey.from(start.plusDays(6))
}
