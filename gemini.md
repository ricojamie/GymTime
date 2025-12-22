# IronLog - Project Context & Development Guide

## 1. Project Identity & Current Status

**Name:** IronLog
**Type:** Offline-first, privacy-centric strength training tracker for serious lifters
**Philosophy:** "Buy Once, Own Forever" - No ads, no subscriptions, no algorithm, no social bloat
**Status:** MVP Complete - Core logging loop, supersets, analytics, and 3D procedural Volume Orb functional

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
│   ├── History - Workout history and calendar
│   ├── Library - Exercise selection and custom exercise creation
│   └── Analytics - Volume trends, PRs, and muscle distribution
└── Gradient background (GradientStart → GradientEnd)
```

### MVVM + Clean Architecture
- **Activity**: MainActivity (only UI entry point)
- **ViewModels**: HomeViewModel, ExerciseSelectionViewModel, ExerciseLoggingViewModel, AnalyticsViewModel
- **Repositories**: UserPreferencesRepository (DataStore), VolumeOrbRepository
- **DAOs**: ExerciseDao, WorkoutDao, SetDao, RoutineDao, MuscleGroupDao
- **State Management**: Flow/StateFlow for reactive data
- **Async**: Coroutines with Dispatchers.IO for database operations

---

## 3. Tech Stack (Actual)

**Language:** Kotlin (2.0+)
**UI Framework:** Jetpack Compose (no XML, no Fragments)
**Architecture:** MVVM with coroutines
**Database:** Room (SQLite)
**Local Storage:** AndroidX DataStore (for user preferences)
**DI:** Hilt
**Animations:** Compose animations (spring, fade, slide)
**Haptics:** Android haptic feedback for set logging

---

## 4. Completed Features

### ✅ Home Dashboard
- Welcome header with user name + split-color "IronLog" branding
- Iron Streak Overview: Blue "glow" circles for active days, streak count, and sustainable skip logic
- Quick Start card (full-width hero card) → starts workout or resumes
- Weekly volume graph (line chart, animated)
- Procedural 3D Volume Orb: Radial gradients, rim lights, and reactive particle "bubbles"
- Personal best showcase card

### ✅ Exercise Selection & Library
- Search box and multi-select muscle group filters
- Custom exercise creation flow
- Exercise deletion with safety confirmation dialog
- Real-time filtering

### ✅ Exercise Logging Flow (The God Loop)
- Large, thumb-friendly weight/reps input (48sp font)
- **Superset Support**: Select 2 exercises to auto-rotate with crossfade navigation
- Auto-countdown rest timer with manual +/- adjustment
- Session log with "Last: [weight]x[reps]" auto-population from previous set
- Workout overview with exercise count and set summary

### ✅ Analytics & History
- Interactive volume charts with tooltip explorer
- Muscle distribution "Freshness" heatmap
- PR detection and 1RM estimation
- Full workout history with calendar view

---

## 5. Roadmap (Remaining Overhauls)

### 🎯 1. Analytics Overhaul
- **Objective**: Deep-dive into training data with better visualization and comparative metrics.
- **Key Features**:
  - Year-over-year volume comparisons
  - Individual exercise progress curves (1RM trends)
  - Muscle group volume distribution (pie/radar charts)
  - Export data (CSV/JSON) for external analysis
- **Plan Reference**: `PLAN_ANALYTICS_OVERHAUL.md`

### 🎯 2. Routine Overhaul
- **Objective**: Complete reconstruction of the routine builder and execution flow.
- **Key Features**:
  - Drag-and-drop routine builder
  - Folder organization for routine templates
  - "Smart Start": Auto-pick next routine in a program
  - Routine-to-Workout mapping with pre-filled target sets
- **Plan Reference**: `PLAN_ROUTINES_OVERHAUL.md`

---

## 6. Development Workflow

### Git Workflow
- **CRITICAL**: ALWAYS present your plan for any code changes or Git operations (branching, committing, merging) to the user and await explicit approval BEFORE execution.
- NEVER merge into main without explicit user approval.
- NEVER make any code changes without making a branch first.
- All features on `main` (no feature branches currently)
- Commit messages: descriptive, start with verb (Feat:, Fix:, Refactor:, etc)
- Always include Co-Authored-By footer with Claude credit

---

## 7. Project Health

### Performance Baseline
- App loads in <2 seconds
- Exercise list renders instantly (lazy column)
- Database queries <100ms
- Set logging button response immediate

**Last Updated**: December 18, 2025
**Current Phase**: Visual Polish & Feature Finalization
**Target Score**: 9.5/10 (MVP polished and responsive)