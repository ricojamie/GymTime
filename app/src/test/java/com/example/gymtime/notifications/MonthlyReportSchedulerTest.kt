package com.example.gymtime.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MonthlyReportSchedulerTest {

    @After
    fun tearDown() {
        unmockkStatic(WorkManager::class)
    }

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

    @Test
    fun `early wakeup successor targets next month instead of same day 09 00`() {
        val zone = ZoneId.of("America/New_York")
        val earlyWakeMs = ZonedDateTime.of(2026, 5, 1, 8, 59, 58, 0, zone).toInstant().toEpochMilli()
        val expectedJuneMs = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        val targetMs = MonthlyReportScheduler.computeTargetTriggerMs(
            nowMs = earlyWakeMs,
            lastHandledPeriod = YearMonth.of(2026, 4),
            zoneId = zone,
            afterWorkerRun = true
        )

        assertEquals(expectedJuneMs, targetMs)
    }

    @Test
    fun `westward timezone travel after handling period does not re-fire same month`() {
        val zone = ZoneId.of("America/New_York")
        val april30NightMs = ZonedDateTime.of(2026, 4, 30, 23, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expectedJuneMs = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        val targetMs = MonthlyReportScheduler.computeTargetTriggerMs(
            nowMs = april30NightMs,
            lastHandledPeriod = YearMonth.of(2026, 4),
            zoneId = zone
        )

        assertEquals(expectedJuneMs, targetMs)
    }

    @Test
    fun `handled april period still allows may period target in june`() {
        val zone = ZoneId.of("America/New_York")
        val june1MorningMs = ZonedDateTime.of(2026, 6, 1, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expectedJuneMs = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        val targetMs = MonthlyReportScheduler.computeTargetTriggerMs(
            nowMs = june1MorningMs,
            lastHandledPeriod = YearMonth.of(2026, 4),
            zoneId = zone
        )

        assertEquals(expectedJuneMs, targetMs)
    }

    @Test
    fun `reportPeriodFor resolves preceding calendar month`() {
        val zone = ZoneId.of("UTC")
        val may1Ms = ZonedDateTime.of(2026, 5, 1, 0, 5, 0, 0, zone).toInstant().toEpochMilli()
        val jan1Ms = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(YearMonth.of(2026, 4), MonthlyReportScheduler.reportPeriodFor(may1Ms, zone))
        assertEquals(YearMonth.of(2025, 12), MonthlyReportScheduler.reportPeriodFor(jan1Ms, zone))
    }

    @Test
    fun `shouldRefreshSchedule preserves overdue unhandled same-period run and refreshes drifted schedule`() {
        val zone = ZoneId.of("UTC")
        val may1At0900 = ZonedDateTime.of(2026, 5, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val may1At0905 = ZonedDateTime.of(2026, 5, 1, 9, 5, 0, 0, zone).toInstant().toEpochMilli()
        val june1At0900 = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val april20At1200 = ZonedDateTime.of(2026, 4, 20, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val may1At1400 = ZonedDateTime.of(2026, 5, 1, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val epoch1970 = ZonedDateTime.of(1970, 2, 1, 9, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertFalse(
            MonthlyReportScheduler.shouldRefreshSchedule(
                storedTargetMs = null,
                desiredTargetMs = june1At0900,
                nowMs = may1At0905,
                zoneId = zone
            )
        )
        assertFalse(
            MonthlyReportScheduler.shouldRefreshSchedule(
                storedTargetMs = may1At0900,
                desiredTargetMs = june1At0900,
                nowMs = may1At0905,
                lastHandledPeriod = YearMonth.of(2026, 3),
                zoneId = zone
            )
        )
        assertTrue(
            MonthlyReportScheduler.shouldRefreshSchedule(
                storedTargetMs = may1At0900,
                desiredTargetMs = june1At0900,
                nowMs = may1At0905,
                lastHandledPeriod = YearMonth.of(2026, 4),
                zoneId = zone
            )
        )
        assertTrue(
            MonthlyReportScheduler.shouldRefreshSchedule(
                storedTargetMs = epoch1970,
                desiredTargetMs = june1At0900,
                nowMs = may1At0905,
                zoneId = zone
            )
        )
        assertTrue(
            MonthlyReportScheduler.shouldRefreshSchedule(
                storedTargetMs = may1At1400,
                desiredTargetMs = may1At0900,
                nowMs = april20At1200,
                zoneId = zone
            )
        )
        assertFalse(
            MonthlyReportScheduler.shouldRefreshSchedule(
                storedTargetMs = may1At0900,
                desiredTargetMs = may1At0900 + TimeUnit.MINUTES.toMillis(5),
                nowMs = april20At1200,
                zoneId = zone
            )
        )
    }

    @Test
    fun `scheduleSuccessor uses APPEND_OR_REPLACE and dedups marked period`() {
        val prefs = InMemorySharedPreferences()
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs

        val workManager = mockk<WorkManager>(relaxed = true)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(context) } returns workManager
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } returns mockk(relaxed = true)

        val scheduler = MonthlyReportScheduler(context)
        val april = YearMonth.of(2026, 4)

        assertFalse(scheduler.hasHandledPeriod(april))
        scheduler.markPeriodHandled(april)
        assertTrue(scheduler.hasHandledPeriod(april))
        assertTrue(scheduler.hasHandledPeriod(YearMonth.of(2026, 3)))
        assertFalse(scheduler.hasHandledPeriod(YearMonth.of(2026, 5)))

        scheduler.scheduleSuccessor()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                MonthlyReportScheduler.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
