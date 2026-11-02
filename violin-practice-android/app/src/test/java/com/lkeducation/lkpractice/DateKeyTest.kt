package com.lkeducation.lkpractice

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DateKeyTest {
    @Test
    fun parsesAndFormatsCanonicalIsoDate() {
        val date = DateKey.parse("2026-11-02")

        assertEquals("2026-11-02", date.value)
        assertEquals(LocalDate.of(2026, 11, 2), date.toLocalDate())
        assertEquals(date, DateKey.from(LocalDate.of(2026, 11, 2)))
        assertSame(date.toLocalDate(), date.toLocalDate())
    }

    @Test
    fun serializesAsTheExistingPlainIsoString() {
        val date = DateKey("2026-11-02")

        val encoded = storeJson.encodeToString(date)

        assertEquals("\"2026-11-02\"", encoded)
        assertEquals(date, storeJson.decodeFromString<DateKey>(encoded))
    }

    @Test(expected = DateTimeParseException::class)
    fun rejectsArbitraryStrings() {
        DateKey("not-a-date")
    }
}
