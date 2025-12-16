# Implementation Plan: Streaks, History Metrics, and Navigation Refinement

**Date:** December 16, 2025  
**Project:** IronLog (GymTime)  
**Status:** Planning Phase

This document outlines the architectural and logic changes required to implement three key improvements: seamless in-workout navigation, enhanced history metrics, and the "Iron Streak" consistency system.

---

## 1. Feature: "Iron Streak" (Consistency Tracker)

### Philosophy

To avoid the toxic patterns of traditional daily streaks, IronLog uses a **Consistency Streak**. The goal is sustainable habit building, not obsession.

**The Rule:** A user maintains their streak as long as they do not miss more than **2 days** in any rolling **7-day window**.

### Logic & Algorithm

We will implement a `StreakCalculator` utility that processes workout history to determine the current state.

**State Definitions:**

1.  **Active (🔥):** The user has worked out today.
    - _Calculation:_ `Streak = Previous Streak + 1`
2.  **Resting/Frozen (_blue flame emoji_):** The user has NOT worked out today, but is within the safe "miss limit".
    - _Calculation:_ `Streak = Previous Streak` (Number remains unchanged).
    - _Condition:_ In the window `[Today-6, Today]`, total missed days ≤ 2.
3.  **Broken (💀):** The user has NOT worked out today and has exceeded the safe limit.
    - _Calculation:_ `Streak = 0`
    - _Condition:_ In the window `[Today-6, Today]`, total missed days > 2.

**Data Source:**

- Query all distinct `startTime` dates from the `workouts` table where at least 1 set was logged.

### UI Implementation (Home Screen)

A new **Consistency Card** will replace or sit alongside the Weekly Volume card.

- **Visuals:**
  - **Icon:** Flame (Animated/Glow).
  - **Colors:**
    - Orange/Lime: Active (Workout done).
    - Ice Blue: Resting (Frozen).
    - Red: Critical (One miss away from break).
- **Information:**
  - **Streak Count:** Large text (e.g., "12 Days").
  - **Rest Bank:** Small indicators showing remaining "safe misses" for the week (e.g., "1 Rest Day Available").

---

## 2. Enhancement: Enhanced Workout History

### Goal

Users should be able to judge the intensity of past workouts at a glance without opening the details view.

### Database Changes (`WorkoutDao`)

The existing `getWorkoutsWithMuscles` query needs to be upgraded or a new query created to perform aggregation.

**New Query Requirements:**

- **Join:** Workouts -> Sets.
- **Aggregation 1 (Volume):** `SUM(weight * reps)` where `isWarmup = false`.
- **Aggregation 2 (Sets):** `COUNT(id)` where `isWarmup = false`.

### UI Changes (`HistoryScreen`)

Update the `WorkoutCard` component to include a metrics row below the date.

- **Layout:** Row with two data points.
- **Point 1:** 🏋️ **Volume:** e.g., "12,450 lbs" (Formatted with commas).
- **Point 2:** 🔢 **Sets:** e.g., "18 Sets" (Working sets only).
- **Design:** Use `TextTertiary` for labels and `TextPrimary` for values to maintain visual hierarchy.

---

## 3. UX Fix: Navigation Loop (Smart Exercise Switching)

### Problem

Currently, when a user is in `ExerciseLoggingScreen` and opens the "Current Workout" bottom sheet to switch exercises, the app pushes a _new_ `ExerciseLoggingScreen` onto the stack.

- _Result:_ Pressing "Back" returns to the _previous_ exercise, not the Home screen.
- _Stack:_ Home -> Bench Press -> Squat -> Bench Press...

### Solution: `popUpTo` Navigation

We will modify the navigation logic in `ExerciseLoggingScreen` (specifically the callback passed to the bottom sheet).

**Logic:**
When switching from Exercise A to Exercise B:

1.  Identify the current route (`ExerciseLogging`).
2.  Call `navController.navigate(Exercise_B_Route)`.
3.  Add `popUpTo(Exercise_A_Route) { inclusive = true }`.

**Result:**
The back stack remains clean: `Home -> Current Exercise`. Pressing "Back" always exits the workout view to the dashboard (or Selection screen), which matches user mental models.

---

## Summary of Work

1.  **Create:** `StreakCalculator.kt` (Logic unit).
2.  **Update:** `WorkoutDao.kt` (New SQL query for stats).
3.  **Update:** `HomeViewModel.kt` (Integrate streak logic).
4.  **Update:** `HomeScreen.kt` (Add Streak Card).
5.  **Update:** `HistoryScreen.kt` (Add Volume/Sets to cards).
6.  **Update:** `ExerciseLoggingScreen.kt` (Fix navigation callbacks).
