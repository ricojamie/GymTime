# IronLog Master Project Guide

Last updated: May 14, 2026

## 1. Project Identity

IronLog is an offline-first, privacy-centric strength training tracker for serious lifters.

Core product promise: buy once, own forever. No ads, no subscriptions, no algorithmic feed, no social bloat, and no cloud dependency.

Non-negotiables:

- The logging loop is the highest priority. Every feature must preserve fast, low-friction set logging.
- All core workout data stays local in Room.
- Core logging must work without internet.
- No Firebase, ads, tracking SDKs, or required account system.
- User-owned data matters: users can create, edit, import, export, and delete their training data.

## 2. Technology

- Language: Kotlin
- UI: Jetpack Compose and Material 3
- Architecture: Single Activity, MVVM, repositories, use cases, DAOs
- Navigation: Compose Navigation
- Database: Room over SQLite
- Preferences: AndroidX DataStore
- Async: Kotlin coroutines and Flow/StateFlow
- Dependency injection: Hilt with kapt
- Charts: Vico
- Minimum SDK: 34
- Compile/target SDK: 36

## 3. Architecture Overview

The app uses one Android activity and no fragments.

Main layers:

- `MainActivity`: creates the Compose app shell, theme, scaffold, bottom navigation, and NavHost.
- `navigation`: route definitions and bottom navigation.
- `ui`: Compose screens, components, and ViewModels.
- `domain`: use cases for analytics and derived business calculations.
- `data`: repositories, DataStore preferences, Room database, DAOs, and entities.
- `service`: foreground rest timer service.
- `util`: calculators, import/export utilities, date/time helpers, and formatting helpers.
- `di`: Hilt module that provides the Room database and DAOs.

The intended data flow is:

UI event -> ViewModel -> Repository or UseCase -> DAO/DataStore -> Room or preferences -> Flow/StateFlow back to UI.

Database work belongs off the UI thread. UI composables should stay mostly stateless and should not call DAOs directly.

## 4. Navigation Map

Visible bottom-nav routes:

- Home: dashboard, quick start/resume, routine card, streak, weekly volume orb.
- History: completed workouts, workout details, set groups, deletion flow.
- Library: exercise library and routine management entry points.
- Analytics: consistency, balance, and trend exploration.

Hidden routes:

- ExerciseSelection: browse, search, filter, create, edit, delete, and select exercises.
- ExerciseLogging: fast set logging for a selected exercise.
- WorkoutResume: resume an active workout.
- PostWorkoutSummary: end-of-workout stats, rating, and notes.
- Settings: preferences, timer, display, import/export, plate setup, and app configuration.
- ThemeSettings: theme color and font controls.
- MuscleGroupManagement: custom muscle group management.
- RoutineList, RoutineForm, RoutineDayList, RoutineDayForm, RoutineDayStart: routine builder and routine workout flow.
- ExerciseForm: create/edit exercise details.

## 5. Core Data Model

Primary Room entities:

- `Exercise`: name, target muscle, log type, default distance unit, custom flag, notes, default rest time, starred flag.
- `Workout`: start/end time, name, notes, rating, routine snapshots, and active routine metadata.
- `Set`: workout/exercise links, weight, reps, RPE, duration, distance, calories, warmup flag, completion flag, timestamp, notes, and superset metadata.
- `Routine`: routine name, active flag, and next day pointer.
- `RoutineDay`: ordered routine day names.
- `RoutineExercise`: ordered planned exercises with target sets, reps, rest, notes, and superset metadata.
- `WorkoutExerciseInstance`: per-workout plan snapshot for routine-backed workouts.
- `MuscleGroup`: user-visible muscle group names.

Important enums:

- `LogType`: `WEIGHT_REPS`, `REPS_ONLY`, `DURATION`, `WEIGHT_DISTANCE`, `DISTANCE_TIME`, `WEIGHT_TIME`, `CALORIES_TIME`.
- `DistanceUnit`: meters, kilometers, yards, feet, miles, steps, floors.

Database version: 12.

Important migration themes:

- Timestamp and superset indexes for fast workout and analytics queries.
- Routine day and workout plan snapshot support.
- Exercise starring for PR trophy tracking.
- Cardio support with raw distance value/unit, normalized meters, duration, and calories.
- Data is seeded locally on first database creation with default muscle groups and starter exercises.

## 6. Feature Inventory

Home:

- Time-aware greeting and user name display.
- Split-color IronLog branding.
- Quick Start card that starts or resumes a workout.
- Active routine card and routine shortcut.
- Iron Streak card with current streak, all-time best, year-to-date workouts, and allowed rest days.
- Weekly Volume Orb with progress against last week.
- Responsive layout for varied phone heights.

Exercise library and selection:

- Searchable exercise list.
- Muscle group filter chips.
- Custom exercise creation and editing.
- Exercise deletion confirmation.
- Muscle group management.
- Superset selection mode.

Exercise logging:

- Large thumb-friendly inputs.
- Log-type-aware fields for weight, reps, RPE, time, distance, and calories.
- Warmup toggle.
- Set notes.
- Fast Log Set action.
- Session set list with edit/delete.
- Previous workout prefill.
- Personal best indicators by rep count.
- Exercise history bottom sheet.
- Plate calculator.
- Volume progress bar.
- Rest timer with foreground notification.
- Superset auto-rotation.

Workout management:

- Ongoing workout detection and resume.
- Workout summary after finish.
- Workout rating and notes.
- Routine-backed workouts with plan snapshots.
- Reopen finished workout support.

Routines:

- Routine list/form screens.
- Routine day list/form screens.
- Planned exercises with order, sets, reps, rest, notes, and supersets.
- Active routine flow advances to the next day after a started routine workout is completed.

History:

- Workout cards show muscles trained, working-set count, and volume.
- Workout details sheet groups sets by exercise.
- Warmup sets are excluded from headline metrics.
- Workout deletion is supported.

Analytics:

- Consistency tab with year heatmap, streak stats, YTD stats, and PR trophy case.
- Balance tab with date range filters, set distribution, radar chart, and current muscle freshness.
- Trends tab with metric, period, interval, muscle, and exercise filters.
- Trend metrics include volume, sets, E1RM, average weight, density, reps, duration, distance, and calories.
- Starred exercises power the PR trophy case.

Settings:

- Theme color selection and custom theme support.
- Font settings.
- Dark mode.
- User name.
- Timer audio/vibration and auto-start.
- Keep-screen-on option.
- Plate calculator settings.
- Import/export utilities.
- Muscle group management.

Import/export:

- FitNotes import support.
- IronLog import/export support.
- Import logic should preserve user data and avoid duplicate sets where possible.

## 7. Analytics Rules

- Warmup sets are excluded from core analytics unless a feature explicitly says otherwise.
- Volume means `weight * reps` for weighted strength sets.
- Working set counts include non-warmup sets and support non-weighted activity.
- E1RM uses the Epley-style estimate already present in the app.
- Distance metrics normalize convertible units when needed and avoid mixing steps/floors with meter-convertible units.
- Current muscle freshness is based on last trained date by target muscle.
- Consistency heatmap currently uses working-set activity count, not raw pounds, so cardio and non-weighted sessions still register.

## 8. Design System

IronLog is a dark, high-contrast training app. It should feel fast, durable, and focused.

Current theme direction:

- Dark canvas and dark card surfaces.
- User-selectable accent color schemes.
- Lime green is the default accent.
- Large readable type for logging fields.
- Compact information density in repeated-use screens.
- Cards should support the work, not become decorative clutter.

Key reusable UI:

- `GlowCard`
- `VolumeOrb`
- `VolumeProgressBar`
- `WeeklyVolumeCard`
- `RoutineCard`
- `InputCard`
- `TimeInputCard`
- `PlateCalculatorSheet`
- Analytics charts and cards

## 9. Testing

Fast unit tests:

```bash
./gradlew testDebugUnitTest
```

Instrumented tests:

```bash
./gradlew connectedDebugAndroidTest
```

Notable test areas:

- `util`: streaks, plates, one-rep max, time, week calculations.
- `domain/analytics`: balance, consistency, trends.
- `ui/home`: home ViewModel.
- `ui/exercise`: logging ViewModel and superset manager.
- `ui/summary`: post-workout summary ViewModel.
- `data`: repositories and volume orb behavior.
- `androidTest/data/db/dao`: Room DAO integration tests.

Testing notes:

- `testOptions { unitTests.isReturnDefaultValues = true }` is required because some code references Android logging APIs.
- ViewModel tests should use `TestDispatcherRule`.
- DAO tests should use in-memory Room.
- If changing Room schema, update migrations and entity definitions together.

## 10. Development Rules

Always protect the logging loop:

- Do not add extra required taps before logging a set.
- Do not block logging on analytics, recommendations, network, or account state.
- Inputs should keep useful values between sets when that speeds up logging.

Respect architecture:

- Keep database calls out of composables.
- Prefer ViewModel state and repository/use-case boundaries.
- Use Flow/StateFlow rather than LiveData.
- Keep suspend calls inside coroutine scopes.
- Run database work on IO-oriented paths.

Respect product values:

- No required network calls in core logging.
- No ads, tracking, or cloud dependency.
- Keep data local by default.
- Avoid dependencies unless they clearly pay for themselves.

When adding screens:

- Add route definitions in `Screen.kt`.
- Wire navigation in `MainActivity`.
- Keep bottom navigation limited to the main four routes unless the product direction changes.

When adding analytics:

- Start with derived local data from sets/workouts/exercises.
- Make formulas explainable.
- Exclude warmups from main effort metrics.
- Prefer use cases over putting calculation logic directly in ViewModels.

When changing schema:

- Add a migration.
- Keep entity annotations and migration SQL aligned.
- Add or update DAO tests when behavior is data-critical.

## 11. Future Roadmap Notes

Fitbod-inspired Muscle Readiness and Strength Balance is a proposed future direction, not currently implemented.

Suggested v1 direction:

- Replace simple last-trained freshness with an offline `0-100` muscle readiness score.
- Base readiness on recent local working sets, recency, volume, log type, and optional RPE.
- Keep the model honest: IronLog currently has one primary target muscle per exercise, so a full anatomical avatar should wait until multi-muscle exercise mappings exist.
- Add a Balance tab toggle between set distribution and strength balance.
- Use user-relative strength scoring from local E1RM and history, not population comparisons.
- Do not auto-generate workouts in the first version. Surface insights first, then consider routine suggestions later.

Other future opportunities:

- Multi-muscle exercise contribution mappings.
- Better chart touch markers.
- More discoverable set editing.
- Timer adjustment polish.
- Expanded import/export validation.
- More instrumented DAO coverage for routines and import flows.

## 12. Repository Hygiene

This file is the master project summary and future-agent guide.

Avoid recreating duplicate project summaries, session logs, or assistant-specific planning files unless the user explicitly asks for them. Keep planning in the conversation or in issue/PR systems, not scattered project markdown.

Docs-only cleanup should not touch:

- `app/src/`
- Gradle config
- resource assets
- tests
- sample images

Source changes should be deliberate, tested, and scoped to the requested feature or fix.
