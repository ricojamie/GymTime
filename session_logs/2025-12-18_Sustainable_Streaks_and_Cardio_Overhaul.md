# 2025-12-18 - Sustainable Streaks and Cardio Overhaul

**Duration:** 2 hours
**Status:** Completed
**Branch:** feature/sustainable-streaks-cardio (merged to main)

---

## 🎯 Session Objective
Implement a more sustainable "Iron Streak" system with calendar-week resets and skips, and overhaul the cardio logging experience with miles support and an intuitive HH:MM:SS input.

---

## ✅ Completed Work

### Major Features
- **Sustainable Iron Streak** - Shifted from rolling 7-day windows to a "Calendar Week" (Sunday-start) system.
  - 2 free skips allowed per calendar week (Sun-Sat).
  - Skips reset to 2 every Sunday morning.
  - Streak continues as long as misses in a week <= 2.
  - Fresh install fix: Streaks now start from the day of the first recorded workout.
  - Key files: `StreakCalculator.kt`, `HomeViewModel.kt`, `HomeScreen.kt`
- **Cardio Overhaul (Miles & Time)** - Transitioned cardio tracking from metric/seconds to imperial/hh:mm:ss.
  - Database maintains meters and seconds for consistency, while UI presents miles and HH:MM:SS.
  - New `TimeUtils` for centralized conversions.
  - Key files: `TimeUtils.kt`, `ExerciseLoggingViewModel.kt`, `ExerciseLoggingScreen.kt`
- **Segmented "Tape" Time Input** - Implemented a high-performance 3-segment (HH:MM:SS) input with auto-focus jumping and smart backspace logic.
  - Prevents invalid time entry (MM/SS capped at 59).
  - High speed, zero-friction entry for cardio sessions.
  - Key files: `ExerciseLoggingScreen.kt`
- **Iron Streak Overview Modal** - Redesigned the home screen streak box and added a detailed summary modal.
  - Displays Current Streak, All-Time Best, YTD Workouts, and YTD Volume (Total Weight Lifted).
  - Visual skip indicators (blue lit circles) show remaining weekly skips.
  - Key files: `HomeScreen.kt`, `HomeViewModel.kt`

---

## 📂 Files Changed

| File | Changes | Lines Modified |
|------|---------|----------------|
| `StreakCalculator.kt` | New calendar-week logic with skip support | [+105 / -105] |
| `ExerciseLoggingScreen.kt` | Segmented time input UI and miles labels | [+216 / -15] |
| `ExerciseLoggingViewModel.kt` | HMS/Miles conversion and YTD volume support | [+45 / -15] |
| `HomeScreen.kt` | Redesigned streak box and new Detail Modal | [+269 / -35] |
| `HomeViewModel.kt` | Added YTD volume and updated streak loading | [+24 / -10] |
| `TimeUtils.kt` | New utility for HMS and Miles/Meters conversion | [+68 / -0] |

---

## 🧪 Testing Status

- [x] Manual testing completed
- [x] Build successful (`./gradlew assembleDebug`)
- [x] Key workflows verified:
  - [x] ATM-style Segmented Time entry (auto-focus jump)
  - [x] Miles to Meters conversion for DB persistence
  - [x] Streak calculation starting from first workout
  - [x] YTD Stats in Streak Modal

---

## 💡 Key Decisions Made

1. **Database Consistency**
   - **Choice:** Keep distance in Meters and time in Seconds in the DB.
   - **Rationale:** Maintains a standardized "source of truth" while allowing the UI to adapt to the user's preferred units (Imperial).

2. **Time Input Strategy**
   - **Options Considered:** ATM-style (right-to-left), Slider, Segmented Input.
   - **Choice:** Segmented "Jump-to-Next" Input.
   - **Rationale:** Most intuitive for error correction and fastest for typing without needing colons.

---

## 📊 Project Health

**Codebase Stats:**
- Total files modified this session: 6
- Build time: 36-53 seconds
- Compilation errors: 0 (fixed method name mismatch and missing imports)

**Architecture Status:**
- ✅ MVVM pattern maintained
- ✅ Conversion logic centralized in `TimeUtils`
- ✅ State management via Flow/StateFlow

---

## 🔗 Related Resources

- Commit: `f136357`
- PR: N/A
- Related Sessions: `2025-12-16_Iron_Streak_Responsive_Home.md`
