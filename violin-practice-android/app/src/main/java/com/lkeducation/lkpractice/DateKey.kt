package com.lkeducation.lkpractice

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * A validated ISO-8601 calendar date used for all internal date identity and comparisons.
 *
 * The serialized value remains a plain `YYYY-MM-DD` string for compatibility with existing
 * snapshots, CSV files, Excel workbooks, and profile archives.
 */
@Serializable
@JvmInline
value class DateKey(val value: String) : Comparable<DateKey> {
    init {
        parsedDate(value)
    }

    fun toLocalDate(): LocalDate = parsedDate(value)

    override fun compareTo(other: DateKey): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        fun from(date: LocalDate): DateKey {
            val value = date.toString()
            parsedDates.putIfAbsent(value, date)
            return DateKey(value)
        }

        fun parse(value: String): DateKey =
            from(parsedDate(value.trim()))

        fun today(): DateKey = from(LocalDate.now())
    }
}

private val parsedDates = ConcurrentHashMap<String, LocalDate>()

private fun parsedDate(value: String): LocalDate =
    parsedDates.computeIfAbsent(value, LocalDate::parse)
