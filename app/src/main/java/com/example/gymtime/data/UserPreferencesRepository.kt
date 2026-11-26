package com.example.gymtime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gymtime.util.IronLogBackupFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val USER_NAME = stringPreferencesKey("user_name")
        val EXERCISES_SEEDED = booleanPreferencesKey("exercises_seeded")
        val ACTIVE_ROUTINE_ID = longPreferencesKey("active_routine_id")

        // Theme and UI preferences
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val CUSTOM_THEME_COLOR = stringPreferencesKey("custom_theme_color")
        val THEME_FONT = stringPreferencesKey("theme_font")
        val CUSTOM_FONT_URI = stringPreferencesKey("custom_font_uri")
        val TIMER_AUTO_START = booleanPreferencesKey("timer_auto_start")
        val TIMER_AUDIO_ENABLED = booleanPreferencesKey("timer_audio_enabled")
        val TIMER_VIBRATE_ENABLED = booleanPreferencesKey("timer_vibrate_enabled")

        // Plate calculator preferences
        val AVAILABLE_PLATES = stringPreferencesKey("available_plates")
        val PLATE_INVENTORY = stringPreferencesKey("plate_inventory")
        val USE_PLATE_INVENTORY = booleanPreferencesKey("use_plate_inventory")
        val BAR_WEIGHT = floatPreferencesKey("bar_weight")
        val LOADING_SIDES = intPreferencesKey("loading_sides")

        // Display preferences
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DARK_MODE = booleanPreferencesKey("dark_mode")

        // Streak tracking
        val BEST_STREAK = intPreferencesKey("best_streak")
        val REST_DAYS_PER_WEEK = intPreferencesKey("rest_days_per_week")

        // Monthly report
        val MONTHLY_REPORT_ENABLED = booleanPreferencesKey("monthly_report_enabled")
    }

    private companion object {
        const val DEFAULT_PLATES_JSON = "[45.0, 35.0, 25.0, 15.0, 10.0, 5.0, 2.5]"
    }

    private val preferencesFlow: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    private inline fun <T> prefFlow(crossinline mapper: (Preferences) -> T): Flow<T> =
        preferencesFlow.map { mapper(it) }.distinctUntilChanged()

    private fun parseAvailablePlates(json: String?): List<Float> =
        (json ?: DEFAULT_PLATES_JSON)
            .removeSurrounding("[", "]")
            .split(",")
            .mapNotNull { it.trim().toFloatOrNull()?.takeIf(Float::isFinite) }

    val userName: Flow<String> = prefFlow { it[PreferencesKeys.USER_NAME] ?: "Athlete" }

    val exercisesSeeded: Flow<Boolean> = prefFlow { it[PreferencesKeys.EXERCISES_SEEDED] ?: false }

    val activeRoutineId: Flow<Long?> = prefFlow { it[PreferencesKeys.ACTIVE_ROUTINE_ID] }

    val themeColor: Flow<String> = prefFlow { it[PreferencesKeys.THEME_COLOR] ?: "lime" }

    val customThemeColor: Flow<String?> = prefFlow { it[PreferencesKeys.CUSTOM_THEME_COLOR] }

    val themeFont: Flow<String> = prefFlow { it[PreferencesKeys.THEME_FONT] ?: "bebas_neue" }

    val customFontUri: Flow<String?> = prefFlow { it[PreferencesKeys.CUSTOM_FONT_URI] }

    val timerAutoStart: Flow<Boolean> = prefFlow { it[PreferencesKeys.TIMER_AUTO_START] ?: true }

    val timerAudioEnabled: Flow<Boolean> = prefFlow { it[PreferencesKeys.TIMER_AUDIO_ENABLED] ?: true }

    val timerVibrateEnabled: Flow<Boolean> = prefFlow { it[PreferencesKeys.TIMER_VIBRATE_ENABLED] ?: true }

    val availablePlates: Flow<List<Float>> = prefFlow { parseAvailablePlates(it[PreferencesKeys.AVAILABLE_PLATES]) }

    val barWeight: Flow<Float> = prefFlow { it[PreferencesKeys.BAR_WEIGHT] ?: 45f }

    /** Counts and toggle come from one emission so calculators never combine stale stock and mode. */
    val plateInventorySettings: Flow<PlateInventorySettings> = prefFlow { preferences ->
        PlateInventorySettings(
            counts = PlateInventoryPreferences.decode(preferences[PreferencesKeys.PLATE_INVENTORY]),
            enabled = preferences[PreferencesKeys.USE_PLATE_INVENTORY] ?: false
        )
    }

    val plateInventory: Flow<Map<Float, Int>> = plateInventorySettings.map { it.counts }.distinctUntilChanged()

    val usePlateInventory: Flow<Boolean> = plateInventorySettings.map { it.enabled }.distinctUntilChanged()

    val loadingSides: Flow<Int> = prefFlow { it[PreferencesKeys.LOADING_SIDES] ?: 2 }

    val keepScreenOn: Flow<Boolean> = prefFlow { it[PreferencesKeys.KEEP_SCREEN_ON] ?: false }

    val darkMode: Flow<Boolean> = prefFlow { it[PreferencesKeys.DARK_MODE] ?: true }

    val bestStreak: Flow<Int> = prefFlow { it[PreferencesKeys.BEST_STREAK] ?: 0 }

    val restDaysPerWeek: Flow<Int> = prefFlow { it[PreferencesKeys.REST_DAYS_PER_WEEK] ?: 2 }

    val monthlyReportEnabled: Flow<Boolean> = prefFlow { it[PreferencesKeys.MONTHLY_REPORT_ENABLED] ?: true }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
        }
    }

    suspend fun setExercisesSeeded(seeded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXERCISES_SEEDED] = seeded
        }
    }

    suspend fun setActiveRoutineId(routineId: Long?) {
        context.dataStore.edit { preferences ->
            if (routineId == null) {
                preferences.remove(PreferencesKeys.ACTIVE_ROUTINE_ID)
            } else {
                preferences[PreferencesKeys.ACTIVE_ROUTINE_ID] = routineId
            }
        }
    }

    suspend fun setThemeColor(color: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_COLOR] = color
        }
    }

    suspend fun setCustomThemeColor(colorHex: String?) {
        context.dataStore.edit { preferences ->
            if (colorHex.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.CUSTOM_THEME_COLOR)
            } else {
                preferences[PreferencesKeys.CUSTOM_THEME_COLOR] = colorHex
            }
        }
    }

    suspend fun setThemeFont(font: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_FONT] = font
        }
    }

    suspend fun setCustomFontUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.CUSTOM_FONT_URI)
            } else {
                preferences[PreferencesKeys.CUSTOM_FONT_URI] = uri
            }
        }
    }

    suspend fun setTimerAutoStart(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIMER_AUTO_START] = enabled
        }
    }

    suspend fun setTimerAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIMER_AUDIO_ENABLED] = enabled
        }
    }

    suspend fun setTimerVibrateEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIMER_VIBRATE_ENABLED] = enabled
        }
    }

    suspend fun setAvailablePlates(plates: List<Float>) {
        context.dataStore.edit { preferences ->
            val json = plates.joinToString(",", "[", "]")
            preferences[PreferencesKeys.AVAILABLE_PLATES] = json
        }
    }

    suspend fun setBarWeight(weight: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BAR_WEIGHT] = weight
        }
    }

    suspend fun setPlateInventoryCount(weight: Float, count: Int) {
        context.dataStore.edit { preferences ->
            PlateInventoryPreferences.updateCount(preferences[PreferencesKeys.PLATE_INVENTORY], weight, count)
                ?.let { preferences[PreferencesKeys.PLATE_INVENTORY] = it }
        }
    }

    /** Atomic increment/decrement also keeps rapid taps on the same denomination. */
    suspend fun adjustPlateInventoryCount(weight: Float, delta: Int) {
        context.dataStore.edit { preferences ->
            PlateInventoryPreferences.adjustCount(preferences[PreferencesKeys.PLATE_INVENTORY], weight, delta)
                ?.let { preferences[PreferencesKeys.PLATE_INVENTORY] = it }
        }
    }

    suspend fun setUsePlateInventory(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_PLATE_INVENTORY] = enabled
        }
    }

    suspend fun setLoadingSides(sides: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOADING_SIDES] = sides
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = enabled
        }
    }

    suspend fun togglePlate(plate: Float) {
        context.dataStore.edit { preferences ->
            val currentPlates = parseAvailablePlates(preferences[PreferencesKeys.AVAILABLE_PLATES])
            val newPlates = if (currentPlates.contains(plate)) {
                currentPlates - plate
            } else {
                (currentPlates + plate).sortedDescending()
            }
            preferences[PreferencesKeys.AVAILABLE_PLATES] = newPlates.joinToString(",", "[", "]")
        }
    }

    suspend fun updateBestStreakIfNeeded(currentStreak: Int) {
        context.dataStore.edit { preferences ->
            val currentBest = preferences[PreferencesKeys.BEST_STREAK] ?: 0
            if (currentStreak > currentBest) {
                preferences[PreferencesKeys.BEST_STREAK] = currentStreak
            }
        }
    }

    suspend fun setRestDaysPerWeek(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REST_DAYS_PER_WEEK] = days.coerceIn(0, 7)
        }
    }

    suspend fun setMonthlyReportEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MONTHLY_REPORT_ENABLED] = enabled
        }
    }

    suspend fun getRestDaysPerWeekSync(): Int =
        (preferencesFlow.first()[PreferencesKeys.REST_DAYS_PER_WEEK] ?: 2).coerceIn(0, 7)

    suspend fun exportPortablePreferences(): IronLogBackupFormat.BackupPreferences {
        val prefs = preferencesFlow.first()
        return IronLogBackupFormat.sanitizePreferences(
            IronLogBackupFormat.BackupPreferences(
                userName = prefs[PreferencesKeys.USER_NAME] ?: "Athlete",
                themeColor = prefs[PreferencesKeys.THEME_COLOR] ?: "lime",
                customThemeColor = prefs[PreferencesKeys.CUSTOM_THEME_COLOR],
                themeFont = prefs[PreferencesKeys.THEME_FONT] ?: "bebas_neue",
                darkMode = prefs[PreferencesKeys.DARK_MODE] ?: true,
                timerAutoStart = prefs[PreferencesKeys.TIMER_AUTO_START] ?: true,
                timerAudioEnabled = prefs[PreferencesKeys.TIMER_AUDIO_ENABLED] ?: true,
                timerVibrateEnabled = prefs[PreferencesKeys.TIMER_VIBRATE_ENABLED] ?: true,
                keepScreenOn = prefs[PreferencesKeys.KEEP_SCREEN_ON] ?: false,
                restDaysPerWeek = prefs[PreferencesKeys.REST_DAYS_PER_WEEK] ?: 2,
                bestStreak = prefs[PreferencesKeys.BEST_STREAK] ?: 0,
                monthlyReportEnabled = prefs[PreferencesKeys.MONTHLY_REPORT_ENABLED] ?: true,
                barWeight = prefs[PreferencesKeys.BAR_WEIGHT] ?: 45f,
                loadingSides = prefs[PreferencesKeys.LOADING_SIDES] ?: 2,
                availablePlates = parseAvailablePlates(prefs[PreferencesKeys.AVAILABLE_PLATES]),
                usePlateInventory = prefs[PreferencesKeys.USE_PLATE_INVENTORY] ?: false,
                plateInventory = PlateInventoryPreferences.decode(prefs[PreferencesKeys.PLATE_INVENTORY]),
                preferredWeightUnit = IronLogBackupFormat.CANONICAL_WEIGHT_UNIT
            )
        )
    }

    suspend fun restorePortablePreferences(
        backup: IronLogBackupFormat.BackupPreferences,
        replaceMode: Boolean
    ) {
        val safe = IronLogBackupFormat.sanitizePreferences(backup)
        context.dataStore.edit { preferences ->
            if (!replaceMode) {
                val currentBest = preferences[PreferencesKeys.BEST_STREAK] ?: 0
                if (safe.bestStreak > currentBest) {
                    preferences[PreferencesKeys.BEST_STREAK] = safe.bestStreak
                }
                return@edit
            }

            preferences[PreferencesKeys.USER_NAME] = safe.userName
            preferences[PreferencesKeys.THEME_COLOR] = safe.themeColor
            if (safe.customThemeColor == null) {
                preferences.remove(PreferencesKeys.CUSTOM_THEME_COLOR)
            } else {
                preferences[PreferencesKeys.CUSTOM_THEME_COLOR] = safe.customThemeColor
            }
            preferences[PreferencesKeys.THEME_FONT] = safe.themeFont
            preferences.remove(PreferencesKeys.CUSTOM_FONT_URI)
            preferences[PreferencesKeys.DARK_MODE] = safe.darkMode
            preferences[PreferencesKeys.TIMER_AUTO_START] = safe.timerAutoStart
            preferences[PreferencesKeys.TIMER_AUDIO_ENABLED] = safe.timerAudioEnabled
            preferences[PreferencesKeys.TIMER_VIBRATE_ENABLED] = safe.timerVibrateEnabled
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = safe.keepScreenOn
            preferences[PreferencesKeys.REST_DAYS_PER_WEEK] = safe.restDaysPerWeek
            preferences[PreferencesKeys.BEST_STREAK] = safe.bestStreak
            preferences[PreferencesKeys.MONTHLY_REPORT_ENABLED] = safe.monthlyReportEnabled
            preferences[PreferencesKeys.BAR_WEIGHT] = safe.barWeight
            preferences[PreferencesKeys.LOADING_SIDES] = safe.loadingSides
            preferences[PreferencesKeys.AVAILABLE_PLATES] = safe.availablePlates.joinToString(",", "[", "]")
            preferences[PreferencesKeys.USE_PLATE_INVENTORY] = safe.usePlateInventory
            val encodedInventory = PlateInventoryPreferences.encode(safe.plateInventory)
            if (encodedInventory.isEmpty()) {
                preferences.remove(PreferencesKeys.PLATE_INVENTORY)
            } else {
                preferences[PreferencesKeys.PLATE_INVENTORY] = encodedInventory
            }
        }
    }
}
