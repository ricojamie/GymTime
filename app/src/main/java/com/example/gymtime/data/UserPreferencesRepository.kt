package com.example.gymtime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
