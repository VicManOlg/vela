# Vela — working notes for Claude

Vela is a console-style Android frontend for emulators and games (Kotlin, Jetpack Compose, Hilt,
Room, DataStore, Paging, Coil 3, OkHttp, kotlinx.serialization). Package `io.vela`, app id
`io.vela.frontend` (debug and perf builds: `io.vela.frontend.debug`). Landscape only, controller
first, touch supported everywhere. Public repo: https://github.com/VicManOlg/vela (GPL-3.0).

## Architecture rules

- Feature modules (`feature/*`) depend only on `core/data`, `core/ui`, `core/model`, `core/common`
  (enforced by `AndroidFeatureConventionPlugin`). They read settings through `AppSettingsRepository`;
  a type a feature needs from another core module belongs in `core/model`.
- Anything specific to an emulator, a system or a theme is **data, never code**:
  `core/catalog/src/main/resources/catalog/platforms.json`, `players.json`, `themes/*.json`.
  Adding an emulator means adding a JSON entry with its intent recipe (activity, action, extras,
  `{rom.uri}` / `{rom.path}` / `{rom.stem}` / `{package}` variables).
- The database is real user data (favourites, ratings, play time). Never rely on destructive
  migration: bump the version and add a `Migration` or an `AutoMigration`. Schemas are exported to
  `core/database/schemas`.
- Settings are one JSON document in DataStore (`AppSettings`); add fields with defaults, never
  rename or remove serialized names. Enum lists that may gain values use lenient serializers.
- Themes decide structure, not just colour: `ThemeLayout.homeLayout`, `libraryLayout`, `libraryView`,
  `showTabs`, `boxArtAspect`, `cardLabels`. User settings (`HomeLayout`, `LibraryLayout`,
  `gameListView`, `TabBarMode`) default to THEME; `AppSettings.libraryView` is the pre-0.3.1 field
  (every old install stored GRID there) and is no longer read. Systems views live in
  `feature/library` (`SystemStage.kt`, `PlatformsScreen.kt`, `SystemViews.kt`), game views in
  `GameGridScreen.kt` + `GameViews.kt`; a new enum value needs its `when` branch there and a
  bundled theme that uses it (see `ThemeLayoutsTest`).
- The user's look tweaks live in `AppSettings.appearance` (`AppearanceOverrides`, every field
  nullable = "theme's value") and are applied once, in `AppViewModel.theme`, through
  `ThemeSpec.applying`. Screens keep reading `VelaTheme`; never apply overrides elsewhere. A new
  tunable means: a field in `ThemeSpec` (so themes can set it), a nullable field in
  `AppearanceOverrides`, one line in `applying`, one row in `AppearanceSection.kt`.
  Settings rows need stable `item(key = …)`; an inserted row without one steals the focus.
- Compose: animated values are read in `graphicsLayer` / `draw` lambdas, not in composition.
  `clickable` alone is not focusable on touch devices; use `velaFocusable` (sounds, ring, glow).
  Touch-only targets use `pointerInput` tap gestures. Dialogs consume gamepad buttons.
- A list that sets an initial focus (`rememberAutoFocus`, `Rail(autoFocus = true)`) takes a
  `rememberFocusMemory()` and builds its items with `rememberedItems` / `rememberedItemsIndexed`,
  or the focus goes back to the first item after a tab switch or a screen on top
  (`focusRestorer` alone does not survive the tab leaving composition).
- ViewModel tests use fakes of repository interfaces (`CollectionRepository` is the model) and
  `MainDispatcherRule`; pass its dispatcher to `runTest` and create the ViewModel lazily.
- Visual direction "Farol de puerto" lives in `vela-night.json` (navy, lantern-amber focus as the
  only warm colour, Archivo + Atkinson Hyperlegible Next). Text never shrinks below the floors in
  `resolveTheme` (body 16, labels 14, captions 13 sp); metadata lines go through `metaLine()`.
  Theme tokens: `focusGlow`, `focusRingGap` (ring outside artwork), `buttonRadius`, `tagRadius`,
  `panelRadius`; leaving them out keeps a theme's old look.
- Motion uses `VelaSprings` (the M3 Expressive springs: `spatialFast/spatial/spatialSlow` move,
  `effects*` fade and recolour); Material 1.4 has no `MotionScheme`. Every new animation checks
  `VelaTheme.motion.reduceMotion`. Pages swapped in place use `pageEntrance(key)`, dialogs
  `panelEntrance()` + `animatedScrim()`.
- `artworkTint` (theme effect + override): the focused game's colour comes down as
  `LocalStageTint`, a `State<Color>` read only in draw/layer lambdas, and only by the focused item.
  Background mode `stage` = sharp scene on the right over the same art decoded at 24 px.
- Covers fly between screens with `Modifier.sharedCover(gameId, shape)` (no-op without the
  `SharedTransitionLayout` in `VelaApp`). Only one element per game per screen: row thumbnails
  and hero scenes never take it. Inside `AnimatedContent`, wrap `aspectRatio` children in a Box.
- Touch-only targets (hints, dialog scrims, tappable labels) get `focusProperties { canFocus = false }`:
  outside touch mode a plain `clickable` becomes focusable and focus disappears into it.
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
