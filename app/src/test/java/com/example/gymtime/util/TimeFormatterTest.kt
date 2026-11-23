package com.example.gymtime.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class TimeFormatterTest {

    @Test
    fun `formatShortDate formats dates using MMM d without leading zero on single-digit days`() {
        val may19 = Date.from(LocalDate.of(2026, 5, 19).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())
        val jan5 = Date.from(LocalDate.of(2026, 1, 5).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())
        assertEquals("May 19", TimeFormatter.formatShortDate(may19, Locale.US))
        assertEquals("May 19", TimeFormatter.formatShortDate(may19, Locale.UK))
        assertEquals("Jan 5", TimeFormatter.formatShortDate(jan5, Locale.US))
    }

    @Test
    fun `formatSecondsToMMSS handles positive zero and negative inputs`() {
        assertEquals("0:00", TimeFormatter.formatSecondsToMMSS(0))
        assertEquals("0:00", TimeFormatter.formatSecondsToMMSS(-12))
        assertEquals("0:45", TimeFormatter.formatSecondsToMMSS(45))
        assertEquals("1:30", TimeFormatter.formatSecondsToMMSS(90))
        assertEquals("10:05", TimeFormatter.formatSecondsToMMSS(605))
    }
}
