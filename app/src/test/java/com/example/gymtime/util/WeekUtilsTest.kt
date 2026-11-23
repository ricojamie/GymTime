package com.example.gymtime.util

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

/**
 * Tests for WeekUtils - verifies week boundary calculations.
 */
class WeekUtilsTest {

    @Test
    fun `getCurrentWeekStartMs returns a Sunday at midnight`() {
        val weekStartMs = WeekUtils.getCurrentWeekStartMs()
        val cal = Calendar.getInstance()
        cal.timeInMillis = weekStartMs

        assertEquals(Calendar.SUNDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `getLastWeekStartMs is one calendar week before current week start`() {
        val zone = ZoneId.systemDefault()
        val currentWeekStart = Instant.ofEpochMilli(WeekUtils.getCurrentWeekStartMs()).atZone(zone)
        val lastWeekStart = Instant.ofEpochMilli(WeekUtils.getLastWeekStartMs()).atZone(zone)

        assertEquals(DayOfWeek.SUNDAY, currentWeekStart.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, lastWeekStart.dayOfWeek)
        assertEquals(7L, ChronoUnit.DAYS.between(lastWeekStart.toLocalDate(), currentWeekStart.toLocalDate()))
        assertEquals(lastWeekStart.toLocalDate().plusWeeks(1), currentWeekStart.toLocalDate())
    }

    @Test
    fun `getLastWeekStartMs returns a Sunday at midnight`() {
        val lastWeekStartMs = WeekUtils.getLastWeekStartMs()
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastWeekStartMs

        assertEquals(Calendar.SUNDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun `getLastWeekEndMs is one millisecond before current week start`() {
        val currentWeekStart = WeekUtils.getCurrentWeekStartMs()
        val lastWeekEnd = WeekUtils.getLastWeekEndMs()

        assertEquals(currentWeekStart - 1, lastWeekEnd)
    }

    @Test
    fun `getLastWeekEndMs falls on Saturday`() {
        val lastWeekEndMs = WeekUtils.getLastWeekEndMs()
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastWeekEndMs

        assertEquals(Calendar.SATURDAY, cal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `getCurrentTimeMs returns approximately current time`() {
        val before = System.currentTimeMillis()
        val result = WeekUtils.getCurrentTimeMs()
        val after = System.currentTimeMillis()

        assertTrue(result >= before)
        assertTrue(result <= after)
    }

    @Test
    fun `week boundaries are consistent`() {
        // Last week end should be immediately before current week start
        val lastWeekEnd = WeekUtils.getLastWeekEndMs()
        val currentWeekStart = WeekUtils.getCurrentWeekStartMs()
        val lastWeekStart = WeekUtils.getLastWeekStartMs()

        assertTrue(lastWeekStart < lastWeekEnd)
        assertTrue(lastWeekEnd < currentWeekStart)
        assertEquals(1, currentWeekStart - lastWeekEnd)
    }

    @Test
    fun `week starts are previous or same Sunday in Monday-first UK locale`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            val zone = ZoneId.of("Europe/London")
            // Monday 2026-06-08 through Saturday 2026-06-13 must resolve back to Sunday 2026-06-07
            val expectedWeekStart = LocalDate.of(2026, 6, 7).atStartOfDay(zone).toInstant().toEpochMilli()
            val expectedLastWeekStart = LocalDate.of(2026, 5, 31).atStartOfDay(zone).toInstant().toEpochMilli()
            for (dayOffset in 0..6) {
                val date = LocalDate.of(2026, 6, 7).plusDays(dayOffset.toLong())
                val nowMs = date.atTime(15, 30).atZone(zone).toInstant().toEpochMilli()
                val actualCurrentStart = WeekUtils.getCurrentWeekStartMsAt(nowMs, zone)
                val actualLastStart = WeekUtils.getLastWeekStartMsAt(nowMs, zone)
                assertTrue("Week start must not be in the future on $date", actualCurrentStart <= nowMs)
                assertEquals("Current week start mismatch on $date", expectedWeekStart, actualCurrentStart)
                assertEquals("Last week start mismatch on $date", expectedLastWeekStart, actualLastStart)
            }
            // Also verify zero-arg WeekUtils methods under UK locale do not return a future Sunday
            val now = System.currentTimeMillis()
            val currentStart = WeekUtils.getCurrentWeekStartMs()
            assertTrue(currentStart <= now)
            assertEquals(
                DayOfWeek.SUNDAY,
                Instant.ofEpochMilli(currentStart).atZone(ZoneId.systemDefault()).dayOfWeek
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `handles DST spring-forward and fall-back weeks at exact Sunday local midnight`() {
        val ny = ZoneId.of("America/New_York")

        // Spring forward week: Sunday 2026-03-08 (23h day) -> Sunday 2026-03-15 (167h week)
        val springRefMs = ZonedDateTime.of(2026, 3, 18, 12, 0, 0, 0, ny).toInstant().toEpochMilli()
        val springCurrentStart = WeekUtils.getCurrentWeekStartMsAt(springRefMs, ny)
        val springLastStart = WeekUtils.getLastWeekStartMsAt(springRefMs, ny)
        assertEquals(
            LocalDate.of(2026, 3, 15).atStartOfDay(ny).toInstant().toEpochMilli(),
            springCurrentStart
        )
        assertEquals(
            LocalDate.of(2026, 3, 8).atStartOfDay(ny).toInstant().toEpochMilli(),
            springLastStart
        )
        assertEquals(167L * 60L * 60L * 1000L, springCurrentStart - springLastStart)

        // Fall back week: Sunday 2026-11-01 (25h day) -> Sunday 2026-11-08 (169h week)
        val fallRefMs = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, ny).toInstant().toEpochMilli()
        val fallCurrentStart = WeekUtils.getCurrentWeekStartMsAt(fallRefMs, ny)
        val fallLastStart = WeekUtils.getLastWeekStartMsAt(fallRefMs, ny)
        assertEquals(
            LocalDate.of(2026, 11, 8).atStartOfDay(ny).toInstant().toEpochMilli(),
            fallCurrentStart
        )
        assertEquals(
            LocalDate.of(2026, 11, 1).atStartOfDay(ny).toInstant().toEpochMilli(),
            fallLastStart
        )
        assertEquals(169L * 60L * 60L * 1000L, fallCurrentStart - fallLastStart)
    }
}
