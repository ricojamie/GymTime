# IronLog - Project Context & Development Guide

## 1. Project Identity & Current Status

**Name:** IronLog
**Type:** Offline-first, privacy-centric strength training tracker for serious lifters
**Philosophy:** "Buy Once, Own Forever" - No ads, no subscriptions, no algorithm, no social bloat
**Status:** MVP+ Complete - Ready for real-world usage

### Core Values
- **The Logging Loop is God**: Every decision prioritizes speed and frictionless set logging
- **Privacy First**: All data stays local (Room DB, no cloud sync)
- **Offline or Nothing**: No internet required, ever
- **User Control**: Users own their data completely, can delete exercises and workouts permanently

---

## 2. Architecture

### Navigation Structure
```
MainActivity (Single Activity, No Fragments)
├── Scaffold with BottomNavigationBar
├── NavHost with 4 visible routes:
│   ├── Home (Dashboard) - Welcome, quick stats, quick start
│   ├── History - Workout history with calendar view
│   ├── Library - Exercise library management
│   └── Analytics - Volume trends and statistics
│   └── (Hidden routes accessed from above)
│       ├── ExerciseSelection - Browse/filter/select exercises
│       ├── ExerciseLogging - Log sets for selected exercise
│       ├── WorkoutResume - Resume ongoing workout
│       ├── PostWorkoutSummary - End-of-workout stats
│       ├── Settings - App configuration
│       ├── RoutineList/Form/DayList/DayForm/DayStart - Routine management
│       └── ExerciseForm - Create/edit exercises
└── Gradient background (GradientStart → GradientEnd)
```

### MVVM + Clean Architecture
- **Activity**: MainActivity (only UI entry point)
- **ViewModels**: HomeViewModel, ExerciseSelectionViewModel, ExerciseLoggingViewModel, etc.
- **Repositories**: UserPreferencesRepository (DataStore), VolumeOrbRepository
- **DAOs**: ExerciseDao, WorkoutDao, SetDao, RoutineDao, MuscleGroupDao
- **State Management**: Flow/StateFlow for reactive data, ViewModel for business logic
- **Async**: Coroutines with Dispatchers.IO for database operations

### Dependency Injection
- **DI Framework**: Hilt
- **Module**: DatabaseModule (provides all DAOs and Room instance)
- **Scope**: Singletons for DB and DAOs to ensure single instance across app

---

## 3. Tech Stack

**Language:** Kotlin (2.0+, with Kapt fallback for Hilt)
**UI Framework:** Jetpack Compose (no XML, no Fragments)
**Architecture:** MVVM with coroutines
**Database:** Room (SQLite) with TypeConverters for Date
**Local Storage:** AndroidX DataStore (for user preferences)
**Async:** Coroutines + Flow (not LiveData)
**DI:** Hilt (requires kapt plugin in build.gradle)
**Animations:** Compose animations (spring, fade, slide)
**Haptics:** Android haptic feedback for set logging
**Services:** Foreground service for rest timer with notifications

**Constraints (Hard Rules):**
- No Firebase
- No XML layouts
- No network calls in core logging features
- No ads or tracking
- Minimal dependencies

---

## 4. Completed Features

### Home Dashboard
- Welcome header with user name + split-color "IronLog" branding
- Quick Start card (responsive hero card) → starts workout or resumes
- Routine card with icon and routine name display
- Iron Streak card with current streak, best streak, and YTD workouts
- Weekly Volume Orb at bottom with tap-to-show details
- Fully responsive layout that adapts to device height (S24 Ultra to smaller phones)

### Exercise Selection Flow
- Search box with real-time filtering
- Multi-select filter pills (muscle groups from database)
- Exercise list (clean, minimal - name + muscle group)
- Long-press context menu (Edit, Delete with confirmation)
- Create custom exercises with ExerciseForm
- Superset mode selection

### Exercise Logging Flow
- Exercise header (name + target muscle)
- Foreground service rest timer with notification controls
- Weight/Reps input cards (large, thumb-friendly)
- RPE tracking
- Warmup toggle
- Log Set button (animated, haptic feedback)
- Session log with edit/delete capabilities
- Personal best indicators with timestamps
- Exercise history bottom sheet
- Plate calculator
- Volume progress bar
- Superset support with auto-rotation

### Workout Management
- WorkoutResume screen for continuing sessions
- PostWorkoutSummary with stats and workout rating
- Routine system with days and exercise ordering

### Analytics
- Weekly volume trends
- Volume Orb per muscle group
- Historical workout data

### Iron Streak System
- Sustainable consistency tracking (2 rest days per 7-day rolling window)
- Three states: Active (fire emoji), Resting (snowflake), Broken (skull)
- Best streak persistence (all-time record)
- Year-to-date workout count
- Algorithm in StreakCalculator.kt utility class

### History Enhancements
- Workout cards show total volume and working set count
- Excludes warmup sets from metrics

### Settings
- Theme color selection (5 color schemes)
- Timer auto-start toggle
- Plate calculator configuration (bar weight, available plates, loading sides)
- User name customization

### Database Features
- 25 pre-loaded exercises (across 8 muscle groups)
- Exercise deletion confirmation dialog
- Automatic seeding on first run
- Muscle groups pre-seeded (Back, Biceps, Chest, Core, Legs, Shoulders, Triceps, Cardio)
- Superset group tracking
- Workout ratings and notes

---

## 5. Database Schema (Room SQLite)

### Entities

**Exercise**
```kotlin
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMuscle: String,
    val logType: LogType,  // WEIGHT_REPS, REPS_ONLY, DURATION, WEIGHT_DISTANCE, DISTANCE_TIME
    val isCustom: Boolean,
    val notes: String?,
    val defaultRestSeconds: Int
)
```

**Workout**
```kotlin
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Date,
    val endTime: Date?,
    val name: String?,
    val note: String?,
    val rating: Int?,
    val ratingNote: String?,
    val routineDayId: Long?
)
```

**Set**
```kotlin
@Entity(tableName = "sets")
data class Set(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val weight: Float?,
    val reps: Int?,
    val rpe: Float?,
    val durationSeconds: Int?,
    val distanceMeters: Float?,
    val isWarmup: Boolean,
    val isComplete: Boolean,
    val timestamp: Date,
    val note: String?,
    val supersetGroupId: String?,
    val supersetOrderIndex: Int
)
```

**Routine / RoutineDay / RoutineExercise**
- Full routine management with days and exercise ordering

**MuscleGroup**
- Pre-seeded muscle categories

### Enums
```kotlin
enum class LogType {
    WEIGHT_REPS,     // Barbell exercises
    REPS_ONLY,       // Calisthenics
    DURATION,        // Time-based (plank, etc)
    WEIGHT_DISTANCE, // Weighted cardio (sled push)
    DISTANCE_TIME    // Cardio with distance and time
}
```

---

## 6. UI/UX Design

### Color Palette
```
Primary Accent (Default - Lime Green):
  - #A3E635 (Main)
  - #84CC16 (Dark)
  - #BEF264 (Light)

Background:
  - #121212 (Canvas)
  - #0D0D0D (Surface)
  - #0A1A0A (Gradient Start)
  - #0A0A0A (Gradient End)

Text:
  - #FFFFFF (Primary)
  - #E0E0E0 (Secondary)
  - #9CA3AF (Tertiary)
```

### Theme Options
- Lime Green (default)
- Electric Blue
- Cyber Purple
- Hot Pink
- Gold Amber

### Component Library
- **GlowCard**: Card with subtle gradient glow, supports onClick and onLongClick
- **VolumeOrb**: Animated circular progress indicator
- **VolumeProgressBar**: Horizontal progress bar for logging screen
- **PlateCalculatorSheet**: Bottom sheet for plate breakdown

### Layout Principles
- Hero Cards for primary actions
- One-handed use optimization
- Minimal scrolling
- Large touch targets

---

## 7. Development Workflow

### Code Standards
- **Compose**: Stateless composables, hoist state to ViewModel
- **Previews**: Include `@Preview(showBackground = true)` for UI components
- **Error Handling**: Never block UI thread
- **Logging**: Use `Log.d(TAG, "message")` for debugging
- **Naming**: Clear, descriptive names
- **Architecture**: Follow MVVM, never put DB calls in UI layer

### Git Workflow
- Feature branches merged to main
- Commit messages: descriptive, start with verb (Feat:, Fix:, Refactor:, Docs:)
- Always include Co-Authored-By footer with Claude credit

### Known Build Notes
- **Kapt Warning**: "Kapt currently doesn't support language version 2.0+" - Harmless, Hilt still works
- Build is clean with no deprecation warnings

---

## 8. Field Test Results (Historical Reference)

**Completed:** December 2024
**Overall Score:** 7.5/10

### Strong Points
1. **8-Second Logging Loop** - Fastest in market
2. **Offline Persistence** - Bulletproof
3. **Exercise History** - Excellent implementation
4. **Visual Design** - Clean dark theme
5. **Exercise Selection** - Real-time search, multi-select filters
6. **Auto-populated Values** - Shows previous workout data

### Areas Identified for Improvement
- Workout context during exercise addition
- Set editing discoverability
- Timer adjustment UX
- Analytics chart discoverability

---

## 9. Instructions for Claude

### Before Coding
1. Check this file first - it's the source of truth
2. Respect the philosophy: Speed and offline-first are non-negotiable
3. Ask questions if anything is unclear

### When Implementing
1. Prioritize the logging loop - new features must not slow down set logging
2. Keep it simple - no premature optimization
3. Use existing patterns - look at ExerciseLogging/ExerciseSelection as examples
4. Follow MVVM - UI ← ViewModel ← Repository/DAO ← Database

### Code Style
```kotlin
// Good: Stateless composable, clear names
@Composable
fun ExerciseListItem(
    exercise: Exercise,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlowCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        // Clear, single-responsibility UI
    }
}
```

### Common Pitfalls
1. Don't call suspend functions without `viewModelScope.launch { }`
2. Don't use LiveData - we use Flow/StateFlow
3. Don't block the UI thread - database ops go to Dispatchers.IO
4. Don't add screens without updating Screen.kt + MainActivity

---

## 10. Project Stats

### Codebase (Post-Refactor Dec 10, 2025)
- ~3,700 lines of Kotlin (797 lines removed in cleanup)
- 45+ Compose screens/components
- 10+ ViewModels
- 7 Database entities
- Clean build with no warnings

### Performance
- App loads in <2 seconds
- Exercise list renders instantly (lazy column)
- Database queries <100ms
- Set logging response immediate

---

## 11. Contact & Collaboration

- **Repo**: https://github.com/ricojamie/GymTime
- **Main Branch**: Always deployable
- **Latest Commit**: Always has working build

---

## 12. Automated Testing

### Running Tests

**Unit Tests (JVM - Fast, No Emulator Required):**
```bash
./gradlew testDebugUnitTest
```
- Runs all unit tests in `app/src/test/`
- Results: `app/build/reports/tests/testDebugUnitTest/index.html`

**Instrumented Tests (Requires Emulator/Device):**
```bash
./gradlew connectedDebugAndroidTest
```
- Runs DAO integration tests with in-memory Room database
- Results: `app/build/reports/androidTests/connected/index.html`

**Run Specific Test Class:**
```bash
./gradlew testDebugUnitTest --tests "com.example.gymtime.util.PlateCalculatorTest"
```

### Test Structure

```
app/src/
├── test/java/com/example/gymtime/       # Unit tests (JVM)
│   ├── util/
│   │   ├── TestDispatcherRule.kt        # Coroutine test helper
│   │   ├── StreakCalculatorTest.kt
│   │   ├── PlateCalculatorTest.kt
│   │   ├── OneRepMaxCalculatorTest.kt
│   │   └── WeekUtilsTest.kt
│   ├── ui/home/
│   │   └── HomeViewModelTest.kt
│   ├── ui/summary/
│   │   └── PostWorkoutSummaryViewModelTest.kt
│   └── data/
│       └── VolumeOrbRepositoryTest.kt
└── androidTest/java/com/example/gymtime/ # Instrumented tests
    └── data/db/dao/
        ├── SetDaoTest.kt
        └── WorkoutDaoTest.kt
```

### Test Dependencies
- **MockK** (1.13.8) - Kotlin mocking framework
- **Turbine** (1.1.0) - Flow testing
- **Coroutines Test** (1.8.0) - TestDispatcher for coroutine testing
- **Room Testing** (2.6.1) - In-memory database for DAO tests

### Writing New Tests

**ViewModel Test Pattern:**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    @get:Rule
    val dispatcherRule = TestDispatcherRule()

    private lateinit var mockDao: MyDao

    @Before
    fun setup() {
        mockDao = mockk(relaxed = true)
        coEvery { mockDao.getData() } returns flowOf(testData)
    }

    @Test
    fun loadDataUpdatesState() = runTest {
        val viewModel = MyViewModel(mockDao)
        advanceUntilIdle()
        assertEquals(expected, viewModel.state.value)
    }
}
```

**DAO Integration Test Pattern:**
```kotlin
@RunWith(AndroidJUnit4::class)
class MyDaoTest {
    private lateinit var database: GymTimeDatabase
    private lateinit var dao: MyDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GymTimeDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.myDao()
    }

    @After
    fun teardown() { database.close() }

    @Test
    fun insertAndRetrieve() = runTest {
        dao.insert(testEntity)
        val result = dao.getAll().first()
        assertEquals(testEntity, result)
    }
}
```

### Current Test Coverage
- **63 unit tests** across utilities, ViewModels, and repositories
- **DAO integration tests** for SetDao and WorkoutDao
- Focus areas: Volume calculations, streak logic, workout stats

### Known Testing Considerations
- `android.util.Log` calls require `testOptions { unitTests.isReturnDefaultValues = true }`
- StreakCalculator has timezone-sensitive edge cases at week boundaries
- ViewModel tests need `TestDispatcherRule` for proper coroutine handling

---

**Last Updated**: December 26, 2025
**Current Phase**: MVP+ Complete - In active use with automated testing
**Codebase Status**: Production-ready with test suite
