# IronLog - Project Context & Development Guide

## 1. Project Identity & Current Status

**Name:** IronLog
**Type:** Offline-first, privacy-centric strength training tracker for serious lifters
**Philosophy:** "Buy Once, Own Forever" - No ads, no subscriptions, no algorithm, no social bloat
**Status:** Early MVP - Core logging loop functional, exercise selection complete, database seeding working

### Core Values

- **The Logging Loop is God**: Every decision prioritizes speed and frictionless set logging
- **Privacy First**: All data stays local (Room DB, no cloud sync)
- **Offline or Nothing**: No internet required, ever
- **User Control**: Users own their data completely, can delete exercises and workouts permanently

---

## 2. Real Architecture (What Actually Exists)

### Navigation Structure

```
MainActivity (Single Activity, No Fragments)
├── Scaffold with BottomNavigationBar
├── NavHost with 4 visible routes:
│   ├── Home (Dashboard) - Welcome, quick stats, quick start
│   ├── History - Placeholder (not yet implemented)
│   ├── Library - Redirects to ExerciseSelectionScreen
│   └── (Hidden routes accessed from above)
│       ├── ExerciseSelection - Browse/filter/select exercises
│       ├── ExerciseLogging - Log sets for selected exercise
│       └── Workout - Original workout screen (deprecated, may remove)
└── Gradient background (GradientStart → GradientEnd)
```

### MVVM + Clean Architecture

- **Activity**: MainActivity (only UI entry point)
- **ViewModels**: HomeViewModel, ExerciseSelectionViewModel, ExerciseLoggingViewModel
- **Repositories**: UserPreferencesRepository (DataStore)
- **DAOs**: ExerciseDao, WorkoutDao, SetDao, RoutineDao, MuscleGroupDao
- **State Management**: Flow/StateFlow for reactive data, ViewModel for business logic
- **Async**: Coroutines with Dispatchers.IO for database operations

### Dependency Injection

- **DI Framework**: Hilt
- **Module**: DatabaseModule (provides all DAOs and Room instance)
- **Scope**: Singletons for DB and DAOs to ensure single instance across app

---

## 3. Tech Stack (Actual)

**Language:** Kotlin (2.0+, with Kapt fallback for Hilt)
**UI Framework:** Jetpack Compose (no XML, no Fragments)
**Architecture:** MVVM with coroutines
**Database:** Room (SQLite) with TypeConverters for Date
**Local Storage:** AndroidX DataStore (for user preferences)
**Async:** Coroutines + Flow (not LiveData)
**DI:** Hilt (requires kapt plugin in build.gradle)
**Animations:** Compose animations (spring, fade, slide)
**Haptics:** Android haptic feedback for set logging

**Constraints (Hard Rules):**

- No Firebase
- No XML layouts
- No network calls in core logging features
- No ads or tracking
- Minimal dependencies

---

## 4. Completed Features

### ✅ Home Dashboard

- Welcome header with user name + split-color "IronLog" branding
- Quick Start card (full-width hero card) → starts workout or resumes
- Routine selector card (placeholder state management exists)
- Weekly volume graph (line chart, animated)
- Stats carousel (streak, volume, PRs - animated rotation)
- Recent workout card (shows most recent session)
- Personal best showcase card

### ✅ Exercise Selection Flow

- Search box (text input, no icon to reduce clutter)
- Multi-select filter pills (muscle groups from database)
- Exercise list (clean, minimal - name + muscle group)
- Long-press context menu (Edit [TODO], Delete)
- Permanent exercise deletion
- Real-time filtering based on search + selected muscles

### ✅ Exercise Logging Flow

- Exercise header (name + target muscle)
- Auto-countdown rest timer (starts at 90s, configurable)
- Weight input card (large, 48sp font for thumb-friendly input)
- Reps input card (large, 48sp font)
- Warmup toggle (checkbox)
- Log Set button (animated, haptic feedback, large hit target)
- Session log (shows all sets logged in this exercise this session)
- Add Exercise button (returns to selection, same workout session active)
- Finish Workout button (ends session, saves to database)

### ✅ Database Seeding

- 25 pre-loaded exercises (across 8 muscle groups)
- Only seeds on true first run (checks exercise count, not flags)
- Verbose logging for troubleshooting
- Exercises stay deleted forever (user intent respected)
- Muscle groups pre-seeded (Back, Biceps, Chest, Core, Legs, Shoulders, Triceps, Cardio)

### ✅ Navigation & UI Polish

- Home button clears entire back stack (returns to Dashboard from anywhere)
- Library button navigates to exercise selection
- Gradient background (dark green to black)
- GlowCard components (subtle left-edge lime green glow)
- Consistent theme throughout (lime green #A3E635 accents on dark background)
- Bottom navigation bar highlights active tab

---

## 5. Database Schema (Current, Room SQLite)

### Entities in Use

**Exercise**

```kotlin
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                           // e.g., "Barbell Bench Press"
    val targetMuscle: String,                   // e.g., "Chest"
    val logType: LogType,                       // WEIGHT_REPS, REPS_ONLY, DURATION, WEIGHT_DISTANCE
    val isCustom: Boolean,                      // false for seed, true for user-created
    val notes: String?,
    val defaultRestSeconds: Int                 // Default rest between sets
)
```

**Workout** (Represents a training session)

```kotlin
@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Date,
    val endTime: Date?,                         // null = in-progress
    val name: String?,                          // Optional workout name
    val note: String?
)
```

**Set** (Core data unit - individual set logged)

```kotlin
@Entity(
    tableName = "sets",
    foreignKeys = [ForeignKey(Workout::class, ...), ForeignKey(Exercise::class, ...)],
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class Set(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val weight: Float?,                         // Nullable per LogType
    val reps: Int?,
    val rpe: Float?,                            // Rate of Perceived Exertion
    val durationSeconds: Int?,
    val distanceMeters: Float?,
    val isWarmup: Boolean,                      // CRITICAL: excludes from volume stats
    val isComplete: Boolean,
    val timestamp: Date
)
```

**Routine** (Template for pre-planned workouts)

```kotlin
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
```

**RoutineExercise** (Junction table: many-to-many between Routine and Exercise)

```kotlin
@Entity(
    tableName = "routine_exercises",
    primaryKeys = ["routineId", "exerciseId"],
    foreignKeys = [...]
)
data class RoutineExercise(
    val routineId: Long,
    val exerciseId: Long,
    val orderIndex: Int                         // Order within routine
)
```

**MuscleGroup** (NEW - Organizes exercises by muscle)

```kotlin
@Entity(tableName = "muscle_groups")
data class MuscleGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String                            // e.g., "Chest", "Back", "Legs"
)
```

### Enums

```kotlin
enum class LogType {
    WEIGHT_REPS,        // Barbell exercises (weight + reps)
    REPS_ONLY,          // Calisthenics (just reps)
    DURATION,           // Cardio/time-based (running, plank, etc)
    WEIGHT_DISTANCE     // Weighted cardio (sled push, etc)
}
```

### Known Indices

- `sets.workoutId` - Fast history lookups per session
- `sets.exerciseId` - Fast history lookups per exercise
- These are critical for performance when displaying past sets

---

## 6. UI/UX Implementation (Actual)

### Color Palette (In Use)

```
Primary Accent (Lime Green):
  - #A3E635 (Main)      - Key actions, text highlights, active tabs
  - #84CC16 (Dark)      - Pressed states, variation
  - #BEF264 (Light)     - Hover effects, highlights

Background:
  - #121212 (Canvas)    - Main background
  - #0D0D0D (Surface)   - Card backgrounds
  - #0A1A0A (Gradient Start) - Dark green tint
  - #0A0A0A (Gradient End)   - Very dark black

Text:
  - #FFFFFF (Primary)   - Main text
  - #E0E0E0 (Secondary) - Less important text
  - #9CA3AF (Tertiary)  - Labels, hints, disabled

Status Indicators:
  - #2ECC71 (Fresh)     - Muscle heat map "fresh" state
  - #E74C3C (Fatigued)  - Muscle heat map "fatigued" state
```

### Typography (Current)

- **Font Family**: Mostly `FontFamily.Default` (system sans-serif)
- **Bebas Neue**: Planned for workout names (not yet implemented)
- **Font Weights**: Bold, Medium, Normal, ExtraBold for hierarchy
- **Letter Spacing**: 1.5sp - 2sp for labels (modern aesthetic)
- **Split-Color Branding**: "Iron" in white, "Log" in lime green

### Component Library (Reusable)

- **GlowCard**: Custom card with subtle left-edge lime green gradient glow

  - Rounded corners (16dp)
  - Elevation (8dp)
  - Background (#0D0D0D)
  - Now supports `onLongClick` for context menus

- **Input Cards**: Large weight/reps input (48sp font)

  - Transparent background, white text
  - Clear keyboard, numeric input
  - 140dp height (easy thumb access)

- **Filter Chips**: Material3 FilterChip

  - Selected: lime green background, black text
  - Unselected: dark surface, lime border

- **Status Tags**: Uppercase labels with letter spacing
  - Examples: "EMPTY SESSION", "IN PROGRESS", "WARMUP"

### Layout Principles (Applied)

- **Hero Cards**: Full-width for primary actions (Start Workout)
- **Two-Column**: Side-by-side cards for stats (Routine + Volume)
- **Minimal Scrolling**: Show essentials, hide secondary info
- **Breathing Room**: Plenty of vertical spacing, less is more
- **One-Handed Use**: All touch targets in thumb-reachable zone (bottom 2/3 of screen)

---

## 7. Development Workflow (How We Work)

### Process for Every Coding Task

1. **Plan First**: Before writing any code

   - Identify files that need changes
   - Sketch implementation approach
   - Consider edge cases and gotchas

2. **Ask Clarifying Questions**: If ambiguities exist

   - Design decisions
   - Feature scope
   - Trade-offs to confirm

3. **Present Plan to User**: Show both questions + proposed approach

   - Get explicit approval before proceeding
   - Adjust based on feedback

4. **Execute**: Only after approval

   - Write code following standards below
   - Test compilation
   - Verify functionality

5. **Code Review**: For complex changes
   - Use code-reviewer agent
   - Fix issues found
   - Document non-obvious decisions

### Code Standards

- **Compose**: Stateless composables, hoist state to ViewModel
- **Previews**: Always include `@Preview(showBackground = true)` for UI components
- **Error Handling**: Never block UI thread, show loaders if DB is slow
- **Logging**: Use `Log.d(TAG, "message")` for debugging (will be removed before release)
- **Naming**: Clear, descriptive names (no abbreviations except common ones like DAO, VM)
- **Scope Functions**: Use Kotlin idioms (let, apply, run, with, also)
- **Null Safety**: Leverage Kotlin's null safety (use `?:`, `?.`, not null checks)
- **Architecture**: Follow MVVM, never put DB calls in UI layer

### Git Workflow

- **NEVER merge into main without explicit user approval.**
- **NEVER make any code changes without making a branch first.**
- All features on `main` (no feature branches currently)
- Commit messages: descriptive, start with verb (Feat:, Fix:, Refactor:, etc)
- Always include Co-Authored-By footer with Claude credit
- Push to GitHub after approval

### Known Issues & Workarounds

- **Kapt Warning**: "Kapt currently doesn't support language version 2.0+" - Harmless, Hilt still works
- **Database Seeding**: First run requires uninstall/reinstall to trigger seed. After that, exercises persist.
- **RecentHistory Deleted**: Was removed to simplify navigation, could be added back if needed
- **WorkoutScreen Deprecated**: Old workout logging screen still exists but not used. Keep for now, may refactor later.

---

## 8. Roadmap (What's Coming)

### Immediate (Next Sessions)

- [ ] Test exercise seeding on fresh install (uninstall/reinstall app)
- [ ] Verify exercises appear in selection screen
- [ ] Test "Add Exercise" flow (same workout, multiple exercises)
- [ ] Test "Finish Workout" flow (saves session)
- [ ] Test home button navigation (from all screens)
- [ ] Implement History screen (show past workouts, re-run features)
- [ ] Add exercise edit functionality (long-press → Edit)

### Short Term (Foundation)

#### Gamification Roadmap (Approved Strategy - Starting Nov 25)

**v0.2.0 (Dec 15, 2025) - Hero Features**

- [ ] **Volume Orb** (per-muscle-group weekly progress, visual fill from 0-100%+)

  - Animated circular progress indicator (lime green → gold)
  - Real-time update as sets logged (weight × reps)
  - Caps at 120% visually (prevents obsessive volume chasing)
  - Resets weekly (Monday 12am)
  - Baseline = rolling 4-week average of volume per muscle
  - Freemium gate: Free users see 3 muscle groups, Premium sees all 8
  - Lives on: Exercise Logging Screen (top-center), Home Dashboard (8 mini-orbs), Post-Workout Summary
  - **Celebration**: Gold glow + particle effects when hitting 100%+, haptic feedback
  - **Estimated effort**: 4-5 weeks (5 sprints), 2-3 developers

- [ ] **PR Detection** (animated badge on new personal records)

  - Algorithm: 1RM estimation (weight × reps × 0.0333 + weight)
  - Animated popup: "New PR! 225 lbs x 8"
  - Gold star icon on PR sets in session log
  - Haptic feedback on detection

- [ ] **Post-Workout Summary Screen**
  - Final orb states for all trained muscles
  - Volume comparison ("12% more than last week")
  - PR callouts ("2 new personal records!")
  - Text export/share button (no social integration)

**v0.3.0 (Jan 15, 2026) - Set-Level Engagement**

- [ ] **Ghost Reps** (show previous best set for comparison)

  - Faded text on input screens: "Last time: 185 lbs × 10"
  - Beat your ghost: green checkmark if current > previous
  - Historical cycling: toggle through last 5 sessions

- [ ] **Session Comparison Card**
  - "8% more volume than last chest day"
  - Visual improvement trending

**v0.4.0 (Mar 1, 2026) - Pre-Workout Planning**

- [ ] **Muscle Readiness Heatmap** (recovery tracking)
  - Color-coded muscle groups: Green (fresh), Yellow (moderate), Red (fatigued)
  - Algorithm: days since last trained + volume intensity
  - Tap muscle: "Ready to train in 18 hours"
  - Pre-session planner: suggested fresh muscle groups

**v0.5.0 (May 1, 2026) - Long-Term Motivation**

- [ ] **Tonnage Milestones** (lifetime achievements)
  - "100,000 lbs lifted", "1 Million Pounds Club", "Iron Titan (10M lbs)"
  - Progress bar: "847,392 / 1,000,000 lbs to Iron Lord"
  - Unlockable badges (no social sharing)

#### Other Foundation Features

- [ ] One Rep Max (1RM) estimation from logged sets
- [ ] Personal records (PRs) detection and tracking
- [ ] Volume calculations (total pounds lifted per muscle per week)
- [ ] Rest day tracking and muscle freshness heatmap
- [ ] Routine management (create/edit/follow routines)
- [ ] Pre-set rest timers (auto-adjust based on weight/reps)

### Medium Term (Analytics)

- [ ] Progress graphs (volume trends, strength curves)
- [ ] Workout performance metrics (RPE, volume per session)
- [ ] Body part heat maps (freshness/fatigue by muscle)
- [ ] Data export (CSV, PDF for sharing or spreadsheet analysis)
- [ ] Backup/restore (local JSON export to Files app)

### Long Term (Premium / Future)

- [ ] Cloud sync option (opt-in, encrypted)
- [ ] Freemium model (free logging, premium = advanced analytics)
- [ ] Exercise library expansion (images, form cues, alternatives)
- [ ] Social features (optional, off by default: share PRs only)
- [ ] Wearable integration (heart rate, GPS for cardio)

### NOT In Scope (Per Project Philosophy)

- Real-time social feed
- Algorithmic recommendations
- Ads or tracking
- Cloud-only features
- Complex authentication

---

## 9. Friction Points & TODOs

### Unresolved Issues

1. **Exercise Images/Videos**: Need form guides and alternatives

   - Current: Text-only exercise library
   - Solution: Add media URLs, display in exercise detail screen

2. **Freemium Logic**: Not yet implemented

   - Current: All features available
   - Solution: Add feature gating in relevant screens (advanced stats, unlimited routines)

3. **Rest Timer Customization**: Currently hardcoded to 90s

   - Current: +/-15s manual adjustment
   - Solution: Let users set per-exercise default rest, learn from history

4. **No Routine Editing**: Routines exist in DB but no UI to create/modify them

   - Current: Routine cards are decorative
   - Solution: Add routine builder screen, quick-start from template

5. **History Screen Incomplete**: Placeholder only

   - Current: Shows "History Screen" text
   - Solution: Implement past workout list, replay/re-run features

6. **No Offline Indicator**: App works offline but users don't know
   - Current: No visual indicator
   - Solution: Add subtle indicator that data is local-only

### Technical Debt

1. **WorkoutScreen**: Old workout logging screen still in code, unused

   - Action: Keep for now (might refactor), but don't add features

2. **Logging**: Scattered `Log.d()` calls for debugging

   - Action: Will clean up before release, useful for now while testing

3. **TypeConverters**: Room Date converter is minimal

   - Action: Fine as-is, may add custom time zone handling later

4. **Database Queries**: No complex queries yet
   - Action: Will add aggregations (sum weights, count sets) when stats are built

---

## 10. Instructions for Claude (How to Work on This Project)

### Before You Code

1. **Check CLAUDE.md First**: This file is your source of truth
2. **Understand Current State**: We're in early MVP phase, focused on core logging loop
3. **Respect the Philosophy**: Speed and offline-first are non-negotiable
4. **Ask Questions**: If anything in this file is outdated or unclear, ask

### When Implementing Features

1. **Prioritize the Logging Loop**: Any new feature must not slow down set logging
2. **Keep It Simple**: No premature optimization, but also no unnecessary complexity
3. **Test on Device**: Emulator is fine, but verify on real device if possible
4. **Use Existing Patterns**: Look at ExerciseLogging/ExerciseSelection as examples
5. **Follow MVVM**: UI ← ViewModel ← Repository/DAO ← Database (unidirectional)

### When Fixing Bugs

1. **Reproduce First**: Make sure you understand the issue
2. **Check Logs**: Use Logcat, look for `DatabaseModule`, `ExerciseSelectionScreen`, etc.
3. **Add Logging**: If unclear, add debug logs (we'll remove later)
4. **Test Thoroughly**: Including edge cases (empty list, first run, etc)

### Code Style Quick Reference

```kotlin
// ✅ Good: Stateless composable, clear names
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

// ❌ Bad: State in composable, vague names
@Composable
fun Item(e: Exercise, fn: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }  // No, hoist to VM
    // ...
}
```

### Common Pitfalls to Avoid

1. **Don't call suspend functions without launch**: Use `viewModelScope.launch { }`
2. **Don't use LiveData**: We use Flow/StateFlow
3. **Don't block the UI thread**: Database operations go to Dispatchers.IO
4. **Don't hardcode strings**: Use string resources (setup later if needed)
5. **Don't add screens without updating navigation**: Always update Screen.kt + MainActivity
6. **Don't forget indices**: If querying by ID, make sure index exists

### When Asking Questions

- Include what you've tried
- Share relevant code snippets
- Mention any logs or errors
- Describe expected vs actual behavior

### When Stuck

1. Check existing implementation patterns (ExerciseLogging, ExerciseSelection)
2. Look at Logcat for errors or unexpected behavior
3. Review CLAUDE.md for constraints/patterns
4. Ask for clarification if something is ambiguous

---

## 11. Project Stats & Health

### Codebase Size

- ~4,500 lines of Kotlin
- ~50 Compose screens/components
- ~10 ViewModels
- ~8 Database tables/entities
- ~2 MB compiled APK (debug)

### Test Coverage

- No unit tests yet (add as features stabilize)
- Manual testing on emulator/device
- Build always passes

### Dependency Count

- Minimal (Compose, Room, Hilt, Coroutines, AndroidX)
- No external analytics, tracking, or ads libraries
- No Firebase

### Performance Baseline

- App loads in <2 seconds
- Exercise list renders instantly (lazy column)
- Database queries <100ms (seed of 25 exercises)
- Set logging button response immediate

---

## 12. Contact & Collaboration

- **Repo**: https://github.com/ricojamie/GymTime
- **Main Branch**: Always deployable
- **Latest Commit**: Always has working build
- **Issue Tracking**: TBD (use GitHub issues when ready)

---

**Last Updated**: November 20, 2024
**Current Phase**: Early MVP - Core logging loop complete, exercise selection functional
**Next Step**: Test on real device, verify database seeding, implement History screen
