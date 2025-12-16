package com.example.gymtime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val USER_NAME = stringPreferencesKey("user_name")
        val EXERCISES_SEEDED = booleanPreferencesKey("exercises_seeded")
        val ACTIVE_ROUTINE_ID = androidx.datastore.preferences.core.longPreferencesKey("active_routine_id")

        // Theme and UI preferences
        val THEME_COLOR = stringPreferencesKey("theme_color")
        val TIMER_AUTO_START = booleanPreferencesKey("timer_auto_start")

        // Plate calculator preferences
        val AVAILABLE_PLATES = stringPreferencesKey("available_plates")
        val BAR_WEIGHT = androidx.datastore.preferences.core.floatPreferencesKey("bar_weight")
        val LOADING_SIDES = androidx.datastore.preferences.core.intPreferencesKey("loading_sides")

        // Streak tracking
        val BEST_STREAK = intPreferencesKey("best_streak")
    }

    val userName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USER_NAME] ?: "Athlete"
        }

    val exercisesSeeded: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.EXERCISES_SEEDED] ?: false
        }

    val activeRoutineId: Flow<Long?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ACTIVE_ROUTINE_ID]
        }

    val themeColor: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.THEME_COLOR] ?: "lime"
        }

    val timerAutoStart: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.TIMER_AUTO_START] ?: true
        }

    val availablePlates: Flow<List<Float>> = context.dataStore.data
        .map { preferences ->
            val json = preferences[PreferencesKeys.AVAILABLE_PLATES]
                ?: "[45.0, 35.0, 25.0, 15.0, 10.0, 5.0, 2.5]"
            // Parse JSON to List<Float>
            json.removeSurrounding("[", "]")
                .split(",")
                .mapNotNull { it.trim().toFloatOrNull() }
        }

    val barWeight: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BAR_WEIGHT] ?: 45f
        }

    val loadingSides: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LOADING_SIDES] ?: 2
        }

    val bestStreak: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BEST_STREAK] ?: 0
        }

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

    suspend fun setTimerAutoStart(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TIMER_AUTO_START] = enabled
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

    suspend fun setLoadingSides(sides: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOADING_SIDES] = sides
        }
    }

    suspend fun togglePlate(plate: Float, currentPlates: List<Float>) {
        val newPlates = if (currentPlates.contains(plate)) {
            currentPlates - plate
        } else {
            (currentPlates + plate).sorted().reversed()
        }
        setAvailablePlates(newPlates)
    }

    suspend fun updateBestStreakIfNeeded(currentStreak: Int) {
        context.dataStore.edit { preferences ->
            val currentBest = preferences[PreferencesKeys.BEST_STREAK] ?: 0
            if (currentStreak > currentBest) {
                preferences[PreferencesKeys.BEST_STREAK] = currentStreak
            }
        }
    }
}
