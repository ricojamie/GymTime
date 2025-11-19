# Project Context: IronLog (Gym Workout Tracker)

## 1. Project Identity

**Name:** IronLog (Working Title)
**Type:** Offline-first, privacy-centric strength tracker for serious lifters.
**Philosophy:** "Buy Once, Own Forever." No ads, no subscriptions (optional LTP).
**Core Constraints:**

- **Offline-First or Die:** No internet required. All data stays local (Room DB).
- **The Logging Loop is God:** The UI for logging a set must be frictionless. Speed > Fancy Animations.
- **No Social Bloat:** No feeds, no sharing, no algorithmic recommendations.

## 2. Tech Stack (Strict)

- **Language:** Kotlin
- **UI:** Jetpack Compose (Single Activity, No Fragments, No XML).
- **Architecture:** MVVM with Clean Architecture principles.
- **DI:** Hilt.
- **Local Data:** Room Database (SQLite).
- **Async:** Coroutines & Flow.
- **Charts:** Vico or MPAndroidChart.

## 3. Key Features & Rules

- **Timer:** Auto-starts upon checking a set.
- **Monetization:** Freemium. Free tier = unlimited logging. Premium = Advanced stats/Unlimited routines. Code must handle feature gating logic cleanly.

## 4. Database Schema (Room - Source of Truth)

_Do not hallucinate new tables. Use this structure._

**Enums:**

- `LogType`: `WEIGHT_REPS`, `REPS_ONLY`, `DURATION`, `WEIGHT_DISTANCE`.

**Entities:**

1. **`Exercise`**
   - `id` (PK), `name`, `targetMuscle`, `logType` (Enum), `isCustom` (Boolean), `notes`, `defaultRestSeconds`.
2. **`Workout`** (Represents a session)
   - `id` (PK), `startTime`, `endTime` (Nullable - null means in-progress), `name` (Optional), `note`.
3. **`Set`** (The core data unit)
   - **Foreign Keys:** `workoutId`, `exerciseId`.
   - **Indices:** Index `workoutId` and `exerciseId` for fast history lookups.
   - **Data Fields (Nullable based on LogType):** `weight`, `reps`, `rpe`, `durationSeconds`, `distanceMeters`.
   - **Meta:** `isWarmup` (Boolean - critical for excluding from Volume stats), `isComplete` (Boolean), `timestamp`.
4. **`Routine`** (Templates)
   - `id` (PK), `name`.
5. **`RoutineExercise`** (Junction Table for Many-to-Many)
   - `routineId`, `exerciseId`, `orderIndex`.

## 5. UI/UX Guidelines

- **Color Palette:**

  - `Primary/Accent (Lime Green)`: `#A3E635` - Modern, energetic lime green for key actions, text highlights, and icons. Conveys growth, progress, and vitality.
  - `Primary Accent Dark`: `#84CC16` - Darker shade for pressed states and variation.
  - `Primary Accent Light`: `#BEF264` - Lighter shade for highlights and hover effects.
  - `Background (Almost Black)`: `#121212` - Standard dark theme compliant, high contrast, minimizes eye strain.
  - `Surface (Very Dark)`: `#0D0D0D` - For cards and containers, almost black for premium feel.
  - `Gradient Start`: `#0A1A0A` - Dark green tint for background gradient (top).
  - `Gradient End`: `#0A0A0A` - Very dark black for background gradient (bottom).
  - `Card Border Glow`: 1dp horizontal gradient border from lime green (20% alpha) fading to transparent over 400px. Very subtle left-edge glow, not a full card gradient. Cards are nearly black (`#0D0D0D`) with minimal green accent.
  - `Text (Near White)`: `#FFFFFF` or `#E0E0E0` - High contrast, easy to read.
  - `Text Tertiary (Muted)`: `#9CA3AF` - For labels, subtitles, and secondary information.
  - `Success/Fresh (Emerald Green)`: `#2ECC71` - For "Fresh" status on Muscle Heat Map.
  - `Warning/Fatigued (Alizarin Red)`: `#E74C3C` - For "Fatigued" status on Muscle Heat Map.

- **Typography:**

  - **Font System:** Two-font system.
    - `Inter`: Used for all general text content.
    - `Bebas Neue`: Used specifically for workout names.
  - **Hierarchy:** Achieved through font weight variations (e.g., `FontWeight.Bold`, `FontWeight.Medium`) rather than different font families.
  - **Letter Spacing:** Increased letter spacing (2sp for labels) for better readability and modern aesthetic.
  - **Split-Color Branding**: App name "IronLog" uses white for "Iron" and lime green for "Log".

- **Component Styling:**

  - **Cards:** Rounded corners (16dp), **very dark** (`#0D0D0D`) with subtle left-edge green glow (1dp border, 20% alpha, fades over 400px). Elevation (8dp). Premium, understated aesthetic - nearly black with minimal accent.
  - **Gradient Cards:** Use `GradientCard` and `PrimaryGradientCard` composables for consistent styling.
  - **Hero Cards:** Full-width, prominent cards for primary actions (e.g., "Start Workout").
  - **Status Tags:** Small uppercase labels with increased letter spacing (e.g., "EMPTY SESSION", "VOLUME TREND").
  - **Buttons:** Use gradient cards instead of traditional buttons for a cohesive look.
  - **Dates:** Use relative date language (e.g., "Today", "Yesterday", "Monday") for recent history entries.
  - **Bottom Navigation:** Active tabs should be highlighted with `PrimaryAccent` (lime green).

- **Layout Principles:**
  - **Focus on Key Actions**: Full-width hero cards for primary workflows (Quick Start).
  - **Two-Column Metrics**: Side-by-side cards for quick glanceable data (Routines + Volume).
  - **Minimal Scrolling**: Show only the most recent workout as a reminder, not full history.
  - **Personalization**: Header shows "WELCOME BACK" + user's name + split-color app branding.
  - **Clean Information Hierarchy**: Use spacing and typography weight to guide attention, not excessive visual elements.
  - **Breathing Room**: Embrace empty space. Less is more. Don't fill every pixel - let content breathe.

## 6. Coding Standards

- **Compose:** Use `Stateless` composables where possible. Hoist state to the ViewModel.
- **Previews:** Always provide a `@Preview` with `showBackground = true` for UI components.
- **Error Handling:** Fail gracefully. If the DB is slow, show a skeleton loader, but never block the UI thread.
- **Hard Rules:**
  - Do NOT suggest Firebase or Network calls for core logging features.
  - Do NOT suggest XML layouts.
  - Keep dependencies minimal.

## 7. Instructions for Claude

- **Assume Project Context:** When I ask questions about implementation, UI components, or features, assume they're for IronLog unless I specify otherwise.
- **Code Style:** Always use Kotlin idioms (scope functions, nullable safety, sealed classes where appropriate).
- **Be Opinionated:** If there's a better way to structure something within these constraints, tell me and explain why.
- **No Boilerplate Explanations:** I understand Compose and Kotlin fundamentals. Focus on the specific solution.
- **Mobile-First Thinking:** All UI suggestions should prioritize thumb-reachability and one-handed use.
- **Reference the Schema:** When discussing features that touch data, explicitly reference the Room entities above.
