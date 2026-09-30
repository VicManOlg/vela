---
name: emulator-launching
description: Use when working on how Vela launches games in Android emulators — players.json recipes, LaunchIntentBuilder, GameLauncher, PlayerResolver, package/activity names, intent actions, extras, flags, content URIs, FileProvider, SAF permissions, killing emulator processes, or adding/fixing an emulator. Contains the rule that packages and activities are never invented and points to the generated emulator catalogue.
---

# Launching games in emulators

## The rule

**Never invent a package name, an activity, an intent action or an extra.** Every launch recipe in
Vela comes from one of two places, in this order:

1. `core/catalog/src/main/resources/catalog/players.json` — Vela's own recipes (`PlayerDefinition`).
2. `core/catalog/src/main/resources/catalog/emulators.json` — the generated catalogue of 958
   recipes for 121 platforms from Daijishō, cross-checked against ES-DE (`esdeVerified`).
   Summary and regeneration steps: `references/catalog-summary.md`; field meanings:
   `references/schema.md`.

If an emulator is in neither file, say so. Do not guess from the app's name, a Play Store URL or
memory. To add it, find a primary source (the emulator's own documentation, ES-DE's
`es_systems.xml` / `es_find_rules.xml`, Daijishō's platform JSON) and cite it in `notes`.
For a renamed build of a known emulator ("Eden Optimized"), `InstalledPackages` already matches
any installed package that declares the same emulation activity; no new entry is needed.

Everything user-facing about a launch is data: adding an emulator means a JSON entry, never
Kotlin. Read `CLAUDE.md` for the module rules.

## How a launch works today

- `PlayerResolver` picks the `PlayerDefinition` (per-game override, per-platform setting,
  platform defaults, any installed player) and the installed package (`InstalledPackages`).
- `LaunchIntentBuilder.build` turns the definition into an `Intent`: component from
  `activity` (relative `.Foo` or `{package}.Foo` expand against the installed package), `action`,
  `categories`, data by `delivery` (`CONTENT_URI_DATA`, `FILE_URI_DATA`, `EXTRAS_ONLY`, `NONE`),
  typed extras with `{rom.uri}`, `{rom.path}`, `{rom.stem}`, `{rom.dir}`, `{core.path}`,
  `{package}` variables, flags. Any `content://` value in an extra is added to `urisToGrant`
  and to `clipData`.
- `GameLauncher.start` grants the URIs to the target package, calls `startActivity`, and only
  then opens the play session. `ActivityNotFoundException` / `SecurityException` become a
  `VelaError.LaunchFailed` message; nothing is retried blindly.
- `EmulatorAvailability` (core/launcher) reports, for any catalogue recipe, `NOT_INSTALLED`,
  `ACTIVITY_MISSING` (package present, activity gone) or `READY`, using
  `PackageManager.resolveActivity` on the explicit component. Use it in diagnostics before
  proposing a recipe; it does not launch anything.
- `CatalogPlayers` (core/catalog) translates catalogue recipes into `PlayerDefinition`s with ids
  `catalog.<daijishou id>`: `{file.uri}`→`{rom.uri}`, `{file.path}`→`{rom.path}`; `-d {file.path}`
  becomes `RomDelivery.PATH_DATA` (bare path as data, like `am start -d`); recipes needing
  `{tags.*}` or `{file.mime}` are left out. Packages already in `players.json` are skipped, so a
  hand-checked recipe always wins. `PlayerResolver` appends these candidates **only when the
  package is installed and the activity resolves**; they never show as "not installed".
  A Vela platform maps to catalogue ids through `Platform.catalogIds` in `platforms.json`
  (e.g. `megadrive` → `genesis`, `arcade` → `mame`, `fbneo`).

## Known problems and how to handle them

- **`content://` vs file paths.** Modern emulators (Eden, Dolphin, PPSSPP, DuckStation new
  builds, aX360e) take a `content://` URI in `-d` or in an extra and need
  `FLAG_GRANT_READ_URI_PERMISSION`. Vela serves files through its `FileProvider`
  (`<applicationId>.files`) or passes the SAF URI as scanned. RetroArch and older emulators
  want a real filesystem path (`{rom.path}`, `requiresFilePath = true`), which only exists with
  All-files access; the launcher refuses with a clear message when the path is empty.
- **URI grants and extras.** `startActivity` grants URI permission only for `intent.data` and
  `clipData`. URIs inside extras get nothing automatically; that is why `LaunchIntentBuilder`
  copies them into `clipData` and calls `grantUriPermission` per URI. Keep that when editing.
- **SAF trees and subfolders.** A persisted tree permission covers the tree; a game that
  references sibling files (Flycast NAOMI/Atomiswave `.zip` + `.bin`/`.dat`, `.cue` + `.bin`,
  `.m3u` playlists) needs the emulator to be able to read the *folder*, which a single-file
  grant cannot give. Prefer path delivery when the emulator supports it, or grant the directory
  URI (`{rom.dir}`), and say in `notes` what the emulator needs.
- **`--activity-clear-task` / `--activity-clear-top`.** Daijishō recipes end with both so a
  second launch replaces the emulator's running task instead of resuming the previous game
  (Pizza Boy needed `FLAG_ACTIVITY_CLEAR_TASK` plus its `rom_uri` extra for exactly that).
  `FLAG_ACTIVITY_NEW_TASK` is required when starting from an application context.
  `--activity-no-history` keeps the emulator out of Back after it exits.
- **Killing the emulator process.** Daijishō's `killPackageProcesses` marks emulators that do
  not release their game on the next `startActivity` (RetroArch, some PS2/PSX cores). Vela does
  not kill processes today; `CatalogEmulator.killProcess` records the hint for a future
  `ActivityManager.killBackgroundProcesses` (needs `KILL_BACKGROUND_PROCESSES`) or a
  `-S`-style force stop, which is not available to third-party apps.
- **Package visibility (Android 11+).** `getPackageInfo`, `resolveActivity` and
  `queryIntentActivities` only see packages the app may query. Vela declares
  `QUERY_ALL_PACKAGES` in `app/src/main/AndroidManifest.xml`; if that is ever removed, add a
  `<queries><package android:name="..."/></queries>` entry per emulator package
  (`EmulatorCatalog.allPackages` and `PlayerCatalog.allPackages` list them).
- **Relative activity names.** `-n pkg/.Main` means `pkg.Main`; the catalogue expands them.
  Forks (Eden, Sudachi, Citron, Yuzu) keep upstream activity names under new packages: match by
  activity, not by name.
- **Placeholders.** Daijishō uses `{file.uri}`, `{file.path}`, `{tags.*}`; Vela uses
  `{rom.uri}`, `{rom.path}`, `{rom.stem}`, `{rom.dir}`, `{package}`. Translate when porting a
  recipe into `players.json`, and keep the original in `notes` or `raw`.

## Verifying a recipe

1. `EmulatorAvailability.status()` must be `READY` on a device with the emulator installed.
2. Launch from the app and watch `adb logcat` for `ActivityNotFoundException`,
   `SecurityException: Permission Denial ... content://` (missing grant) or the emulator's own
   "file not found" (path vs URI mismatch).
3. Launch a second game while the first is still open: it must replace it (flags).
4. Only then write it down; "it should work" is not verification.

## References

- `references/catalog-summary.md` — platforms, emulators, packages seen, warnings; how to regenerate.
- `references/schema.md` — the catalogue's fields and the parser's guarantees.
- `docs/research/emulator-intents.md` — field notes from real devices.
