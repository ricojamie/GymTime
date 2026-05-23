package com.example.gymtime.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MonthlyReportSchedulerTest {

    @Test
    fun `computeInitialDelay targets next 1st of month at 09 00`() {
        // 2026-05-19 14:00:00 local — next fire should be 2026-06-01 09:00.
        val cal = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(2026, Calendar.MAY, 19, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val delay = MonthlyReportScheduler.computeInitialDelayMs(now)

        val expected = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(2026, Calendar.JUNE, 1, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis - now

        assertEquals(expected, delay)
    }

    @Test
    fun `computeInitialDelay on 1st of month after 09 00 rolls to next month`() {
        // 2026-05-01 10:00 — already past 09:00 today, so next fire is 2026-06-01.
        val cal = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(2026, Calendar.MAY, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val delay = MonthlyReportScheduler.computeInitialDelayMs(now)

        // Should be at least ~30 days out, not negative or zero.
        assertTrue("delay must be > 27d", delay > TimeUnit.DAYS.toMillis(27))
    }

    @Test
    fun `computeInitialDelay on 1st of month before 09 00 fires today`() {
        // 2026-05-01 07:30 — same-day fire at 09:00.
        val cal = Calendar.getInstance().apply {
            timeZone = TimeZone.getDefault()
            set(2026, Calendar.MAY, 1, 7, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val delay = MonthlyReportScheduler.computeInitialDelayMs(now)

        assertEquals(TimeUnit.MINUTES.toMillis(90), delay)
    }
}
