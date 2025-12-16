# Analytics Overhaul: The "Quantified Lifter" Roadmap

**Goal:** Transform the Analytics section from a simple volume viewer into a data-rich "Nerd Dashboard" that provides actionable insights into training consistency, balance, and intensity.

## ✅ Phase 1: Consistency (In Progress)
*   **Streaks & Stats Card:** [COMPLETED/PENDING MERGE] - Tracks current/longest streaks and total workouts.
*   **Next Step:** The Activity Heatmap.

---

## 📅 Phase 1.5: The Activity Heatmap (GitHub Style)
**Objective:** Visualize "showing up" over the last 365 days.

### 1. UI Component: `ActivityHeatmap`
*   **Visual:** A horizontal scrolling grid of squares (52 columns x 7 rows).
*   **Logic:**
    *   **Empty (Gray):** No workout.
    *   **Level 1 (Light Green):** Low volume (< 5,000 lbs) or short duration.
    *   **Level 2 (Medium Green):** Medium volume.
    *   **Level 3 (Neon Green):** High volume (> 20,000 lbs) or PR set.
    *   **Interaction:** Tap a square -> Toast/Tooltip showing "Oct 12: Leg Day - 14,250 lbs".
*   **Data Source:** `WorkoutDao` query returning `date` + `totalVolume` for the last year.

---

## ⚖️ Phase 2: Muscle Balance & Symmetry
**Objective:** Identify neglected muscle groups and visualize the training split.

### 1. Muscle Split Donut Chart
*   **Visual:** Ring chart showing % of total working sets per muscle group.
*   **Filters:** [Last 30 Days] vs [All Time].
*   **Insight:** Quick visual check—is Chest 50% of your training? (Don't skip legs).
*   **Library:** Use `Vico` pie/donut chart support or a custom Canvas drawing for minimal overhead.

### 2. Muscle Freshness / Frequency Tracker
*   **Visual:** Horizontal bar list of muscle groups.
*   **Metric:** "Days Since Last Trained".
*   **Color Coding:**
    *   **Green:** > 3 days (Fresh/Ready).
    *   **Yellow:** 2-3 days (Recovering).
    *   **Red:** < 24 hours (Fatigued).
*   **Data Source:** Query `WorkoutWithMuscles` to find the last occurrence of each muscle group.

---

## 📈 Phase 3: Advanced "Nerd" Trends
**Objective:** Move beyond Volume to track Intensity, Density, and Strength.

### 1. Expanded Metric Selector (Dropdown)
Current analytics only show Volume and E1RM. We will add:
*   **Intensity (Avg Weight):** Are you actually lifting heavier over time? (Formula: `Total Volume / Total Reps`).
*   **Workout Density:** Work capacity measurement. (Formula: `Total Volume / Workout Duration (mins)`).
*   **Rep Count:** Total reps (useful for hypertrophy blocks).
*   **Tonnage:** Total weight lifted (already exists, but refine presentation).

### 2. The "One Chart to Rule Them All"
Refactor the main line chart to be more powerful:
*   **Comparison Mode:** Allow overlaying two metrics (e.g., "Volume" (Left Axis) vs "E1RM" (Right Axis)).
*   **Granularity Control:** Toggle between [By Workout] / [Weekly] / [Monthly] aggregation.
    *   *Note:* "By Workout" is noisy but precise. "Weekly" is smoother.

### 3. Personal Record (PR) Milestones
*   **Visual:** Small markers/flags on the line chart when a new theoretical 1RM is established.
*   **List View:** A "Trophy Case" section showing current 1RM for the "Big 3" (Bench, Squat, Deadlift) + OHP.

---

## 🛠 Technical Implementation Guide

### A. Database Layer (`Dao` Updates)
New queries needed in `WorkoutDao` and `SetDao`:
1.  **Heatmap Data:**
    ```sql
    SELECT date(startTime/1000, 'unixepoch') as day, SUM(weight * reps) as dailyVol 
    FROM workouts ... 
    GROUP BY day
    ```
2.  **Muscle Frequency:**
    ```sql
    SELECT muscle, MAX(startTime) as lastTrained 
    FROM ... 
    GROUP BY muscle
    ```
3.  **Aggregations:** Robust queries for `AvgWeight` and `Density`.

### B. ViewModel Architecture
The current `AnalyticsViewModel` is getting heavy. We will refactor into granular State Holders or Use Cases:
*   `ConsistencyUseCase`: Fetches streaks and heatmap data.
*   `DistributionUseCase`: Fetches muscle split stats.
*   `TrendUseCase`: Handles the complex chart logic.

### C. UI Composition
Structure of the new `AnalyticsScreen`:
```kotlin
Column(Scrollable) {
    // 1. Consistency Section
    StreakStatCard()
    ActivityHeatmap() // The "Github Chart"

    // 2. Balance Section
    Text("Training Split")
    DonutChart(muscleDistribution)
    MuscleFreshnessList()

    // 3. Trends Section
    Text("Progress Analysis")
    MetricSelectorDropdown() // Volume, Intensity, Density
    MainTrendChart() // Vico chart with new modes
}
```
