# IronLog Routines Feature - Implementation Plan

## Overview

This plan details the implementation of a complete routines system for IronLog, allowing users to create workout templates with multiple days and exercises.

## Requirements Summary

- Users can create up to 3 routines (hard limit)
- Each routine can have up to 7 custom-named days (e.g., "Push Day", "Pull Day", "Legs")
- Each day contains a list of exercises (no sets/reps targets, just exercise list)
- Users can mark one routine as "active"
- Active routine displays on home screen routine card
- Tapping active routine card navigates to day selection
- Starting workout from routine day loads ALL exercises from that day
- UI screens for create/edit/view/delete routines and days
- MVP scope: simple, fast, offline-first

---

## Phase 1: Database Schema Changes (Day 1)

### 1.1 Create New RoutineDay Entity

**File:** `app/src/main/java/com/example/gymtime/data/db/entity/RoutineDay.kt` (NEW)

```kotlin
package com.example.gymtime.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_days",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val name: String,           // e.g., "Push Day", "Pull Day"
    val orderIndex: Int         // Display order within routine
)
```

### 1.2 Modify RoutineExercise Entity

**File:** `app/src/main/java/com/example/gymtime/data/db/entity/RoutineExercise.kt` (MODIFY)

**Current (v5):**
```kotlin
@Entity(
    tableName = "routine_exercises",
    primaryKeys = ["routineId", "exerciseId"]
)
data class RoutineExercise(
    val routineId: Long,
    val exerciseId: Long,
    val orderIndex: Int
)
```

**New (v6) - BREAKING CHANGE:**
```kotlin
@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDay::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineDayId"), Index("exerciseId")]
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineDayId: Long,     // Changed from routineId to routineDayId
    val exerciseId: Long,
    val orderIndex: Int
)
```

### 1.3 Modify Workout Entity

**File:** `app/src/main/java/com/example/gymtime/data/db/entity/Workout.kt` (MODIFY)

**Add field:**
```kotlin
@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = RoutineDay::class,
            parentColumns = ["id"],
            childColumns = ["routineDayId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("routineDayId")]
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Date,
    val endTime: Date?,
    val name: String?,
    val note: String?,
    val rating: Int?,
    val ratingNote: String?,
    val routineDayId: Long? = null     // NEW: Links to routine day if started from routine
)
```

### 1.4 Update Database and Migration

**File:** `app/src/main/java/com/example/gymtime/data/db/GymTimeDatabase.kt` (MODIFY)

**Update version and entities:**
```kotlin
@Database(
    entities = [
        Exercise::class,
        Workout::class,
        Set::class,
        Routine::class,
        RoutineExercise::class,
        RoutineDay::class,          // NEW
        MuscleGroup::class
    ],
    version = 6,                     // Changed from 5 to 6
    exportSchema = false
)
```

**File:** `app/src/main/java/com/example/gymtime/di/DatabaseModule.kt` (MODIFY)

**Add migration after MIGRATION_4_5:**
```kotlin
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Log.d(TAG, "Running migration 5 -> 6: Adding routines days structure")

        // Create routine_days table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS routine_days (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routineId INTEGER NOT NULL,
                name TEXT NOT NULL,
                orderIndex INTEGER NOT NULL,
                FOREIGN KEY(routineId) REFERENCES routines(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_days_routineId ON routine_days(routineId)")

        // Drop old routine_exercises table (breaking change)
        database.execSQL("DROP TABLE IF EXISTS routine_exercises")

        // Create new routine_exercises table with routineDayId
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS routine_exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                routineDayId INTEGER NOT NULL,
                exerciseId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                FOREIGN KEY(routineDayId) REFERENCES routine_days(id) ON DELETE CASCADE,
                FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON DELETE CASCADE
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_routineDayId ON routine_exercises(routineDayId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_routine_exercises_exerciseId ON routine_exercises(exerciseId)")

        // Add routineDayId to workouts table
        database.execSQL("ALTER TABLE workouts ADD COLUMN routineDayId INTEGER DEFAULT NULL")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_workouts_routineDayId ON workouts(routineDayId)")
    }
}
```

**Update addMigrations:**
```kotlin
.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
```

---

## Phase 2: Data Layer Enhancements (Days 1-2)

### 2.1 Enhance RoutineDao

**File:** `app/src/main/java/com/example/gymtime/data/db/dao/RoutineDao.kt` (MODIFY)

**Add data classes at top:**
```kotlin
data class RoutineWithDays(
    @Embedded val routine: Routine,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId"
    )
    val days: List<RoutineDay>
)

data class RoutineDayWithExercises(
    @Embedded val day: RoutineDay,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineDayId",
        entity = RoutineExercise::class
    )
    val exercises: List<RoutineExerciseWithDetails>
)

data class RoutineExerciseWithDetails(
    @Embedded val routineExercise: RoutineExercise,
    @Relation(
        parentColumn = "exerciseId",
        entityColumn = "id"
    )
    val exercise: Exercise
)
```

**Add methods:**
```kotlin
@Update
suspend fun updateRoutine(routine: Routine)

@Delete
suspend fun deleteRoutine(routine: Routine)

@Query("SELECT COUNT(*) FROM routines")
fun getRoutineCount(): Flow<Int>

// Routine Day methods
@Insert
suspend fun insertRoutineDay(day: RoutineDay): Long

@Update
suspend fun updateRoutineDay(day: RoutineDay)

@Delete
suspend fun deleteRoutineDay(day: RoutineDay)

@Query("SELECT * FROM routine_days WHERE routineId = :routineId ORDER BY orderIndex ASC")
fun getDaysForRoutine(routineId: Long): Flow<List<RoutineDay>>

@Query("SELECT COUNT(*) FROM routine_days WHERE routineId = :routineId")
fun getDayCountForRoutine(routineId: Long): Flow<Int>

@Query("SELECT * FROM routine_days WHERE id = :dayId")
fun getRoutineDayWithExercises(dayId: Long): Flow<RoutineDayWithExercises?>

// Routine Exercise methods
@Insert
suspend fun insertRoutineExercise(routineExercise: RoutineExercise)

@Delete
suspend fun deleteRoutineExercise(routineExercise: RoutineExercise)

@Query("DELETE FROM routine_exercises WHERE routineDayId = :dayId")
suspend fun deleteAllExercisesForDay(dayId: Long)

@Query("SELECT * FROM routine_exercises WHERE routineDayId = :dayId ORDER BY orderIndex ASC")
fun getExercisesForDay(dayId: Long): Flow<List<RoutineExerciseWithDetails>>

@Query("""
    SELECT e.* FROM exercises e
    INNER JOIN routine_exercises re ON e.id = re.exerciseId
    WHERE re.routineDayId = :dayId
    ORDER BY re.orderIndex ASC
""")
fun getExerciseListForDay(dayId: Long): Flow<List<Exercise>>
```

### 2.2 Update UserPreferencesRepository

**File:** `app/src/main/java/com/example/gymtime/data/repository/UserPreferencesRepository.kt` (MODIFY)

**Add to PreferencesKeys:**
```kotlin
private object PreferencesKeys {
    val USER_NAME = stringPreferencesKey("user_name")
    val ACTIVE_ROUTINE_ID = longPreferencesKey("active_routine_id")  // NEW
}
```

**Add methods:**
```kotlin
val activeRoutineId: Flow<Long?> = dataStore.data
    .map { preferences ->
        preferences[PreferencesKeys.ACTIVE_ROUTINE_ID]
    }

suspend fun setActiveRoutineId(routineId: Long?) {
    dataStore.edit { preferences ->
        if (routineId == null) {
            preferences.remove(PreferencesKeys.ACTIVE_ROUTINE_ID)
        } else {
            preferences[PreferencesKeys.ACTIVE_ROUTINE_ID] = routineId
        }
    }
}
```

---

## Phase 3: Navigation Setup (Day 2)

### 3.1 Add Routes to Screen.kt

**File:** `app/src/main/java/com/example/gymtime/navigation/Screen.kt` (MODIFY)

**Add after existing routes:**
```kotlin
object RoutineList : Screen("routine_list", Icons.Default.Home) {
    override val route = "routine_list"
}

object RoutineForm : Screen("routine_form?routineId={routineId}", Icons.Default.Home) {
    fun createRoute(routineId: Long? = null) = if (routineId != null) {
        "routine_form?routineId=$routineId"
    } else {
        "routine_form"
    }
}

object RoutineDayList : Screen("routine_day_list/{routineId}", Icons.Default.Home) {
    fun createRoute(routineId: Long) = "routine_day_list/$routineId"
}

object RoutineDayForm : Screen("routine_day_form/{routineId}?dayId={dayId}", Icons.Default.Home) {
    fun createRoute(routineId: Long, dayId: Long? = null) = if (dayId != null) {
        "routine_day_form/$routineId?dayId=$dayId"
    } else {
        "routine_day_form/$routineId"
    }
}

object RoutineDayStart : Screen("routine_day_start/{routineId}", Icons.Default.Home) {
    fun createRoute(routineId: Long) = "routine_day_start/$routineId"
}
```

---

## Phase 4: ViewModel Implementations (Days 2-3)

### 4.1 RoutineListViewModel

**File:** `app/src/main/java/com/example/gymtime/ui/routine/RoutineListViewModel.kt` (NEW)

```kotlin
package com.example.gymtime.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val routines: Flow<List<Routine>> = routineDao.getAllRoutines()

    val activeRoutineId: Flow<Long?> = userPreferencesRepository.activeRoutineId

    val canCreateMoreRoutines: StateFlow<Boolean> = routineDao.getRoutineCount()
        .map { count -> count < 3 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setActiveRoutine(routineId: Long?) {
        viewModelScope.launch {
            userPreferencesRepository.setActiveRoutineId(routineId)
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            // If deleting active routine, clear active state
            val currentActiveId = userPreferencesRepository.activeRoutineId.first()
            if (currentActiveId == routine.id) {
                userPreferencesRepository.setActiveRoutineId(null)
            }
            routineDao.deleteRoutine(routine)
        }
    }
}
```

### 4.2 RoutineFormViewModel

**File:** `app/src/main/java/com/example/gymtime/ui/routine/RoutineFormViewModel.kt` (NEW)

```kotlin
package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.entity.Routine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineFormViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long? = savedStateHandle.get<String>("routineId")?.toLongOrNull()

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    val isEditMode: StateFlow<Boolean> = MutableStateFlow(routineId != null)

    val isSaveEnabled: StateFlow<Boolean> = _routineName
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveSuccessEvent = Channel<Long>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        if (routineId != null) {
            viewModelScope.launch {
                routineDao.getRoutineById(routineId).firstOrNull()?.let { routine ->
                    _routineName.value = routine.name
                }
            }
        }
    }

    fun updateRoutineName(name: String) {
        _routineName.value = name
    }

    fun saveRoutine() {
        viewModelScope.launch {
            val name = _routineName.value.trim()
            if (name.isBlank()) return@launch

            if (routineId != null) {
                // Edit mode
                routineDao.updateRoutine(Routine(id = routineId, name = name))
                _saveSuccessEvent.send(routineId)
            } else {
                // Create mode
                val newId = routineDao.insertRoutine(Routine(name = name))
                _saveSuccessEvent.send(newId)
            }
        }
    }
}
```

### 4.3 RoutineDayListViewModel

**File:** `app/src/main/java/com/example/gymtime/ui/routine/RoutineDayListViewModel.kt` (NEW)

```kotlin
package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.entity.RoutineDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineDayListViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _routineId = MutableStateFlow(savedStateHandle.get<String>("routineId")?.toLong() ?: 0L)
    val routineId: StateFlow<Long> = _routineId.asStateFlow()

    val routineName: Flow<String> = _routineId
        .flatMapLatest { id ->
            routineDao.getRoutineById(id).map { it?.name ?: "" }
        }

    val days: Flow<List<RoutineDay>> = _routineId
        .flatMapLatest { id ->
            routineDao.getDaysForRoutine(id)
        }

    val canAddMoreDays: StateFlow<Boolean> = _routineId
        .flatMapLatest { id ->
            routineDao.getDayCountForRoutine(id)
        }
        .map { count -> count < 7 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun deleteDay(day: RoutineDay) {
        viewModelScope.launch {
            routineDao.deleteRoutineDay(day)
        }
    }
}
```

### 4.4 RoutineDayFormViewModel

**File:** `app/src/main/java/com/example/gymtime/ui/routine/RoutineDayFormViewModel.kt` (NEW)

```kotlin
package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.ExerciseDao
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.entity.Exercise
import com.example.gymtime.data.db.entity.RoutineDay
import com.example.gymtime.data.db.entity.RoutineExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineDayFormViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<String>("routineId")?.toLong() ?: 0L
    private val dayId: Long? = savedStateHandle.get<String>("dayId")?.toLongOrNull()

    private val _dayName = MutableStateFlow("")
    val dayName: StateFlow<String> = _dayName.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow<Set<Long>>(emptySet())

    val selectedExercises: Flow<List<Exercise>> = _selectedExerciseIds
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else exerciseDao.getAllExercises().map { exercises ->
                exercises.filter { it.id in ids }
            }
        }

    val availableExercises: Flow<List<Exercise>> = exerciseDao.getAllExercises()

    val isEditMode: StateFlow<Boolean> = MutableStateFlow(dayId != null)

    val isSaveEnabled: StateFlow<Boolean> = combine(
        _dayName,
        _selectedExerciseIds
    ) { name, exercises ->
        name.isNotBlank() && exercises.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveSuccessEvent = Channel<Unit>(Channel.BUFFERED)
    val saveSuccessEvent = _saveSuccessEvent.receiveAsFlow()

    init {
        if (dayId != null) {
            viewModelScope.launch {
                routineDao.getRoutineDayWithExercises(dayId).firstOrNull()?.let { dayWithExercises ->
                    _dayName.value = dayWithExercises.day.name
                    _selectedExerciseIds.value = dayWithExercises.exercises.map { it.exercise.id }.toSet()
                }
            }
        }
    }

    fun updateDayName(name: String) {
        _dayName.value = name
    }

    fun addExercise(exerciseId: Long) {
        _selectedExerciseIds.value = _selectedExerciseIds.value + exerciseId
    }

    fun removeExercise(exerciseId: Long) {
        _selectedExerciseIds.value = _selectedExerciseIds.value - exerciseId
    }

    fun saveDay() {
        viewModelScope.launch {
            val name = _dayName.value.trim()
            val exerciseIds = _selectedExerciseIds.value.toList()

            if (name.isBlank() || exerciseIds.isEmpty()) return@launch

            if (dayId != null) {
                // Edit mode
                routineDao.updateRoutineDay(RoutineDay(id = dayId, routineId = routineId, name = name, orderIndex = 0))
                routineDao.deleteAllExercisesForDay(dayId)
                exerciseIds.forEachIndexed { index, exerciseId ->
                    routineDao.insertRoutineExercise(
                        RoutineExercise(routineDayId = dayId, exerciseId = exerciseId, orderIndex = index)
                    )
                }
            } else {
                // Create mode
                val maxOrder = routineDao.getDaysForRoutine(routineId).first().maxOfOrNull { it.orderIndex } ?: -1
                val newDayId = routineDao.insertRoutineDay(
                    RoutineDay(routineId = routineId, name = name, orderIndex = maxOrder + 1)
                )
                exerciseIds.forEachIndexed { index, exerciseId ->
                    routineDao.insertRoutineExercise(
                        RoutineExercise(routineDayId = newDayId, exerciseId = exerciseId, orderIndex = index)
                    )
                }
            }

            _saveSuccessEvent.send(Unit)
        }
    }
}
```

### 4.5 RoutineDayStartViewModel

**File:** `app/src/main/java/com/example/gymtime/ui/routine/RoutineDayStartViewModel.kt` (NEW)

```kotlin
package com.example.gymtime.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gymtime.data.db.dao.RoutineDao
import com.example.gymtime.data.db.dao.WorkoutDao
import com.example.gymtime.data.db.entity.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class RoutineDayStartViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long = savedStateHandle.get<String>("routineId")?.toLong() ?: 0L

    val routineName: Flow<String> = routineDao.getRoutineById(routineId).map { it?.name ?: "" }

    val daysWithExercises = routineDao.getDaysForRoutine(routineId)
        .flatMapLatest { days ->
            if (days.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(days.map { day ->
                    routineDao.getRoutineDayWithExercises(day.id)
                }) { array ->
                    array.filterNotNull().toList()
                }
            }
        }

    private val _startWorkoutEvent = Channel<Long>(Channel.BUFFERED)
    val startWorkoutEvent = _startWorkoutEvent.receiveAsFlow()

    fun startWorkoutFromDay(dayId: Long) {
        viewModelScope.launch {
            // Get exercises for this day
            val exercises = routineDao.getExerciseListForDay(dayId).first()

            if (exercises.isEmpty()) return@launch

            // Create workout linked to routine day
            val workoutId = workoutDao.insertWorkout(
                Workout(
                    startTime = Date(),
                    endTime = null,
                    routineDayId = dayId
                )
            )

            // Navigate to first exercise
            _startWorkoutEvent.send(exercises.first().id)
        }
    }
}
```

### 4.6 Update HomeViewModel

**File:** `app/src/main/java/com/example/gymtime/ui/home/HomeViewModel.kt` (MODIFY)

**Add dependency:**
```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val setDao: SetDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val routineDao: RoutineDao  // NEW
) : ViewModel() {
```

**Replace hardcoded routine values:**
```kotlin
val activeRoutineId: Flow<Long?> = userPreferencesRepository.activeRoutineId

val hasActiveRoutine: StateFlow<Boolean> = activeRoutineId
    .map { it != null }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

val activeRoutineName: StateFlow<String?> = activeRoutineId
    .flatMapLatest { id ->
        if (id == null) flowOf(null)
        else routineDao.getRoutineById(id).map { it?.name }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

---

## Phase 5: Screen Implementations (Days 3-5)

### 5.1 RoutineListScreen

**File:** `app/src/main/java/com/example/gymtime/ui/routine/RoutineListScreen.kt` (NEW)

```kotlin
package com.example.gymtime.ui.routine

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.data.db.entity.Routine
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineListScreen(
    navController: NavController,
    viewModel: RoutineListViewModel = hiltViewModel()
) {
    val routines by viewModel.routines.collectAsState(initial = emptyList())
    val activeRoutineId by viewModel.activeRoutineId.collectAsState(initial = null)
    val canCreateMore by viewModel.canCreateMoreRoutines.collectAsState()

    var showMaxRoutinesDialog by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "My Routines",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (canCreateMore) {
                        navController.navigate(Screen.RoutineForm.createRoute())
                    } else {
                        showMaxRoutinesDialog = true
                    }
                },
                containerColor = if (canCreateMore) PrimaryAccent else TextTertiary,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Routine", modifier = Modifier.size(32.dp))
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (routines.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No routines yet.\nTap + to create your first routine!",
                        color = TextTertiary,
                        fontSize = 16.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(routines) { routine ->
                        RoutineListItem(
                            routine = routine,
                            isActive = routine.id == activeRoutineId,
                            onTap = { navController.navigate(Screen.RoutineDayList.createRoute(routine.id)) },
                            onSetActive = { viewModel.setActiveRoutine(routine.id) },
                            onEdit = { navController.navigate(Screen.RoutineForm.createRoute(routine.id)) },
                            onDelete = { routineToDelete = routine }
                        )
                    }
                }
            }
        }
    }

    // Max routines dialog
    if (showMaxRoutinesDialog) {
        AlertDialog(
            onDismissRequest = { showMaxRoutinesDialog = false },
            title = { Text("Maximum Routines Reached") },
            text = { Text("You can only create up to 3 routines. Delete an existing routine to create a new one.") },
            confirmButton = {
                TextButton(onClick = { showMaxRoutinesDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Delete confirmation dialog
    routineToDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete Routine?") },
            text = { Text("This will permanently delete \"${routine.name}\" and all its days and exercises.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRoutine(routine)
                        routineToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RoutineListItem(
    routine: Routine,
    isActive: Boolean,
    onTap: () -> Unit,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    GlowCard(
        onClick = onTap,
        onLongClick = { showMenu = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = routine.name,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (isActive) {
                        Surface(
                            color = PrimaryAccent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = PrimaryAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (!isActive) {
                    DropdownMenuItem(
                        text = { Text("Set as Active", color = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onSetActive()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Edit Name", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
```

### 5.2 RoutineFormScreen

**File:** `app/src/main/java/com/example/gymtime/ui/routine/RoutineFormScreen.kt` (NEW)

```kotlin
package com.example.gymtime.ui.routine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.gymtime.navigation.Screen
import com.example.gymtime.ui.components.GlowCard
import com.example.gymtime.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineFormScreen(
    navController: NavController,
    viewModel: RoutineFormViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val isSaveEnabled by viewModel.isSaveEnabled.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saveSuccessEvent.collect { routineId ->
            if (isEditMode) {
                navController.navigateUp()
            } else {
                navController.navigate(Screen.RoutineDayList.createRoute(routineId)) {
                    popUpTo(Screen.RoutineList.route)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) "Edit Routine" else "New Routine",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveRoutine() },
                        enabled = isSaveEnabled
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Save",
                            tint = if (isSaveEnabled) PrimaryAccent else TextTertiary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ROUTINE NAME",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = TextTertiary
            )

            GlowCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = routineName,
                    onValueChange = { viewModel.updateRoutineName(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontSize = 18.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (routineName.isEmpty()) {
                            Text(
                                text = "e.g., Push Pull Legs",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextTertiary,
                                fontSize = 18.sp
                            )
                        }
                        innerTextField()
                    },
                    singleLine = true
                )
            }
        }
    }
}
```

### 5.3-5.5 Additional Screens

Due to length, the remaining screens (RoutineDayListScreen.kt, RoutineDayFormScreen.kt, RoutineDayStartScreen.kt) follow the same patterns as above with:
- LazyColumn for lists
- GlowCard for items
- TopAppBar with back button and save/action buttons
- FAB for adding items
- Context menus for edit/delete
- Confirmation dialogs for destructive actions
- StateFlow observation for reactive UI
- Navigation via Screen.kt routes

---

## Phase 6: Home Screen Integration (Day 5)

### 6.1 Update HomeScreen

**File:** `app/src/main/java/com/example/gymtime/ui/home/HomeScreen.kt` (MODIFY)

**Update RoutineCard usage (around line 200):**
```kotlin
RoutineCard(
    hasActiveRoutine = hasActiveRoutine,
    routineName = activeRoutineName,
    onClick = {
        if (hasActiveRoutine && activeRoutineId != null) {
            navController.navigate(Screen.RoutineDayStart.createRoute(activeRoutineId))
        } else {
            navController.navigate(Screen.RoutineList.route)
        }
    }
)
```

### 6.2 Update RoutineCard Component

**File:** `app/src/main/java/com/example/gymtime/ui/components/RoutineCard.kt` (MODIFY)

```kotlin
@Composable
fun RoutineCard(
    hasActiveRoutine: Boolean,
    routineName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasActiveRoutine) "ACTIVE ROUTINE" else "NO ACTIVE ROUTINE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = TextTertiary
                )
            }

            Text(
                text = routineName ?: "Select a Routine",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = if (hasActiveRoutine) "Tap to start workout" else "Tap to view routines",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}
```

---

## Phase 7: Testing Checklist (Days 6-7)

### Database & Migration
- [ ] Fresh install creates routine_days table
- [ ] Fresh install creates new routine_exercises table
- [ ] Fresh install adds routineDayId to workouts
- [ ] Migration 5→6 runs successfully on existing database
- [ ] No data loss during migration
- [ ] Foreign key constraints work (cascade deletion)

### Routine Management
- [ ] Create routine with valid name
- [ ] Cannot create routine with blank name
- [ ] Edit routine name
- [ ] Delete routine shows confirmation
- [ ] Deleting routine deletes all days and exercises (cascade)
- [ ] Can create up to 3 routines
- [ ] FAB disabled at 3 routines
- [ ] Max routines dialog shows when tapping disabled FAB
- [ ] Set routine as active
- [ ] Active routine shows badge
- [ ] Only one routine can be active at a time

### Day Management
- [ ] Create day with valid name
- [ ] Cannot create day with blank name or no exercises
- [ ] Edit day name and exercises
- [ ] Delete day shows confirmation
- [ ] Deleting day deletes all routine_exercises for that day
- [ ] Can create up to 7 days per routine
- [ ] FAB disabled at 7 days
- [ ] Max days dialog shows when tapping disabled FAB
- [ ] Days display in correct order

### Exercise Selection for Days
- [ ] Exercise picker shows all available exercises
- [ ] Can add multiple exercises
- [ ] Can remove exercises
- [ ] Selected exercises display in list
- [ ] Save button disabled until at least 1 exercise selected
- [ ] Exercise order preserved

### Workout Start from Routine
- [ ] Tapping active routine card navigates to day selection
- [ ] Day selection shows all days with exercise counts
- [ ] Starting workout from day creates Workout with routineDayId
- [ ] All exercises from day load immediately
- [ ] Can navigate between exercises in routine
- [ ] Can add additional exercises not in routine
- [ ] Finishing workout behaves normally

### Home Screen Integration
- [ ] Active routine name displays correctly
- [ ] "No Active Routine" shows when no active routine
- [ ] Tapping card with active routine goes to day selection
- [ ] Tapping card with no active routine goes to routine list
- [ ] Home screen updates when active routine changes

### Navigation
- [ ] All navigation routes work correctly
- [ ] Back button behavior is correct
- [ ] Deep linking works with parameters
- [ ] Bottom nav maintains state

### Edge Cases
- [ ] Deleting active routine clears active status
- [ ] Deleting routine while viewing its days handles gracefully
- [ ] Empty states display correctly (no routines, no days, no exercises)
- [ ] Long routine names don't break layout
- [ ] Long day names don't break layout
- [ ] Large number of exercises in day displays correctly

---

## Critical Files Summary

### NEW FILES (10):
1. `RoutineDay.kt` - Database entity
2. `RoutineListViewModel.kt`
3. `RoutineFormViewModel.kt`
4. `RoutineDayListViewModel.kt`
5. `RoutineDayFormViewModel.kt`
6. `RoutineDayStartViewModel.kt`
7. `RoutineListScreen.kt`
8. `RoutineFormScreen.kt`
9. `RoutineDayListScreen.kt`
10. `RoutineDayFormScreen.kt`
11. `RoutineDayStartScreen.kt`

### MODIFIED FILES (10):
1. `RoutineExercise.kt` - Breaking change (routineId → routineDayId)
2. `Workout.kt` - Add routineDayId field
3. `GymTimeDatabase.kt` - Version bump, add entity
4. `DatabaseModule.kt` - Add migration
5. `RoutineDao.kt` - Add many new methods
6. `UserPreferencesRepository.kt` - Add activeRoutineId
7. `Screen.kt` - Add 5 new routes
8. `MainActivity.kt` - Add 5 new composable routes
9. `HomeViewModel.kt` - Replace hardcoded routine values
10. `HomeScreen.kt` - Update RoutineCard usage
11. `RoutineCard.kt` - Update to use real data

---

## Implementation Sequence

**Recommended order to minimize errors:**

1. Database schema changes (Phase 1) - Get foundation right
2. Data layer (Phase 2) - DAO methods and repository
3. Navigation (Phase 3) - Routes defined early
4. ViewModels (Phase 4) - Business logic before UI
5. Screens (Phase 5) - UI implementation
6. Home integration (Phase 6) - Connect everything
7. Testing (Phase 7) - Validate end-to-end

**Estimated Total Time:** 6-7 days for full implementation + testing

---

## Key Design Decisions

1. **RoutineDay as separate entity** - Allows proper cascade deletion and future extensibility
2. **Breaking change to RoutineExercise** - Necessary to link exercises to days, not routines
3. **Workout.routineDayId nullable** - Workouts can be started without routines (manual start)
4. **3 routine limit** - Prevents overwhelming users, keeps MVP focused
5. **7 day limit** - Reasonable for most training splits (PPL = 6 days, Upper/Lower = 4-6 days)
6. **No set/rep targets in routines** - MVP keeps it simple, just exercise lists
7. **Active routine in DataStore** - Persists across app restarts, doesn't require database query
8. **Cascade deletion** - Deleting routine deletes days, deleting days deletes routine_exercises

---

## Potential Challenges

1. **Database migration complexity** - Breaking change requires careful testing
2. **UI state management** - Many nested flows and StateFlows
3. **Navigation complexity** - Multiple nested screens with parameters
4. **Exercise picker UX** - Needs to be fast and intuitive
5. **Empty states** - Many scenarios where lists are empty
6. **Long names** - Need to handle text overflow gracefully

---

This plan provides complete implementation details while maintaining IronLog's core philosophy of speed, simplicity, and offline-first design.
