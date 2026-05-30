# Releasing the Gym App

## Where the version lives

The app version is defined in **one place only**: [`gradle.properties`](gradle.properties).

```properties
gymVersionCode=8
gymVersionName=1.7.0
```

Both the phone app (`:app`) and the Wear OS companion (`:wear`) read these values, so
their versions can never drift apart. **Never** hardcode `versionCode` or `versionName`
in a module's `build.gradle.kts`.

## Why this matters

The phone and Wear modules share the same `applicationId` (`com.example.gymtime`).
Android identifies an installed app by that package name, so if either artifact ever
ships with a lower `versionCode` than what's installed, Android treats it as a
**downgrade** and the device reverts to the older app. Keeping a single, always-increasing
`versionCode` for both modules prevents this.

## The rules

1. **`gymVersionCode`** is a single integer that **must only ever increase** — bump it by
   exactly 1 on every release. Never reuse or lower it.
2. **`gymVersionName`** is the human-facing [semver](https://semver.org) string
   (`MAJOR.MINOR.PATCH`):
   - **PATCH** (`1.7.0 → 1.7.1`) — bug fixes only.
   - **MINOR** (`1.7.0 → 1.8.0`) — new features, backwards compatible.
   - **MAJOR** (`1.7.0 → 2.0.0`) — large or breaking changes.
3. Update the in-app changelog in
   [`SettingsScreen.kt`](app/src/main/java/com/example/gymtime/ui/settings/SettingsScreen.kt)
   (`ChangelogDialog`) in the same commit so users see what changed.
4. Commit with a `Release vX.Y.Z` message, and optionally tag it: `git tag vX.Y.Z`.

## Release checklist

- [ ] Bump `gymVersionCode` (+1) and `gymVersionName` in `gradle.properties`.
- [ ] Update the changelog entries in `SettingsScreen.kt`.
- [ ] Build: `.\gradlew.bat :app:assembleRelease :wear:assembleRelease`.
- [ ] Verify both APKs report the new version (Settings screen / merged manifest).
- [ ] Commit (`Release vX.Y.Z`) and tag.
