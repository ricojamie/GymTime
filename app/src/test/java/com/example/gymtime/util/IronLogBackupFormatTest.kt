package com.example.gymtime.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class IronLogBackupFormatTest {

    @Test
    fun `manifest round trip preserves all fields and counts`() {
        val manifest = IronLogBackupFormat.BackupManifest(
            formatVersion = 2,
            dbSchemaVersion = 15,
            appVersion = "1.11.3",
            source = "pwa",
            exportedAtEpochMs = 1_732_620_000_000L,
            weightUnit = "lbs",
            counts = IronLogBackupFormat.BackupTableCounts(
                muscleGroups = 9,
                exercises = 30,
                routines = 2,
                routineDays = 6,
                routineExercises = 24,
                workouts = 100,
                sets = 1400,
                workoutExerciseInstances = 400
            )
        )

        val encoded = IronLogBackupFormat.encodeManifest(manifest)
        val decoded = IronLogBackupFormat.decodeManifest(encoded)
        assertEquals(manifest, decoded)
    }

    @Test
    fun `preferences codec sanitizes custom font invalid hex and out-of-range values`() {
        val json = """
            {
              "userName": "  Alex \"Iron\"  ",
              "themeColor": "custom",
              "customThemeColor": "not-a-hex",
              "themeFont": "custom",
              "darkMode": false,
              "restDaysPerWeek": 99,
              "bestStreak": -5,
              "barWeight": 45,
              "loadingSides": 0,
              "availablePlates": [25, 45, 25, -5, 2.5],
              "usePlateInventory": true,
              "plateInventory": { "45": 4, "10": 250, "bad": 2 },
              "preferredWeightUnit": "kg"
            }
        """.trimIndent()

        val decoded = IronLogBackupFormat.decodePreferences(json)
        assertEquals("Alex \"Iron\"", decoded.userName)
        assertEquals("lime", decoded.themeColor)
        assertNull(decoded.customThemeColor)
        assertEquals("bebas_neue", decoded.themeFont)
        assertEquals(7, decoded.restDaysPerWeek)
        assertEquals(0, decoded.bestStreak)
        assertEquals(1, decoded.loadingSides)
        assertEquals(listOf(45f, 25f, 2.5f), decoded.availablePlates)
        assertEquals(mapOf(45f to 4, 10f to 100), decoded.plateInventory)
        assertEquals("kg", decoded.preferredWeightUnit)

        val reEncoded = IronLogBackupFormat.encodePreferences(decoded)
        assertEquals(decoded, IronLogBackupFormat.decodePreferences(reEncoded))
    }

    @Test
    fun `csv parser rejects trailing non-whitespace characters after closing quote`() {
        val malformed = BufferedReader(StringReader("a,b\n\"foo\"bar,baz\n"))
        val error = runCatching {
            CsvParser.forEachRecord(malformed) { _, _ -> }
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `csv parser allows trailing spaces after closing quote and strips UTF-8 BOM`() {
        val valid = BufferedReader(StringReader("\uFEFFid,name\n1,\"Bench, \"\"Flat\"\"\"  \n"))
        val rows = mutableListOf<List<String>>()
        CsvParser.forEachRecord(valid) { _, fields -> rows.add(fields) }
        assertEquals(2, rows.size)
        assertEquals(listOf("id", "name"), rows[0])
        assertEquals(listOf("1", "Bench, \"Flat\""), rows[1])
    }
}
