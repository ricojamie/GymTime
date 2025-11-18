# GymTime 💪

A modern Android fitness tracking app built with Jetpack Compose, designed to help you log workouts, track progress, and achieve your fitness goals.

## ✨ Features

### Current Features
- **🏠 Dynamic Home Screen**
  - Time-based greeting (Good morning/afternoon/evening)
  - Animated stats banner showcasing achievements
  - Quick start and continue routine buttons
  - Recent workout history with detailed metrics

- **📊 Animated Stats Tracking**
  - Auto-cycling achievement banner (3-second intervals)
  - Day streak with fire emoji 🔥
  - Weekly pounds lifted tracking
  - Personal bests (PBs) counter

- **🎨 Modern UI/UX**
  - Material3 design with dark theme
  - Custom purple gradient color scheme
  - Smooth animations and transitions
  - Elevated buttons with rounded corners
  - Page indicator dots for multi-stat banner

- **🗂️ Bottom Navigation**
  - Home screen
  - History screen (coming soon)
  - Exercise library (coming soon)

### Upcoming Features
- 💾 Room database integration for persistent storage
- 📈 Detailed workout history and analytics
- 🏋️ Exercise library with searchable exercises
- 📅 Workout routine builder
- 📊 Progress charts and visualizations
- 🎯 Goal setting and tracking
- ⏱️ Built-in workout timer
- 🔔 Rest timer notifications

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material3
- **Architecture:** MVVM (Model-View-ViewModel)
- **Dependency Injection:** Hilt
- **Navigation:** Compose Navigation
- **Minimum SDK:** 33 (Android 13)
- **Target SDK:** 36

## 📱 Screenshots

*Coming soon*

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 11
- Android SDK 33 or higher
- Git

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/ricojamie/GymTime.git
   cd GymTime
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory and select it

3. **Sync Gradle**
   - Android Studio should automatically sync Gradle files
   - If not, click `File > Sync Project with Gradle Files`

4. **Build and Run**
   - Connect an Android device or start an emulator
   - Click the "Run" button or press `Shift + F10`

### Troubleshooting

**Changes not appearing in Android Studio?**
- `File > Invalidate Caches > Invalidate and Restart`
- `Build > Clean Project` then `Build > Rebuild Project`

**Build errors?**
```bash
./gradlew clean assembleDebug
```

## 🎨 Design System

### Color Palette
- **Primary Accent:** `#8B48F7` (Vibrant Purple)
- **Primary Accent Dark:** `#6A1B9A` (Deep Purple)
- **Background Canvas:** `#121212` (Near Black)
- **Gradient Start:** `#1A0033` (Dark Purple)
- **Surface Cards:** `#1E1E1E` (Dark Gray)
- **Text Primary:** `#FFFFFF` (White)
- **Text Secondary:** `#FDE0E0` (Light Gray)
- **Success Fresh:** `#2ECC71` (Emerald Green)
- **Warning Fatigued:** `#E74C3C` (Alizarin Red)

### Component Guidelines
- **Buttons:** 16dp rounded corners, 12dp elevation, 2dp white border (30% opacity)
- **Cards:** 20dp rounded corners, 8dp elevation for feature cards
- **Icons:** 48dp for primary touch targets, 24dp for decorative
- **Animations:** 500ms transitions, 3s cycle time for rotating content

## 📂 Project Structure

```
app/
├── src/main/java/com/example/gymtime/
│   ├── GymTimeApp.kt              # Application class with Hilt
│   ├── MainActivity.kt            # Main entry point
│   ├── navigation/                # Navigation components
│   │   ├── Screen.kt             # Route definitions
│   │   └── BottomNavigationBar.kt
│   └── ui/
│       ├── theme/                # Theme & styling
│       │   ├── Color.kt
│       │   ├── Theme.kt
│       │   └── Type.kt
│       ├── home/                 # Home screen components
│       │   ├── Greeting.kt
│       │   ├── HomeViewModel.kt
│       │   └── StatsRow.kt
│       ├── history/              # History screen (placeholder)
│       ├── library/              # Library screen (placeholder)
│       ├── StartWorkout.kt       # Workout action buttons
│       └── RecentHistory.kt      # Recent workouts list
└── res/
    ├── drawable/                 # Vector icons
    ├── values/                   # Strings, colors, themes
    └── mipmap-*/                 # App launcher icons
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is currently unlicensed. All rights reserved.

## 👤 Author

**Rico Jamie**
- GitHub: [@ricojamie](https://github.com/ricojamie)
- Email: ricojamie@gmail.com

## 🙏 Acknowledgments

- Built with assistance from [Claude Code](https://claude.com/claude-code)
- Inspired by modern fitness tracking applications
- Material3 design guidelines from Google

---

**Note:** This app is currently in active development. Features and UI are subject to change.
