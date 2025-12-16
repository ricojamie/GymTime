# Plan: Routines System Overhaul

## 🛑 The Problem
Currently, "Routines" in IronLog are structurally sound (Database has `Routine` -> `Day` -> `Exercise`) but functionally disconnected.
*   **Gap 1:** You can't easily "Save this workout as a routine" from history.
*   **Gap 2:** Starting a workout doesn't clearly offer "Pick from Routine" as a primary path.
*   **Gap 3:** Editing routines is disjointed from the workout experience.

## 🎯 The Goal
Make Routines the "First Class Citizen" for starting a workout. Users should be able to:
1.  **Create** a routine from scratch OR from a past good workout.
2.  **Organize** routines by days (Push, Pull, Legs).
3.  **Start** a workout pre-filled with their routine's exercises.

---

## 🛠 Phase 1: "Save As Routine" (The Quick Win)
**Why:** Most users lift first, plan later. Let them turn a good session into a template.

### 1. UI: Workout History Action
*   **Location:** `WorkoutDetailsSheet` (The bottom sheet when you tap a history item).
*   **Action:** Add a "Save as Routine" button next to/below the title.
*   **Flow:**
    1.  Tap "Save as Routine".
    2.  Dialog pops up: "Name this Routine" (Default: "Chest Day").
    3.  **Backend Logic:**
        *   Create new `Routine` entity.
        *   Create `RoutineDay` (e.g., "Day 1").
        *   Copy all `Exercises` from that workout into `RoutineExercise` entries.
    4.  Toast: "Routine Saved".

---

## 🏗 Phase 2: The "Start Workout" Hub
**Why:** The "Quick Start" button is too generic. We need a "Menu" to choose *what* to lift.

### 1. Redesign `Home/Dashboard`
*   Replace the single "Quick Start" card with a **"Start Workout" Section**.
*   **Tabs / Toggle:**
    *   **"Empty"**: (Current Quick Start) - good for freestyle.
    *   **"My Routines"**: Horizontal scroll of your saved Routines (e.g., "PPL - Push", "Upper Body A").

### 2. Routine Card Interaction
*   **Visual:** Card showing Routine Name + List of Muscle Groups involved.
*   **Action:** Tap "Start".
*   **Result:**
    *   Navigates to `ExerciseLoggingScreen`.
    *   **CRITICAL:** Pre-fills the "Active Workout" list with the routine's exercises (in order).
    *   **No Sets Logged Yet:** It just populates the "ToDo list" of exercises.

---

## 📝 Phase 3: Routine Management (CRUD)
**Why:** Users need to tweak their program without lifting.

### 1. New Screen: `RoutineDetailScreen`
*   **Entry:** Click "Edit" on a routine card in the Library/Settings.
*   **Features:**
    *   **Rename Routine.**
    *   **Reorder Exercises:** Drag-and-drop list (using standard Compose reordering).
    *   **Add/Remove Exercises:** Reuse the existing `ExerciseSelectionScreen` but in "Selection Mode" (returns ID list instead of starting workout).

---

## 🧩 Technical Implementation Details

### 1. `WorkoutDao` & `RoutineDao`
*   Need a mapping function: `fun createRoutineFromWorkout(workoutId: Long, name: String)`
    *   This is complex logic; best placed in a `RoutineRepository`.

### 2. `ExerciseLoggingViewModel` (The "Pre-fill" Logic)
*   **Current:** Starts empty.
*   **New:** `init(routineId: Long?)`
    *   If `routineId` is provided:
        1.  Fetch `RoutineExercises` for that routine.
        2.  Populate `state.activeExercises` list.
        3.  Do **not** log any sets yet.

### 3. Database Schema Check
*   Your current schema (`Routine` -> `RoutineDay` -> `RoutineExercise`) is actually **too complex** for a simple MVP.
*   **Simplification Recommendation:** If you only support "Single Day Routines" (e.g., "Leg Day"), you might merge `Routine` and `RoutineDay` concepts for the UI, or just auto-create a default "Day 1" for every routine to hide the complexity from the user.

---

## 🚀 Execution Order
1.  **Backend:** Implement `RoutineRepository.createFromWorkout()`.
2.  **UI:** Add "Save as Routine" button to History.
3.  **UI:** Update Dashboard to show/start Routines.
4.  **Backend:** Update `ExerciseLoggingViewModel` to accept a list of exercises on init.
