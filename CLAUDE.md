# CLAUDE.md

## Versioning (read before changing the app version)

The app version is defined **once** in [`gradle.properties`](gradle.properties) as
`gymVersionCode` and `gymVersionName`, and is consumed by both the `:app` and `:wear`
modules. They share the same `applicationId`, so a mismatched/lower `versionCode` causes
devices to downgrade ("revert") the app.

- To change the version, edit **only** those two properties — never hardcode
  `versionCode`/`versionName` in a module's `build.gradle.kts`.
- `gymVersionCode` must **only ever increase** (by 1 per release).
- Update the changelog in `SettingsScreen.kt` (`ChangelogDialog`) in the same release.
- Full procedure: [RELEASING.md](RELEASING.md).
