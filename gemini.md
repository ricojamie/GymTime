# Project Context: IronLog (Gym Workout Tracker)

## 1. Project Identity
**Name:** IronLog (Working Title)
**Type:** Offline-first, privacy-centric strength tracker for serious lifters.
**Philosophy:** "Buy Once, Own Forever." No ads, no subscriptions (optional LTP).
**Core Constraints:**
* **Offline-First or Die:** No internet required. All data stays local (Room DB).
* **The Logging Loop is God:** The UI for logging a set must be frictionless. Speed > Fancy Animations.
* **No Social Bloat:** No feeds, no sharing, no algorithmic recommendations.

## 2. Tech Stack (Strict)
* **Language:** Kotlin
* **UI:** Jetpack Compose (Single Activity, No Fragments, No XML).
* **Architecture:** MVVM with Clean Architecture principles.
* **DI:** Hilt.
* **Local Data:** Room Database (SQLite).
* **Async:** Coroutines & Flow.
* **Charts:** Vico or MPAndroidChart.

## 3. Key Features & Rules
* **Timer:** Auto-starts upon checking a set.
* **Monetization:** Freemium. Free tier = unlimited logging. Premium = Advanced stats/Unlimited routines. Code must handle feature gating logic cleanly.

## 4. Database Schema (Room - Source of Truth)
*Do not hallucinate new tables. Use this structure.*

**Enums:**
* `LogType`: `WEIGHT_REPS`, `REPS_ONLY`, `DURATION`, `WEIGHT_DISTANCE`.

**Entities:**
1. **`Exercise`**
   * `id` (PK), `name`, `targetMuscle`, `logType` (Enum), `isCustom` (Boolean), `notes`, `defaultRestSeconds`.
2. **`Workout`** (Represents a session)
   * `id` (PK), `startTime`, `endTime` (Nullable - null means in-progress), `name` (Optional), `note`.
3. **`Set`** (The core data unit)
   * **Foreign Keys:** `workoutId`, `exerciseId`.
   * **Indices:** Index `workoutId` and `exerciseId` for fast history lookups.
   * **Data Fields (Nullable based on LogType):** `weight`, `reps`, `rpe`, `durationSeconds`, `distanceMeters`.
   * **Meta:** `isWarmup` (Boolean - critical for excluding from Volume stats), `isComplete` (Boolean), `timestamp`.
4. **`Routine`** (Templates)
   * `id` (PK), `name`.
5. **`RoutineExercise`** (Junction Table for Many-to-Many)
   * `routineId`, `exerciseId`, `orderIndex`.

## 5. UI/UX Guidelines

*   **Color Palette:**
    *   `Primary/Accent (Deep Amethyst)`: `#8B48F7` - Vibrant, saturated purple for key actions (e.g., "Log Set" button, timer overlay).
    *   `Background (Almost Black)`: `#121212` - Standard dark theme compliant, high contrast, minimizes eye strain.
    *   `Surface (Slightly Lighter)`: `#1E1E1E` - For cards and containers, provides depth.
    *   `Text (Near White)`: `#FFFFFF` or `#E0E0E0` - High contrast, easy to read.
    *   `Success/Fresh (Emerald Green)`: `#2ECC71` - For "Fresh" status on Muscle Heat Map.
    *   `Warning/Fatigued (Alizarin Red)`: `#E74C3C` - For "Fatigued" status on Muscle Heat Map.
    *   `Gradient Start`: `#1A0033` - Used in background gradient.

*   **Typography:**
    *   **Font System:** Two-font system.
        *   `Inter`: Used for all general text content.
        *   `Bebas Neue`: Used specifically for workout names.
    *   **Hierarchy:** Achieved through font weight variations (e.g., `FontWeight.Bold`, `FontWeight.Medium`) rather than different font families.
    *   **Letter Spacing:** Slight increase to letter spacing for easier scanning during sets.

*   **Component Styling:**
    *   **Buttons:** Generally square with subtle shadows.
    *   **Dates:** Use relative date language (e.g., "Today", "Yesterday", "Monday") for recent history entries.
    *   **Bottom Navigation:** Active tabs should be highlighted with `PrimaryAccent`.

*   **Layout Principles:**
    *   Prioritize single-page views where possible, avoid excessive scrolling.

## 6. Coding Standards
* **Compose:** Use `Stateless` composables where possible. Hoist state to the ViewModel.
* **Previews:** Always provide a `@Preview` with `showBackground = true` for UI components.
* **Error Handling:** Fail gracefully. If the DB is slow, show a skeleton loader, but never block the UI thread.
* **Hard Rules:**
    * Do NOT suggest Firebase or Network calls for core logging features.
    * Do NOT suggest XML layouts.
    * Keep dependencies minimal.