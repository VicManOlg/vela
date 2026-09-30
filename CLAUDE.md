# Vela — working notes for Claude

Vela is a console-style Android frontend for emulators and games (Kotlin, Jetpack Compose, Hilt,
Room, DataStore, Paging, Coil 3, OkHttp, kotlinx.serialization). Package `io.vela`, app id
`io.vela.frontend` (debug and perf builds: `io.vela.frontend.debug`). Landscape only, controller
first, touch supported everywhere. Public repo: https://github.com/VicManOlg/vela (GPL-3.0).

## Architecture rules

- Feature modules (`feature/*`) depend only on `core/data`, `core/ui`, `core/model`, `core/common`.
- Anything specific to an emulator, a system or a theme is **data, never code**:
  `core/catalog/src/main/resources/catalog/platforms.json`, `players.json`, `themes/*.json`.
  Adding an emulator means adding a JSON entry with its intent recipe (activity, action, extras,
  `{rom.uri}` / `{rom.path}` / `{rom.stem}` / `{package}` variables).
- The database is real user data (favourites, ratings, play time). Never rely on destructive
  migration: bump the version and add a `Migration` or an `AutoMigration`. Schemas are exported to
  `core/database/schemas`.
- Settings are one JSON document in DataStore (`AppSettings`); add fields with defaults, never
  rename or remove serialized names. Enum lists that may gain values use lenient serializers.
- Themes decide structure, not just colour: `ThemeLayout.homeLayout`, `libraryLayout`, `showTabs`,
  `boxArtAspect`. User settings (`HomeLayout`, `LibraryLayout`, `TabBarMode`) default to THEME.
- Compose: animated values are read in `graphicsLayer` / `draw` lambdas, not in composition.
  `clickable` alone is not focusable on touch devices; use `velaFocusable` (sounds, ring, glow).
  Touch-only targets use `pointerInput` tap gestures. Dialogs consume gamepad buttons.
- Keyboard letter shortcuts (X/Y/Q/E) exist only in debuggable builds.

## Build, test, install

```bash
./gradlew :app:assembleDebug            # debuggable, slow (interpreter); for Android Studio
./gradlew :app:assemblePerf             # R8, not debuggable, same package/signature as debug: day-to-day testing
./gradlew :app:assembleRelease          # signed with vela-release.keystore (untracked), io.vela.frontend
./gradlew testDebugUnitTest :core:catalog:test
adb install -r app/build/outputs/apk/perf/app-perf.apk
adb shell cmd package compile -m speed -f io.vela.frontend.debug   # full speed from the first frame
```

Android 14+ waits on `adb install -r` while the app is in the foreground: `am force-stop` first.
Perf/release builds do not log (Timber is planted only in debug).

## Devices

- Emulator AVD `Medium_Phone` (`emulator-5554`), landscape 2400x1080. Launch it **from bash, by full
  path, detached**: `nohup /c/Users/vicma/AppData/Local/Android/Sdk/emulator/emulator.exe -avd
  Medium_Phone -no-window -gpu swiftshader_indirect -no-snapshot-load -no-snapshot-save
  -no-boot-anim > log 2>&1 & disown`. After a hang, kill `qemu-system-x86_64-headless.exe` and
  `rm -rf ~/.android/avd/Medium_Phone.avd/*.lock`. Never `adb kill-server` with the emulator up.
  Gboard stylus overlay: `adb shell settings put secure stylus_handwriting_enabled 0`.
- Git Bash mangles adb arguments that look like paths: prefix commands with `MSYS_NO_PATHCONV=1`
  and give `adb push` Windows-style local paths (`C:/...`).
- Test ROMs are empty placeholder files under `/sdcard/ROMs/<system>/`; the scanner only needs names.
- Samsung Galaxy A54 (serial `RZCW320TXYE`, Android 16) runs the perf build with real data.
- Frame cost is measured from `dumpsys gfxinfo <pkg> framestats` (SyncQueued - HandleInputStart);
  the emulator's total frame time is dominated by its software GPU and is not meaningful.

## Secrets (untracked, back them up)

`secrets.properties` at the root: `screenscraper.devid/devpassword`, `steamgriddb.apikey`
(compiled in as the default key), `release.storeFile/storePassword/keyAlias/keyPassword`.
`vela-release.keystore` signs releases; losing it breaks updates for every installed copy.

## Releasing

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, then
`./gradlew :app:assembleRelease` and
`gh release create vX.Y.Z vela-X.Y.Z.apk --title "Vela X.Y.Z" --notes "..."`
(`gh` is at `C:/Program Files/GitHub CLI/gh.exe`). Obtainium follows the repository URL.

## Reporting

The owner works in Spanish and expects short HECHO / SIGUIENTE / DECISIONES reports, real
commits, screenshots as proof, and as few questions as possible.
