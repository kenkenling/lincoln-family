package com.practicepartner.music

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonExtensionsTest {
    @Test
    fun nullableStringHandlesMissingNullBlankAndPresentValues() {
        val json = JSONObject()
            .put("nullValue", JSONObject.NULL)
            .put("blankValue", "")
            .put("presentValue", "Violin")

        assertNull(json.optNullableString("missingValue"))
        assertNull(json.optNullableString("nullValue"))
        assertNull(json.optNullableString("blankValue"))
        assertEquals("Violin", json.optNullableString("presentValue"))
    }

    @Test
    fun nullableNumbersPreserveZeroAndReturnNullWhenAbsent() {
        val json = JSONObject()
            .put("nullValue", JSONObject.NULL)
            .put("intValue", 0)
            .put("longValue", 42L)

        assertNull(json.optIntOrNull("missingValue"))
        assertNull(json.optIntOrNull("nullValue"))
        assertEquals(0, json.optIntOrNull("intValue"))
        assertNull(json.optLongOrNull("missingValue"))
        assertNull(json.optLongOrNull("nullValue"))
        assertEquals(42L, json.optLongOrNull("longValue"))
    }
}
