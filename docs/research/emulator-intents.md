# Launching Android emulators from a frontend via Intents

Research notes for a Kotlin/Compose emulation frontend. **No code was copied from any project**; this document records facts (package names, component names, actions, extras, flags) taken from primary sources, and marks anything inferred or second-hand.

Research date: 2026-09-17.

## 0. Sources and confidence legend

Primary sources used (all fetched directly):

| Tag | Source | URL |
|---|---|---|
| ES-DE-SYS | ES-DE Android `es_systems.xml` (launch commands) | https://gitlab.com/es-de/emulationstation-de/-/raw/master/resources/systems/android/es_systems.xml |
| ES-DE-RULES | ES-DE Android `es_find_rules.xml` (package/activity list) | https://gitlab.com/es-de/emulationstation-de/-/raw/master/resources/systems/android/es_find_rules.xml |
| ES-DE-DEV | ES-DE `ANDROID-DEV.md` (per-emulator setup notes, FileProvider list) | https://gitlab.com/es-de/emulationstation-de/-/raw/master/ANDROID-DEV.md |
| ES-DE-SRC | ES-DE `es-app/src/FileData.cpp` (how launch variables are parsed into an Intent) | https://gitlab.com/es-de/emulationstation-de/-/raw/master/es-app/src/FileData.cpp |
| DAIJI-WIKI | Daijishō wiki "Start Arguments" (raw markdown of the built-in player table) | https://raw.githubusercontent.com/wiki/TapiocaFox/Daijishou/Start-Arguments.md |
| DAIJI-TPL | Daijishō player template doc | https://github.com/TapiocaFox/Daijishou/blob/main/docs/daijishou_player_template.md |
| Per-emulator | `AndroidManifest.xml`, `build.gradle`, and activity sources on each project's GitHub/GitLab (URLs cited inline) | |
| AOSP | Android package-visibility docs | https://developer.android.com/training/package-visibility/declaring |

Confidence levels used below:

* **HIGH** – confirmed by the emulator's own manifest/source *and* by at least one frontend (ES-DE or Daijishō) that ships the recipe.
* **MEDIUM** – confirmed by ES-DE and/or Daijishō shipping recipes, but the emulator is closed-source (or its source could not be fetched), so the extra names come only from the frontends.
* **LOW** – inferred, single second-hand source, or known to be flaky.

Notation in the machine-readable blocks:

* `{rom.path}` = absolute filesystem path (`/storage/emulated/0/ROMs/...`).
* `{rom.safUri}` = a Storage Access Framework *document* URI string, e.g. `content://com.android.externalstorage.documents/document/primary%3AROMs%2Fpsx%2Fgame.chd`. The **emulator** must already hold a persisted tree permission for the parent folder (granted inside the emulator's own UI); the frontend cannot grant this.
* `{rom.providerUri}` = a `content://` URI served by the **frontend's own** `FileProvider`, sent with `FLAG_GRANT_READ_URI_PERMISSION`. Works for single files only (no `.cue` + `.bin` companions).
* `pkg/.Activity` = component with a relative class name; `pkg/full.class.Name` when the class lives in a different namespace than the applicationId (very common with forks: e.g. `io.github.lime3ds.android/org.citra.citra_emu.activities.EmulationActivity`).

---

## 1. General model: how ES-DE and Daijishō build the Intent

### 1.1 ES-DE launch-command grammar (ES-DE-SYS + ES-DE-SRC)

ES-DE's `es_systems.xml` for Android encodes an Intent as a pseudo command line, e.g.

```
%EMULATOR_DUCKSTATION% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% %EXTRABOOL_resumeState%=false %EXTRA_bootPath%=%ROMSAF%
```

`FileData::launchGame()` (ES-DE-SRC, Android branch around lines 1960–2130) parses it as follows:

| Token | Meaning (from `FileData.cpp`) |
|---|---|
| `%EMULATOR_X%` | Resolved through `es_find_rules.xml` `androidpackage` entries: `package/activity` pairs tried in order; the first installed one wins. |
| `%ACTION%=…` | `Intent.setAction`. If omitted, no action is set (explicit component launch). |
| `%CATEGORY%=…` | `Intent.addCategory`. |
| `%MIMETYPE%=…` | Intent type (used together with data, i.e. `setDataAndType`). |
| `%DATA%=…` | Intent data URI. Value may be `%ROM%`, `%ROMSAF%`, `%ROMPROVIDER%`, a literal (e.g. MAME machine name `cpc6128`) or `%INJECT%=…`. |
| `%EXTRA_name%=value` | `putExtra(name, String)`. |
| `%EXTRABOOL_name%=true/false` | `putExtra(name, boolean)`. |
| `%EXTRAINTEGER_name%=n` | `putExtra(name, int)`. |
| `%EXTRAARRAY_name%=a,b,c` | `putExtra(name, String[])` (comma separated). |
| `%ACTIVITY_CLEAR_TASK%`, `%ACTIVITY_CLEAR_TOP%`, `%ACTIVITY_NO_HISTORY%` | `FLAG_ACTIVITY_CLEAR_TASK`, `FLAG_ACTIVITY_CLEAR_TOP`, `FLAG_ACTIVITY_NO_HISTORY`. |
| `%ROM%` | Plain absolute path of the game file (string). |
| `%ROMSAF%` | The game file as a SAF document URI string. |
| `%ROMPROVIDER%` | The game file exposed through ES-DE's own FileProvider (temporary read grant). |
| `%ROMRAW%`, `%GAMEDIRRAW%`, `%ROMPATHRAW%`, `%BASENAME%` | Raw path / parent dir / ROM root / file name without extension, usable inside extra values (used heavily for MAME4droid `cli_params`). |
| `%INJECT%=file` | Replace the token with the *contents* of a text file (e.g. `.psvita`, `.ps3`, `.scummvm` files hold an ID). |
| `%INTERNALDATA%` | Expands to `/data/user/<userId>` (source comment: "expanded to /data/user/<userid> and /storage/emulated/<userid> respectively"). |
| `%EXTERNALDATA%` | Expands to `/storage/emulated/<userId>`. |
| `%ANDROIDPACKAGE%` | The resolved emulator package name (so RetroArch paths follow the variant). |
| `%ANDROIDAPP%=pkg` | Launch a native Android app by package (used by the Game importer). |

The final call is `Utils::Platform::Android::launchGame(package, activity, action, category, mimeType, data, startPath, romRaw, extrasString, extrasStringArray, extrasInteger, extrasBool, activityFlags, launchOnOtherScreen)`. **The Java/Kotlin side that turns `%ROMSAF%`/`%ROMPROVIDER%` into URIs is not in the public GitLab repository** (the `Utils::Platform::Android` implementation is not present in the tree), so the URI formats above are documented behaviour (ES-DE-DEV) rather than code we read.

ES-DE-DEV on FileProvider vs SAF (verbatim, important):

> "A number of emulators support the FileProvider API which makes it possible for ES-DE to temporarily provide storage access to the game file on launch. This means that most of the time no access permission needs to be setup in the emulator upfront. Access can however only be passed for single files, so for systems that support multi-file games such as disc-based games in .bin/.cue format SAF URIs are often used instead. For those emulators you will therefore generally need to manually provide scoped storage access to each game system directory. Note that it's not supported to give access to the root of the entire ROM directory for most emulators that use scoped storage, it has to be for the specific system. For instance /storage/emulated/0/ROMs/n64 rather than /storage/emulated/0/ROMs. The MAME4droid emulator is an exception to this rule."

> "Some emulators like RetroArch are still using an older storage access method and for those emulators this is not something you need to consider."

Emulators ES-DE launches through **FileProvider** (`%ROMPROVIDER%`): 2600.emu, DroidArcadia, EKA2L1, FPseNG, FPse (both "still need scoped storage to be setup in emulator"), GBA.emu, GBC.emu, Infinity, J2ME Loader, JL-Mod, Lynx.emu, MAME4droid Current (most systems), MAME4droid, MD.emu (some systems), NES.emu, NGP.emu, Panda3DS, PCE.emu (some systems), Ruffle, SkyEmu, Skyline, Swan.emu, SWF Player, Virtual Virtual Boy. Eden/Eden Nightly also use `%ROMPROVIDER%` in `es_systems.xml`.

### 1.2 Daijishō "am start arguments" grammar (DAIJI-WIKI, DAIJI-TPL)

Daijishō players are literally `am start` argument strings: `-n pkg/activity`, `-a action`, `-c category`, `-d data`, `-t mime`, `-e key value` (string), `--es`, `--ez key bool`, `--ei key int`, `--esa key a,b` (string array), `--activity-clear-task`, `--activity-clear-top`, `--activity-no-history`. Template tags: `{file.path}` (absolute path), `{file.uri}` (content URI), `{file.mime}`, `{file.name}`, `{tags.xxx}` (values from `.dpt` template files). Each player also has a "Kill process" boolean: when true Daijishō kills the emulator process when you come back (used for RetroArch and AetherSX2 entries). The exact way `{file.uri}` is generated (SAF vs FileProvider) is not documented on the wiki; issue threads (#487, #579) show that on Android 13 several `{file.uri}` players fail with `ActivityNotFoundException` — see §22.

### 1.3 The three ROM-passing styles you must support

1. **Path string** (`{rom.path}`) – in an extra or as `file://` data. Needed by RetroArch, ePSXe, Citra MMJ, Winlator (.desktop path), NooDS, DraStic (legacy `GAMEPATH`). Emulator needs legacy/"All files access" storage.
2. **SAF document URI string** (`{rom.safUri}`) – in an extra (Dolphin `AutoStartFile`, DuckStation/AetherSX2 `bootPath`, Pizza Boy `rom_uri`, Yaba Sanshiro `FileNameUri`, melonDS legacy `uri`) or as data. Only works if the emulator already has a persisted permission covering that file's tree.
3. **FileProvider content URI as Intent data** (`{rom.providerUri}`) with `FLAG_GRANT_READ_URI_PERMISSION` – the modern path. Grants apply only to `Intent.data`/`clipData`, **never to URIs placed inside string extras**. Single-file only.

When you set both data and a MIME type use `setDataAndType()`; calling `setType()` after `setData()` clears the data.

---

## 2. RetroArch (all variants)

**Confidence: HIGH** (manifest + gradle + native source + ES-DE + Daijishō).

Sources: `pkg/android/phoenix/AndroidManifest.xml` (https://raw.githubusercontent.com/libretro/RetroArch/master/pkg/android/phoenix/AndroidManifest.xml), `pkg/android/phoenix/build.gradle`, `pkg/android/phoenix/src/com/retroarch/browser/retroactivity/RetroActivityFuture.java`, `frontend/drivers/platform_unix.c` (extras read from JNI, lines ~2505–2740).

### 2.1 Package variants (from `build.gradle` productFlavors)

| Flavor | applicationId | Distribution |
|---|---|---|
| `normal` | `com.retroarch` | retroarch.com "universal" APK, F-Droid, Samsung Galaxy Store |
| `aarch64` | `com.retroarch.aarch64` | retroarch.com 64-bit APK (ES-DE recommended) |
| `ra32` | `com.retroarch.ra32` | retroarch.com 32-bit APK |
| `playStoreNormal` | `com.retroarch` | Google Play "RetroArch" (PLAY_STORE_BUILD=1; **missing many cores**) |
| `playStorePlus` | `com.retroarch.aarch64` | Google Play "RetroArch Plus" |

ES-DE find rule order: `com.retroarch.aarch64`, `com.retroarch.ra32`, `com.retroarch`. ES-DE-DEV: "The RetroArch release from the Google Play store is problematic. It does not contain all emulator cores and a number of people have reported issues launching games from ES-DE".

### 2.2 Activity

`com.retroarch.browser.retroactivity.RetroActivityFuture` – `exported="true"`, `launchMode="singleInstance"`, a `NativeActivity` (`android.app.lib_name = retroarch-activity`). `com.retroarch.browser.mainmenu.MainMenuActivity` is now only an `<activity-alias>` pointing at `RetroActivityFuture` (manifest comment: "same component name as the retired Java launcher"). Manifest declares `MANAGE_EXTERNAL_STORAGE` and `requestLegacyExternalStorage="true"` – RetroArch reads plain paths.

### 2.3 Extras actually read

From `platform_unix.c` (native, via `getStringExtra`): `CONFIGFILE`, `IME`, `USED`, `LIBRETRO`, `ROM`, `SDCARD`, `APK`, `EXTERNAL`, `AUDIO_RATE`, `AUDIO_FRAMES`, `DATADIR`. From `RetroActivityFuture.java`: `QUITFOCUS` (checked with `hasExtra`, "If QUITFOCUS parameter is provided then enable that Retroarch quits when focus is lost"), `REFRESH`; `onNewIntent` compares the new `ROM`/`LIBRETRO` with the current intent to decide whether to reload content. `DOWNLOADS` and `SCREENSHOTS` were **not found** in the current source; treat them as legacy and do not rely on them. Neither ES-DE nor Daijishō passes `IME`, `DATADIR`, `APK`, `SDCARD`, `EXTERNAL` – only `ROM`, `LIBRETRO`, `CONFIGFILE`.

```
id: retroarch64
package: com.retroarch.aarch64            # also com.retroarch, com.retroarch.ra32
component: com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture
action: none (ES-DE) | android.intent.action.MAIN (optional)
data: none
extras:
  ROM: {rom.path}                                        (string, absolute path; not a content URI)
  LIBRETRO: /data/data/com.retroarch.aarch64/cores/{core}_libretro_android.so
  CONFIGFILE: /storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg
  QUITFOCUS: ""      (optional; presence = exit RetroArch when it loses focus)
flags: FLAG_ACTIVITY_NEW_TASK (frontend side) ; ES-DE adds none; Daijishō "Kill process"=true
file://: n/a (ROM is a path string)
grant: not needed / not applicable
notes: singleInstance; black screen + ANR if the core .so or a BIOS is missing (ES-DE-DEV); cannot read /mnt/media_rw (USB) paths;
       "%INTERNALDATA%" in ES-DE expands to /data/user/<uid>, which is /data/data for user 0.
source: manifest+build.gradle+platform_unix.c (libretro/RetroArch), ES-DE-SYS, DAIJI-WIKI
```

Daijishō built-in players (DAIJI-WIKI, verbatim template):

```
-n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture
-e ROM {file.path}
-e LIBRETRO /data/data/com.retroarch.aarch64/cores/<core>_libretro_android.so
-e CONFIGFILE /storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg
```
(same for `com.retroarch.ra32` and `com.retroarch`).

### 2.4 Core naming convention

* Path: `/data/data/<package>/cores/<core>_libretro_android.so` (ES-DE: `%INTERNALDATA%/%ANDROIDPACKAGE%/cores/<core>_libretro_android.so`). The same core name is used for all three package variants; only `<package>` changes.
* Exception seen in ES-DE-SYS: `azahar_libretro.so` (no `_android` suffix) for the Azahar core.
* Fake-08 (PICO-8) must be manually installed and renamed to `fake08_libretro_android.so` (ES-DE-DEV).
* Cores must be downloaded inside RetroArch first (Online Updater) – the frontend can check `File("/data/data/<pkg>/cores/x_libretro_android.so")` only if it can read that directory, which it cannot on modern Android; treat a missing core as a runtime failure (black screen).

### 2.5 Recommended cores per system (ES-DE Android defaults; first entry is ES-DE's default)

| System | Cores (ES-DE order) |
|---|---|
| nes / famicom / fds | mesen, mesen2, nestopia, fceumm, quicknes, rustynes |
| snes / sfc | snes9x, snes9x2010, snes9x2005_plus, bsnes, bsnes_hd_beta, bsnes-jg, bsnes_mercury_accuracy, mednafen_supafaust, mesen-s, mesen2 |
| gb / gbc | gambatte, sameboy, gearboy, tgbdual, DoubleCherryGB, irogb, mesen-s, mesen2, bsnes, mgba, vbam, skyemu |
| gba | mgba, vbam, vba_next, gpsp, noods, skyemu, mesen2 |
| n64 / n64dd | mupen64plus_next_gles3, parallel_n64 |
| nds | melondsds, melonds, desmume, desmume2015, noods, skyemu |
| n3ds | azahar (`azahar_libretro.so`), citra, citra2018 |
| gc / wii / triforce | dolphin |
| wiiu | cemu |
| megadrive / genesis / segacd / megacd | genesis_plus_gx, genesis_plus_gx_wide, picodrive, blastem, clownmdemu |
| mastersystem / gamegear / sg-1000 | genesis_plus_gx, genesis_plus_gx_wide, smsplus/gearsystem, picodrive, blastem, mesen2 |
| sega32x | picodrive, blastem |
| saturn | mednafen_saturn, yabasanshiro, yabause |
| dreamcast / naomi / atomiswave | flycast |
| psx | mednafen_psx, mednafen_psx_hw, pcsx_rearmed, swanstation |
| ps2 | pcee2, pcsx2 |
| psp | ppsspp |
| pcengine / tg16 / pcenginecd | mednafen_pce, mednafen_pce_fast, mednafen_supergrafx, geargrafx, mesen2 |
| pcfx | mednafen_pcfx |
| neogeo | fbneo, geolith, mamearcade |
| neogeocd | neocd, geolith |
| arcade / mame | mamearcade, mame2010, mame2003_plus, mame2003, mame2000, hbmame, fbneo, fbalpha2012, geolith, flycast, dice, supermodel |
| cps1/2/3 | mamearcade, mame2010, mame2003_plus, fbneo, fbalpha2012, fbalpha2012_cps1/2/3 |
| atari2600 | stella, stella2014, stella2023, tia |
| atari5200 / atari800 | a5200, atari800 |
| atari7800 | prosystem |
| atarilynx | handy, mednafen_lynx, gearlynx, holani |
| atarijaguar | virtualjaguar |
| atarist | hatari, hatari2014 |
| amiga* / cdtv | puae, puae2021, amiberry |
| c64 | vice_x64sc, vice_x64, vice_xscpu64, vice_x128 |
| vic20 / plus4 | vice_xvic / vice_xplus4 |
| amstradcpc / gx4000 | cap32, crocods |
| zxspectrum / zx81 | fuse / 81 |
| msx / msx1 / msx2 | bluemsx, fmsx |
| colecovision | bluemsx, gearcoleco, jollycv, blastem |
| intellivision | freeintv |
| odyssey2 / videopac | o2em |
| channelf | freechaf |
| vectrex | vecx |
| virtualboy | mednafen_vb |
| wonderswan / wonderswancolor | mednafen_wswan, mesen2 |
| ngp / ngpc | mednafen_ngp, race |
| pokemini | pokemini |
| 3do | opera |
| dos / pc | dosbox_pure, dosbox_core, dosbox_svn, virtualxt |
| scummvm | scummvm (on Android you must press "Start Core" after launch – USERGUIDE) |
| pico8 | fake08 (manual install), retro8 |
| doom / quake | prboom, boom3 / tyrquake, vitaquake2 |
| tic80 / lowresnx / wasm4 / uzebox / vircon32 | tic80 / lowresnx / wasm4 / uzem / vircon32 |
| x68000 / pc98 / pc88 | px68k / np2kai, nekop2 / quasi88 |
| gameandwatch / lcdgames | mamemess, gw |

Full mapping is in ES-DE-SYS; the table above is the subset relevant to a general-purpose frontend.

---

## 3. Dolphin (official) and Dolphin MMJR / MMJR2

**Confidence: HIGH** for official (source read), **MEDIUM** for MMJR forks (closed/archived).

Sources: `Source/Android/app/src/main/AndroidManifest.xml`, `utils/StartupHandler.kt`, `utils/ContentHandler.kt` (https://raw.githubusercontent.com/dolphin-emu/dolphin/master/Source/Android/app/src/main/java/org/dolphinemu/dolphinemu/utils/StartupHandler.kt), ES-DE-SYS, DAIJI-WIKI.

`StartupHandler.getGamesFromIntent()` documents the priority order (verbatim comments): "1. Content URI, multiple" (`intent.clipData`), "2. Content URI, single" (`intent.data`), "3. File path, multiple" (`extras.getStringArray("AutoStartFiles")`), "4. File path, single" (`extras.getString("AutoStartFile")`). Comment: "Specifying content URIs (compatible with scoped storage) is prioritized over raw paths. The intention is that if a frontend app specifies both a content URI and a raw path, newer versions of Dolphin will work correctly under scoped storage, while older versions of Dolphin ... will also work." `HandleInit` is run by both `MainActivity` and `TvMainActivity`, then it calls `EmulationActivity.launch(...)` and finishes the main activity. `ContentHandler` catches `SecurityException` "when trying to access something the user hasn't granted us permission to" – i.e. a SAF URI in `AutoStartFile` only works if Dolphin already has the folder added in its own game list settings.

```
id: dolphin
package: org.dolphinemu.dolphinemu
component: org.dolphinemu.dolphinemu/.ui.main.MainActivity      (ES-DE uses .ui.main.TvMainActivity)
action: android.intent.action.MAIN
category: android.intent.category.LEANBACK_LAUNCHER (ES-DE, with TvMainActivity) | none
data: {rom.providerUri}  (preferred, modern)  -- or --
extras:
  AutoStartFile: {rom.safUri}            (string; ES-DE) | {rom.path} (legacy builds)
  AutoStartFiles: [uri1, uri2]           (string[]; multi-disc)
flags: FLAG_GRANT_READ_URI_PERMISSION when passing data/clipData; ES-DE adds no activity flags
file://: accepted as a raw path only via AutoStartFile on legacy builds
notes: intent.data / clipData take precedence over the extras; EmulationActivity itself uses the internal
       "SelectedGames" String[] extra, do not target it directly. Dolphin needs the ROM folder added in its own UI
       for SAF URIs in AutoStartFile (no grant possible for string extras).
source: StartupHandler.kt, AndroidManifest.xml (dolphin-emu/dolphin), ES-DE-SYS, DAIJI-WIKI
```

Forks (ES-DE-RULES + DAIJI-WIKI):

```
id: dolphin-mmjr
package: org.mm.jr
component: org.mm.jr/org.dolphinemu.dolphinemu.ui.main.MainActivity
action: android.intent.action.VIEW
extras: AutoStartFile: {rom.safUri} (ES-DE) | {rom.path} (Daijishō)
source: ES-DE-SYS/RULES, DAIJI-WIKI   (confidence MEDIUM)

id: dolphin-mmjr2
package: org.dolphinemu.mmjr
component: org.dolphinemu.mmjr/org.dolphinemu.dolphinemu.ui.main.MainActivity
action: android.intent.action.VIEW
extras: AutoStartFile: {rom.safUri}
source: ES-DE-SYS/RULES, DAIJI-WIKI   (confidence MEDIUM)
```
Daijishō also lists `org.dolphinemu.handheld`, `org.mm.j`, `org.dolphinemu.mmjr3`, `org.dolphin.ishiirukadark` (same activity, `AutoStartFile`). ES-DE-DEV: supported MMJR builds are `Dolphin.MMJR.v11505.apk` and `MMJR.v2.0-17878.apk`.

---

## 4. PPSSPP / PPSSPP Gold

**Confidence: HIGH.**

Sources: `android/AndroidManifest.xml`, `android/src/org/ppsspp/ppsspp/PpssppActivity.java` (`parseIntent`, https://raw.githubusercontent.com/hrydgard/ppsspp/master/android/src/org/ppsspp/ppsspp/PpssppActivity.java), ES-DE-SYS, DAIJI-WIKI.

Manifest: `.PpssppActivity` `exported="true"`, `launchMode="singleInstance"`, intent-filter `VIEW` + `DEFAULT` + `BROWSABLE`, schemes `file` and `content`, `mimeType="*/*"`, pathPatterns for `.iso .cso .chd .pbp .elf ...`. `parseIntent()` takes `intent.getData()` first; otherwise string extras `org.ppsspp.ppsspp.Shortcuts` (a path/URI used by home-screen shortcuts) or `org.ppsspp.ppsspp.Args` (raw argument string).

```
id: ppsspp
package: org.ppsspp.ppsspp                 # Gold: org.ppsspp.ppssppgold ; Legacy: org.ppsspp.ppsspplegacy
component: org.ppsspp.ppsspp/.PpssppActivity      # Gold: org.ppsspp.ppssppgold/org.ppsspp.ppsspp.PpssppActivity
action: android.intent.action.VIEW
category: android.intent.category.DEFAULT
data: {rom.providerUri} | {rom.safUri} | file://{rom.path}
type: application/octet-stream (Daijishō sets it; optional)
extras (alternative to data):
  org.ppsspp.ppsspp.Shortcuts: {rom.path or uri}   (string)
  org.ppsspp.ppsspp.Args: "<raw args>"             (string)
flags: FLAG_GRANT_READ_URI_PERMISSION ; Daijishō: --activity-clear-task --activity-clear-top --activity-no-history
notes: singleInstance. ES-DE-DEV: "Make sure that you press the Browse button in PPSSPP when you're adding scoped
       storage access to your games directory or you will not be able to launch any games". PPSSPP supports SAF natively.
source: PpssppActivity.java, AndroidManifest.xml (hrydgard/ppsspp), ES-DE-SYS, DAIJI-WIKI
```

---

## 5. AetherSX2 / NetherSX2 (and Turnip builds)

**Confidence: MEDIUM** (closed source; ES-DE and Daijishō agree on the recipe).

Sources: ES-DE-SYS/RULES/DEV, DAIJI-WIKI, https://github.com/Trixarian/NetherSX2-patch (README, no intent docs).

```
id: nethersx2
package: xyz.aethersx2.android             # NetherSX2 keeps the AetherSX2 package id (patched APK "…-4248-noads.apk")
component: xyz.aethersx2.android/.EmulationActivity
action: android.intent.action.MAIN
data: none
extras:
  bootPath: {rom.safUri}                  (string; Daijishō {file.uri}; SAF URI of the ISO/CHD)
flags: FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP (ES-DE + Daijishō); Daijishō "Kill process"=true
notes: ES-DE-DEV: AetherSX2 proper "hasn't been updated in years and which probably can't be used with ES-DE at all";
       the emulator must have the game directory added in its own settings for SAF URIs.
source: ES-DE-SYS, DAIJI-WIKI

variants (same activity class xyz.aethersx2.android.EmulationActivity, same extras):
  xyz.aethersx2.tturnip     NetherSX2-Turnip
  xyz.aethersx2.cturnip     NetherSX2-Turnip Classic
  xyz.aethersx2.custom      "AetherSX2 Turnip" (Daijishō only)
```

---

## 6. DuckStation

**Confidence: HIGH** for extra names (read from the last open-source Android revision), current builds are closed source.

Sources: last Android source before commit "Remove Android app" – `android/app/src/main/java/com/github/stenzek/duckstation/EmulationActivity.java` and `MainActivity.java` at commit `81da9be2d1040665ebfaf2db6d7fdb710a48a383` (https://raw.githubusercontent.com/stenzek/duckstation/81da9be2d1040665ebfaf2db6d7fdb710a48a383/android/app/src/main/java/com/github/stenzek/duckstation/EmulationActivity.java); ES-DE-SYS; DAIJI-WIKI.

Source: `getIntent().getStringExtra("bootPath")`, `getIntent().getBooleanExtra("resumeState", saveStateOnExit)`, `getIntent().getStringExtra("saveStatePath")`. `MainActivity.startEmulation()` puts `bootPath` + `resumeState`; when opening a file via the picker it passes `data.getDataString()` (a content URI) as `bootPath`, so **content URIs are accepted in `bootPath`**. `EmulationActivity` is `exported="true"`; `MainActivity` has only a MAIN/LAUNCHER filter (no VIEW).

```
id: duckstation
package: com.github.stenzek.duckstation
component: com.github.stenzek.duckstation/.EmulationActivity
action: none
data: none
extras:
  bootPath: {rom.safUri}            (string; content URI or legacy absolute path)
  resumeState: false                (boolean; false = fresh boot, true = load resume state)
  saveStatePath: <path>             (string, optional)
flags: FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP
notes: Android app is closed source since 2024 and unsupported by the author (README: "No support is provided for the
       Android app"). Extras confirmed in the last GPL revision and still used by ES-DE/Daijishō. Game dirs must be added in DuckStation.
source: EmulationActivity.java@81da9be (stenzek/duckstation), ES-DE-SYS, DAIJI-WIKI
```

---

## 7. Azahar / Lime3DS / Citra / Citra MMJ / Mandarine / AzaharPlus

**Confidence: HIGH** for Azahar (source), **HIGH** for Citra MMJ (source), **MEDIUM** for the others.

Sources: Azahar `src/android/app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, `fragments/EmulationFragment.kt` (https://raw.githubusercontent.com/azahar-emu/azahar/master/src/android/app/src/main/java/org/citra/citra_emu/fragments/EmulationFragment.kt); Citra MMJ `org/citra/emu/ui/EmulationActivity.java` (https://raw.githubusercontent.com/weihuoya/citra/master/src/android/app/src/main/java/org/citra/emu/ui/EmulationActivity.java); ES-DE; DAIJI-WIKI.

Azahar facts: namespace `org.citra.citra_emu`; `applicationId = "org.azahar_emu.azahar"` (GitHub builds) and a flavor with `applicationId = "io.github.lime3ds.android"` (Play Store / Lime3DS successor id). `EmulationActivity` is `exported="true"`, `launchMode="singleTop"`, filter `VIEW` + `DEFAULT` + `<data scheme="content" mimeType="application/octet-stream">`. `EmulationFragment.onCreate` reads `requireActivity().intent.data`; if null it falls back to string extras `"SelectedGame"` (a URI string) and `"SelectedTitle"` ("oldIntentInfo"). Non-Play builds then call `contentResolver.openFileDescriptor(uri, "r")` and use an `fd://` path – so a **read grant is required** (either the intent grant or a persisted permission).

```
id: azahar
package: org.azahar_emu.azahar             # also io.github.lime3ds.android (Play Store id); AzaharPlus: io.github.azaharplus.android
component: org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity
action: android.intent.action.VIEW
data: {rom.providerUri} | {rom.safUri}    (content://, type application/octet-stream to match the filter; explicit component ignores filter anyway)
extras (legacy fallback only if data is null):
  SelectedGame: <uri string>  (string)
  SelectedTitle: <title>      (string)
flags: FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP
notes: singleTop; onNewIntent restarts emulation with the new game.
source: AndroidManifest.xml, build.gradle.kts, EmulationFragment.kt (azahar-emu/azahar), ES-DE-SYS, DAIJI-WIKI
```

Related IDs (ES-DE-RULES, DAIJI-WIKI):

| Emulator | component |
|---|---|
| Lime3DS (dead) | `io.github.lime3ds.android/.activities.EmulationActivity` (same recipe) |
| Citra (PabloMK7 fork) | `org.citra.citra_emu/.activities.EmulationActivity`; ES-DE also lists `.ui.main.MainActivity` |
| Citra Canary | `org.citra.citra_emu.canary/org.citra.citra_emu.activities.EmulationActivity` |
| Mandarine | `io.github.mandarine3ds.mandarine/.activities.EmulationActivity` |
| Lemonade | `org.gamerytb.lemonade.canary/org.citra.citra_emu.activities.EmulationActivity` (Daijishō) |

Citra MMJ (weihuoya) is different – **file path in an extra**:

```
id: citra-mmj
package: org.citra.emu                    # also disguised build: com.antutu.ABenchMark
component: org.citra.emu/.ui.EmulationActivity
action: android.intent.action.VIEW (Daijishō) | none (ES-DE)
data: none
extras:
  GamePath: {rom.path}                    (string; EXTRA_GAME_PATH = "GamePath" in source; Daijishō also has a {file.uri} variant)
flags: FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP
source: EmulationActivity.java (weihuoya/citra), ES-DE-SYS, DAIJI-WIKI
```

---

## 8. melonDS (Android port by rafaelvcaetano)

**Confidence: HIGH** (README + manifest + source).

Sources: README "Integration with third-party frontends" (https://raw.githubusercontent.com/rafaelvcaetano/melonDS-android/master/README.md), `AndroidManifest.xml`, `ui/emulator/EmulatorActivity.kt`.

README (verbatim): "Package name: `me.magnum.melonds`; Activity name: `me.magnum.melonds.ui.emulator.EmulatorActivity`; Parameters (choose one): Intent data (preferred) - a URI of the NDS ROM (ZIP and 7z files are supported). Ensure read permission is granted; `uri` (deprecated) - a string with the SAF URI of the NDS ROM; `PATH` (deprecated) - a string with the absolute path to the NDS ROM". Also: "you will need to have the ROMs you want to launch already scanned by melonDS" and if not scanned, saves go to `Android/data/me.magnum.melonds/files/saves`. Manifest: `EmulatorActivity` `exported="true"`, `launchMode="singleTask"`, actions `${applicationId}.LAUNCH_ROM` and `${applicationId}.LAUNCH_FIRMWARE`. Source constants: `KEY_ROM="rom"` (internal Parcelable), `KEY_PATH="PATH"`, `KEY_URI="uri"`; `intent.data` is copied into the `uri` argument.

```
id: melonds
package: me.magnum.melonds                # nightly: me.magnum.melonds.nightly ; WatermelonDS: me.magnum.melondualds ; dev: me.magnum.melonds.dev
component: me.magnum.melonds/.ui.emulator.EmulatorActivity
action: me.magnum.melonds.LAUNCH_ROM      # = <applicationId>.LAUNCH_ROM, so nightly = me.magnum.melonds.nightly.LAUNCH_ROM
data: {rom.providerUri} | {rom.safUri}    (preferred)
extras (deprecated alternatives):
  uri: {rom.safUri}      (string)
  PATH: {rom.path}       (string)
flags: FLAG_GRANT_READ_URI_PERMISSION
notes: singleTask; WatermelonDS uses action me.magnum.melonds.LAUNCH_ROM with package me.magnum.melondualds (ES-DE).
source: README, AndroidManifest.xml, EmulatorActivity.kt (rafaelvcaetano/melonDS-android), ES-DE-SYS, DAIJI-WIKI
```

---

## 9. DraStic

**Confidence: MEDIUM** (closed source; recipes from ES-DE, Daijishō and a reverse-engineering mod README).

Sources: ES-DE-SYS/RULES/DEV, DAIJI-WIKI, https://github.com/TheGammaSqueeze/drastic-android-mod (README: works "from a `file://` or `content://` VIEW intent, from Daijisho / LaunchBox / any other frontend"; the internal field is a "`GAMEPATH` Parcelable extra"), Kodi AEL thread (`-e GAMEPATH "%rom%"` with `-a MAIN -c LAUNCHER`).

```
id: drastic
package: com.dsemu.drastic
component: com.dsemu.drastic/.DraSticActivity
action: android.intent.action.VIEW (implied; Daijishō omits -a) 
data: {rom.safUri} | {rom.providerUri} | file://{rom.path}
extras (legacy alternative): GAMEPATH: {rom.path}  (string; used by old Kodi/AEL recipes; LOW confidence)
flags: FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP | FLAG_GRANT_READ_URI_PERMISSION
notes: removed from Play Store; no zipped ROMs (ES-DE-DEV); Daijishō users on Android 13 report ActivityNotFoundException
       for this player (issue #579) – see §22 on package visibility.
source: ES-DE-SYS, DAIJI-WIKI, drastic-android-mod README
```

---

## 10. Redream

**Confidence: MEDIUM** (closed source).

Sources: ES-DE-SYS/RULES, DAIJI-WIKI, Daijishō issue #487 (Redream dev: "we can launch the main activity, but for some reason with saf paths it's going off the rails").

```
id: redream
package: io.recompiled.redream
component: io.recompiled.redream/.MainActivity
action: android.intent.action.VIEW
data: {rom.safUri} (ES-DE) | {file.uri} (Daijishō)
flags: FLAG_GRANT_READ_URI_PERMISSION (if provider URI); Daijishō: --activity-clear-task --activity-clear-top --activity-no-history
notes: game directory must be added in Redream's Library; Android 13 ActivityNotFoundException reports in Daijishō (#487, #579).
source: ES-DE-SYS, DAIJI-WIKI
```

---

## 11. Flycast

**Confidence: HIGH.**

Sources: `shell/android-studio/flycast/src/main/AndroidManifest.xml`, `BaseGLActivity.java` (https://raw.githubusercontent.com/flyinghead/flycast/master/shell/android-studio/flycast/src/main/java/com/flycast/emulator/BaseGLActivity.java), ES-DE, DAIJI-WIKI.

Manifest: launcher activity is `com.flycast.emulator.NativeGLActivity`; `com.flycast.emulator.MainActivity` is an `<activity-alias>` targeting it (so both names work; ES-DE also lists the legacy `com.reicast.emulator.MainActivity`). The VIEW filter only declares `file` scheme patterns (`.gdi .chd .cdi .cue ...`), but `BaseGLActivity.onCreate` simply does `if (ACTION_VIEW.equals(intent.getAction())) { Uri gameUri = intent.getData(); ... JNIdc.setGameUri(gameUri.toString()) }` and Flycast has SAF support, so content URIs work when launched with an explicit component.

```
id: flycast
package: com.flycast.emulator
component: com.flycast.emulator/com.flycast.emulator.MainActivity   (alias of .NativeGLActivity)
action: android.intent.action.VIEW
data: {rom.safUri} | {rom.providerUri} | file://{rom.path}
flags: FLAG_GRANT_READ_URI_PERMISSION
notes: the action MUST be VIEW or the data is ignored. For arcade (NAOMI/Atomiswave) Flycast needs its own content dir + BIOS.
source: AndroidManifest.xml, BaseGLActivity.java (flyinghead/flycast), ES-DE-SYS, DAIJI-WIKI
```

---

## 12. MAME4droid 2024 ("MAME4droid Current") and MAME4droid (0.139)

**Confidence: HIGH.**

Sources: `android-MAME4droid/app/src/main/AndroidManifest.xml`, `Emulator.java` (https://raw.githubusercontent.com/seleuco/MAME4droid-2024/master/android-MAME4droid/app/src/main/java/com/seleuco/mame4droid/Emulator.java), ES-DE-SYS/DEV, DAIJI-WIKI.

Manifest: `com.seleuco.mame4droid.MAME4droid` `exported="true"`, `launchMode="singleTask"`, VIEW filter with `mimeType application/zip`, `application/x-7z-compressed`, `scheme content` (and `file` up to SDK 23). Source (`Emulator.java` ~929–1040): on `ACTION_VIEW` it reads `intent.getData()` and `intent.getStringExtra("cli_params")`; for a `content://` URI it gets the display name and **copies the file into `<installationDir>/roms/`** if not already there, then sets `ROM_NAME`/`GAME_SELECTED` (zip/7z extension stripped). For a non-content URI it uses `uri.getPath()`. ES-DE additionally passes a **plain machine name as data** (e.g. `%DATA%=cpc6128`) plus `cli_params` such as `-rompath '<gamedir>;<roms>/amstradcpc' -flop1 '<rom>'` for MESS-style systems. ES-DE-DEV: ROM path inside MAME4droid must be set to the **root** ROMs directory, not per system.

```
id: mame4droid-2024
package: com.seleuco.mame4d2024             # legacy 0.139: com.seleuco.mame4droid/.MAME4droid
component: com.seleuco.mame4d2024/com.seleuco.mame4droid.MAME4droid
action: android.intent.action.VIEW
data: {rom.providerUri} | {rom.safUri} | file://{rom.path} | <machine name> (e.g. "a7800")
type: application/zip (optional)
extras:
  cli_params: "-rompath '<dir>;<roms>/arcade' [-cart '<path>'] ..."   (string, optional MAME CLI args)
flags: FLAG_GRANT_READ_URI_PERMISSION ; Daijishō: --activity-clear-task --activity-clear-top
notes: content URIs are COPIED into the app's roms dir (fine for zips; huge CHDs are slow). singleTask.
source: AndroidManifest.xml, Emulator.java (seleuco/MAME4droid-2024), ES-DE-SYS, DAIJI-WIKI
```

---

## 13. Mupen64Plus FZ (and Mupen64Plus AE)

**Confidence: HIGH.**

Sources: `app/src/main/AndroidManifest.xml`, `SplashActivity.java` (https://raw.githubusercontent.com/mupen64plus-ae/mupen64plus-ae/master/app/src/main/java/paulscode/android/mupen64plusae/SplashActivity.java), ES-DE, DAIJI-WIKI.

Manifest: `paulscode.android.mupen64plusae.SplashActivity` `exported="true"` with VIEW + DEFAULT + BROWSABLE, schemes `file`/`content`, `mimeType */*`, pathPatterns `.n64 .v64 .z64 .zip .7z ...`. `SplashActivity` forwards the launching intent into `GalleryActivity` (`ActivityHelper.startGalleryActivity(this, getIntent())`), which opens the data URI. Manifest `<queries>` declares only `OPEN_DOCUMENT_TREE`.

```
id: m64plus-fz
package: org.mupen64plusae.v3.fzurita          # Pro: org.mupen64plusae.v3.fzurita.pro ; Amazon: org.mupen64plusae.v3.fzurita.amazon
component: org.mupen64plusae.v3.fzurita/paulscode.android.mupen64plusae.SplashActivity
action: android.intent.action.VIEW
data: {rom.safUri} | {rom.providerUri} | file://{rom.path}
flags: FLAG_GRANT_READ_URI_PERMISSION
notes: Mupen64Plus AE (upstream): org.mupen64plusae.v3.alpha/paulscode.android.mupen64plusae.SplashActivity, same recipe.
source: AndroidManifest.xml, SplashActivity.java (mupen64plus-ae), ES-DE-SYS, DAIJI-WIKI
```

---

## 14. Pizza Boy GBA / GBC / SC (Basic and Pro)

**Confidence: MEDIUM** (closed source; two frontends use two different recipes).

Sources: ES-DE-SYS/RULES, DAIJI-WIKI, Argosy launcher PR #441 (https://github.com/rommapp/argosy-launcher/pull/441).

ES-DE: `%EXTRA_rom_uri%=%ROMSAF%` on `MainActivity` with CLEAR_TASK/CLEAR_TOP. Daijishō: `-e rom_uri {file.path}`. Argosy PR #441: Basic and Pro editions "use `LaunchConfig.FileUri` with `ACTION_VIEW` and FileProvider content URI", requiring `FLAG_GRANT_READ_URI_PERMISSION`; an earlier `ACTION_MAIN` + `rom_uri` attempt failed because "The raw file path passed via rom_uri caused FileDialogScopedStorage to fail on modern Android scoped storage".

```
id: pizzaboy-gba
package: it.dbtecno.pizzaboygba                # Pro: it.dbtecno.pizzaboygbapro
component: it.dbtecno.pizzaboygba/it.dbtecno.pizzaboygba.MainActivity   (Pro: it.dbtecno.pizzaboygbapro/it.dbtecno.pizzaboygbapro.MainActivity)
action: android.intent.action.VIEW
data: {rom.providerUri}                        (recommended, Argosy)
extras (alternative): rom_uri: {rom.safUri}    (string; ES-DE) | {rom.path} (Daijishō; fails under scoped storage per Argosy)
flags: FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP
source: ES-DE-SYS, DAIJI-WIKI, argosy-launcher PR #441

id: pizzaboy-gbc
package: it.dbtecno.pizzaboy                   # Pro: it.dbtecno.pizzaboypro
component: it.dbtecno.pizzaboy/it.dbtecno.pizzaboy.MainActivity
(same recipe)

id: pizzaboy-sc  (Sega)
package: it.dbtecno.pizzaboyscbasic            # Pro: it.dbtecno.pizzaboyscpro
component: it.dbtecno.pizzaboyscbasic/.MainActivity
extras: rom_uri: {rom.safUri}  (ES-DE only)
```

---

## 15. Lemuroid

**Confidence: HIGH that there is no ROM-path launch API.**

Sources: `lemuroid-app/src/main/AndroidManifest.xml`, `ExternalGameLauncherActivity.kt` (https://raw.githubusercontent.com/Swordfish90/Lemuroid/master/lemuroid-app/src/main/java/com/swordfish/lemuroid/app/shared/game/ExternalGameLauncherActivity.kt).

`com.swordfish.lemuroid.app.shared.game.ExternalGameLauncherActivity` (`exported="true"`) accepts `VIEW` with `<data scheme="lemuroid" host="${applicationId}" pathPattern="/play-game/id/.*">`; the code takes the **last path segment as an Int and looks it up in Lemuroid's own Room database** (`retrogradeDatabase.gameDao().selectById(gameId)`). `GameActivity` (VIEW filter, `process=":game"`) expects internal extras. There is no way to pass a ROM path/URI from another app.

```
id: lemuroid
package: com.swordfish.lemuroid
component: com.swordfish.lemuroid/com.swordfish.lemuroid.app.shared.game.ExternalGameLauncherActivity
action: android.intent.action.VIEW
data: lemuroid://com.swordfish.lemuroid/play-game/id/{lemuroidInternalGameId}
notes: internal ID only (created by Lemuroid's own scan / home-screen shortcuts). Practical frontend integration: open the app
       (MAIN/LAUNCHER) or use RetroArch instead. Not present in ES-DE or Daijishō.
source: AndroidManifest.xml, ExternalGameLauncherActivity.kt (Swordfish90/Lemuroid)
```

---

## 16. Yaba Sanshiro 2 (Saturn)

**Confidence: MEDIUM** (source not fetchable; ES-DE + Daijishō agree).

Sources: ES-DE-SYS/RULES/DEV, DAIJI-WIKI, Daijishō issue #434, HyperSpin thread (legacy `FileNameEx`).

```
id: yabasanshiro2
package: org.devmiyax.yabasanshioro2.pro       # free: org.devmiyax.yabasanshioro2 (ES-DE-DEV: only the paid Pro version supports launching)
component: org.devmiyax.yabasanshioro2.pro/org.uoyabause.android.Yabause
action: android.intent.action.VIEW (ES-DE) | android.intent.action.MAIN (Daijishō)
data: none
extras:
  org.uoyabause.android.FileNameUri: {rom.safUri}    (string)
flags: FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP (| FLAG_ACTIVITY_NO_HISTORY in Daijishō)
notes: ES-DE-DEV: ".bin/.cue files can't be launched for the time being, only .chd files seem to work"; some devices show
       "Cannot initialize SH2". Legacy Yaba Sanshiro 1: org.uoyabause.android(.pro)/org.uoyabause.android.Yabause with
       -a VIEW -e org.uoyabause.android.FileNameEx {file.path}.
source: ES-DE-SYS, DAIJI-WIKI
```

---

## 17. Play! (PS2)

**Confidence: HIGH.**

Sources: `build_android/src/main/AndroidManifest.xml` (https://raw.githubusercontent.com/jpd002/Play-/master/build_android/src/main/AndroidManifest.xml), `Source/ui_android/java/com/virtualapplications/play/MainActivity.java`, ES-DE.

Manifest: `.MainActivity` `exported="true"`, VIEW + DEFAULT + BROWSABLE, schemes `file`/`content`, `mimeType */*`, pathPatterns `.iso .bin .cso .isz .elf`; `requestLegacyExternalStorage="true"`. `MainActivity.onCreate`: `if (ACTION_VIEW.equals(intent.getAction())) { Uri uri = intent.getData(); VirtualMachineManager.launchGame(this, uri.toString(), this::finish); }`.

```
id: play
package: com.virtualapplications.play
component: com.virtualapplications.play/.MainActivity
action: android.intent.action.VIEW
data: {rom.safUri} | {rom.providerUri} | file://{rom.path}
flags: FLAG_GRANT_READ_URI_PERMISSION
source: AndroidManifest.xml, MainActivity.java (jpd002/Play-), ES-DE-SYS
```

---

## 18. Vita3K

**Confidence: HIGH.**

Sources: `android/app/src/main/AndroidManifest.xml`, `Emulator.java` (https://raw.githubusercontent.com/Vita3K/Vita3K/master/android/app/src/main/java/org/vita3k/emulator/Emulator.java), ES-DE, DAIJI-TPL.

`Emulator` activity: `exported="true"`, `launchMode="singleTop"`. `getArguments()`: `String[] args = intent.getStringArrayExtra("AppStartParameters")` used verbatim if present; else `title_id` (`EXTRA_TITLE_ID = "title_id"`) → `["-r", titleId]`; `game_title` (`EXTRA_GAME_TITLE`) is informational. Games must already be installed inside Vita3K; the frontend passes the **title ID**, not a file. ES-DE reads the ID from a `<name>.psvita` text file (`%INJECT%=%BASENAME%.psvita`).

```
id: vita3k
package: org.vita3k.emulator                   # ZX fork: org.vita3k.emulator.ikhoeyZX/org.vita3k.emulator.Emulator
component: org.vita3k.emulator/.Emulator
action: none
data: none
extras:
  AppStartParameters: ["-r", "{titleId}"]      (string[]; e.g. PCSF00001)
  -- or --
  title_id: "{titleId}"                        (string)
flags: none required
source: AndroidManifest.xml, Emulator.java (Vita3K/Vita3K), ES-DE-SYS, DAIJI-TPL
```

---

## 19. Nintendo Switch: Eden / Citron / Sudachi / Yuzu / Skyline / Kenji-NX

### 19.1 Eden — **HIGH**

Sources: `src/android/app/src/main/AndroidManifest.xml` and `app/build.gradle.kts`, `activities/EmulationActivity.kt` on https://git.eden-emu.dev/eden-emu/eden (raw/branch/master/...), ES-DE, DAIJI-WIKI.

Gradle: `namespace = "org.yuzu.yuzu_emu"`, `applicationId = "dev.eden.eden_emulator"`, nightly suffix `.nightly`, legacy flavor `dev.legacy.eden_emulator`, disguised flavor `com.miHoYo.Yuanshen`. Manifest `EmulationActivity` filters: `android.nfc.action.TECH_DISCOVERED` (mimeType `application/octet-stream`), `VIEW` (scheme `content`, mimeType `application/octet-stream`), and `dev.eden.eden_emulator.LAUNCH_WITH_CUSTOM_CONFIG`. Source: `isSwapIntent()` treats `intent.data != null` as "launch this game" (or the internal `SelectedGame` Parcelable extra). Both ES-DE and Daijishō use the `TECH_DISCOVERED` action with a content URI (historical yuzu quirk); `VIEW` + `content://` + `application/octet-stream` also matches the manifest.

```
id: eden
package: dev.eden.eden_emulator                # nightly: dev.eden.eden_emulator.nightly ; legacy: dev.legacy.eden_emulator ; disguised: com.miHoYo.Yuanshen(.nightly)
component: dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity
action: android.nfc.action.TECH_DISCOVERED     (ES-DE + Daijishō) | android.intent.action.VIEW
data: {rom.providerUri}                        (ES-DE uses %ROMPROVIDER%)
type: application/octet-stream
flags: FLAG_GRANT_READ_URI_PERMISSION
notes: keys/firmware must be installed in Eden. onNewIntent with data triggers a "ROM swap".
source: AndroidManifest.xml, build.gradle.kts, EmulationActivity.kt (eden-emu/eden), ES-DE-SYS, DAIJI-WIKI
```

### 19.2 Yuzu (dead) — **MEDIUM**
`org.yuzu.yuzu_emu` (+ `org.yuzu.yuzu_emu.ea`) `/org.yuzu.yuzu_emu.activities.EmulationActivity`, `-a android.nfc.action.TECH_DISCOVERED -d {file.uri}` (DAIJI-WIKI).

### 19.3 Sudachi — **MEDIUM/LOW**
Package `org.sudachi.sudachi_emu` (Uptodown listing, https://sudachi.en.uptodown.com/android) and `org.sudachi.sudachi_emu.ea` (DAIJI-WIKI). Component `org.sudachi.sudachi_emu(.ea)/org.sudachi.sudachi_emu.activities.EmulationActivity`, `-a android.nfc.action.TECH_DISCOVERED -d {file.uri}` (DAIJI-WIKI). Source repository could not be fetched; not in ES-DE.

### 19.4 Citron — **LOW**
Package `org.citron.citron_emu` (Uptodown listing, https://citron.en.uptodown.com/android). Activity is presumably `org.citron.citron_emu.activities.EmulationActivity` with the yuzu-style recipe, **unverified** (git.citron-emu.org was unreachable during research; not in ES-DE or Daijishō).

### 19.5 Skyline (dead) — **MEDIUM**
`skyline.emu/emu.skyline.EmulationActivity`, `-a android.intent.action.VIEW -d {file.uri}` (ES-DE uses `%ROMPROVIDER%`; DAIJI-WIKI). The maintained fork Strato (`org.stratoemu.strato/.EmulationActivity`, https://raw.githubusercontent.com/strato-emu/strato/master/app/src/main/AndroidManifest.xml) declares VIEW filters for `content` URIs with mimeTypes `application/nro`, `text/plain`, `application/octet-stream`.

### 19.6 Kenji-NX (Ryujinx-based) — **MEDIUM**
ES-DE: `org.kenjinx.android/.MainActivity`, `%ACTION%=org.kenjinx.android.LAUNCH_GAME %EXTRA_bootPath%=%ROMSAF%`.

---

## 20. ePSXe and FPse / FPseNG

**Confidence: MEDIUM** (closed source; ES-DE + Daijishō + old Kodi recipes agree).

```
id: epsxe
package: com.epsxe.ePSXe
component: com.epsxe.ePSXe/.ePSXe
action: android.intent.action.MAIN
data: none
extras:
  com.epsxe.ePSXe.isoName: {rom.path}          (string; absolute path – ES-DE uses %ROM%)
flags: FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP (Daijishō)
notes: paid app; needs legacy storage access to the path.
source: ES-DE-SYS, DAIJI-WIKI, Kodi forum recipe

id: fpse
package: com.emulator.fpse                     # FPseNG (64-bit): com.emulator.fpse64/.Main
component: com.emulator.fpse/.Main
action: android.intent.action.VIEW
data: {rom.providerUri} (ES-DE) | file://{rom.path} (Daijishō)
flags: FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP
notes: ES-DE-DEV: launches only via FileProvider but "still needs scoped storage to be setup in emulator"; no .chd support.
source: ES-DE-SYS/DEV, DAIJI-WIKI
```

---

## 21. Xenia (Xbox 360) on Android — not applicable

Xenia's own site states: "On Android, Xenia can only render GPU traces so it can't play games yet. Anything claiming to be a Xenia apk, etc is fake and probably a virus" (https://xenia.jp/). Android 360 emulators that ES-DE supports instead (ES-DE-SYS/RULES, MEDIUM):

```
aX360e:   aenu.ax360e/aenu.ax360e.EmulatorActivity (free: aenu.ax360e.free)  action aenu.intent.action.AX360E  extra game_uri={rom.safUri}
XenDroid: xendroid.compose/.EmulatorHostActivity                             data={rom.safUri}
Xenra:    Ali.Xanite/Ali.Xanite.LauncherActivity (also Ali.Xanite.green)      data={rom.safUri}
```

---

## 22. ScummVM

**Confidence: HIGH.**

Sources: `dists/android/AndroidManifest.xml`, `backends/platform/android/org/scummvm/scummvm/SplashActivity.java` and `ScummVMActivity.java` (https://raw.githubusercontent.com/scummvm/scummvm/master/backends/platform/android/org/scummvm/scummvm/ScummVMActivity.java), ES-DE.

`SplashActivity` (exported, MAIN/LAUNCHER) copies action+data into the internal `ScummVMActivity` intent (`fillIn(getIntent(), FILL_IN_ACTION | FILL_IN_DATA)`). `ScummVMActivity` then builds argv: `args = {"ScummVM", intentData.getSchemeSpecificPart()}` – i.e. the data URI's scheme-specific part is passed as the ScummVM **game target** (e.g. `monkey`). The game must already be added in ScummVM's launcher. ES-DE reads the target from the `.scummvm` file with `%DATA%=%INJECT%=%ROM%`.

```
id: scummvm
package: org.scummvm.scummvm                   # debug builds: org.scummvm.scummvm.debug
component: org.scummvm.scummvm/org.scummvm.scummvm.SplashActivity
action: android.intent.action.MAIN
data: {scummvmTargetId}                        (Uri.parse("monkey") – schemeless string; ScummVM uses getSchemeSpecificPart())
flags: none
notes: pass the target id, not the game directory; RetroArch scummvm core on Android requires "Start Core" manually.
source: SplashActivity.java, ScummVMActivity.java (scummvm/scummvm), ES-DE-SYS
```

---

## 23. Winlator (Cmod and forks), WinNative, GameHub Lite, GameNative

### 23.1 Winlator mainline — cannot be launched
`com.winlator/.XServerDisplayActivity` is `exported="false"` in mainline (brunodev85) manifests, and the PR that would have exported it (https://github.com/brunodev85/winlator/pull/79, adding `externalCommand` and a string `container_id`) was **closed unmerged**. ES-DE-DEV: "mainline Winlator does not offer frontend support".

### 23.2 Winlator Cmod (coffincolors) — **HIGH**

Sources: branch `cmod_bionic` `app/build.gradle` (`applicationId "com.winlator.cmod"`), `AndroidManifest.xml` (`com.winlator.cmod.XServerDisplayActivity` `exported="true"`), `XServerDisplayActivity.java` (https://raw.githubusercontent.com/coffincolors/winlator/cmod_bionic/app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java): reads `getIntent().getIntExtra("container_id", 0)`, `getStringExtra("shortcut_path")`, `getStringExtra("shortcut_name")`; when `container_id == 0` it parses the container id and name from the `.desktop` file at `shortcut_path`.

```
id: winlator-cmod
package: com.winlator.cmod                     # Bionic build. Glibc build keeps package com.winlator (+ com.winlator.XServerDisplayActivity);
                                               # PRoot build: com.cmodded.winlator/com.winlator.XServerDisplayActivity;
                                               # Ludashi fork disguised ids: com.winlator.vanilla, com.ludashi.benchmark, com.miHoYo.GenshinImpact (activity com.winlator.cmod.XServerDisplayActivity)
component: com.winlator.cmod/.XServerDisplayActivity
action: none
data: none
extras:
  shortcut_path: {rom.path}                    (string; absolute path of a .desktop file exported via "Export for Frontend")
  container_id: <int>                          (optional; parsed from the .desktop file when 0/absent)
  shortcut_name: <string>                      (optional)
flags: FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP (| FLAG_ACTIVITY_NO_HISTORY in Daijishō)
notes: singleTask; needs legacy storage to read the .desktop path; the .desktop file references the container.
source: build.gradle, AndroidManifest.xml, XServerDisplayActivity.java (coffincolors/winlator@cmod_bionic), ES-DE-SYS/RULES/DEV, DAIJI-WIKI
```

WinNative (ES-DE-RULES/SYS, MEDIUM): `com.winnative.cmod/com.winlator.cmod.runtime.display.XServerDisplayActivity` (also disguised ids `com.antutu.ABenchMark`, `com.ludashi.benchmark`, `com.tencent.ig`), extra `shortcut_path={rom.path}`, CLEAR_TASK|CLEAR_TOP.

### 23.3 GameHub Lite — **MEDIUM** (ES-DE only)
```
package: emuready.gamehub.lite                 # also gamehub.lite and disguised ids (com.antutu.ABenchMark, com.tencent.ig, com.ludashi.aibench, com.antutu.benchmark.full)
component: <pkg>/com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity
action: gamehub.lite.LAUNCH_GAME
extras: autoStartGame=true (boolean), steamAppId="<appid>" (string) and/or localGameId="<id>" (string)
source: ES-DE-SYS/RULES
```

### 23.4 GameNative — **HIGH**
Manifest (https://raw.githubusercontent.com/utkarshdalal/GameNative/master/app/src/main/AndroidManifest.xml): `.MainActivity` `exported="true"`, `launchMode="singleTop"`, filters include `<action android:name="app.gamenative.LAUNCH_GAME"/>` and a `gamenative://run` deep link.
```
package: app.gamenative
component: app.gamenative/.MainActivity
action: app.gamenative.LAUNCH_GAME
extras: game_source="STEAM"|"EPIC"|"GOG"|"AMAZON"|"CUSTOM_GAME" (string), app_id=<int> (integer)
source: AndroidManifest.xml (utkarshdalal/GameNative), ES-DE-SYS
```

---

## 24. Moonlight and Steam Link (package names only)

* Moonlight: `com.limelight` (applicationId in https://raw.githubusercontent.com/moonlight-stream/moonlight-android/master/app/build.gradle; `com.limelight.root` for the root flavor, `.debug`/`.unofficial` suffixes exist). Launcher: MAIN/LAUNCHER. **HIGH**.
* Steam Link: `com.valvesoftware.steamlink` (Play Store / APK mirrors). **HIGH** (well-known id; not verified from source since the app is closed).

---

## 25. ES-DE: how content URIs are resolved vs. passed

* ES-DE scans ROM directories with plain file access (it declares "All files access"), so it always knows `%ROM%` as a path.
* `%ROMSAF%` converts that path into a SAF *document* URI string for the primary/secondary storage document provider. ES-DE itself does not hold (and cannot pass) the permission; the docs therefore instruct users to grant each **system directory** inside each emulator ("it has to be for the specific system ... /storage/emulated/0/ROMs/n64 rather than /storage/emulated/0/ROMs", MAME4droid being the exception that wants the root).
* `%ROMPROVIDER%` exposes the file through ES-DE's own `FileProvider` and passes it as Intent data with a temporary read grant ("temporarily provide storage access to the game file on launch ... Access can however only be passed for single files").
* `%ROM%` (raw path) is used only for emulators with legacy storage (RetroArch, ePSXe, Citra MMJ, NooDS, SeedlessDS, Winlator/WinNative `.desktop` files, Starboard).
* `%INJECT%` reads a small text file (`.psvita`, `.ps3`, `.ps4`, `.scummvm`, `.commands`) and injects its content (title IDs / target names).
* The Java layer implementing this is not in the public repo, so URI formats are inferred from the docs, not read from code.

Recommendation for a new frontend: implement all three (path, SAF-URI string, FileProvider-URI data) and choose per emulator as in the blocks above; expose a per-emulator override so users can switch when an emulator update changes behaviour.

---

## 26. Detecting installed emulators (Android 11+ package visibility)

Source: https://developer.android.com/training/package-visibility/declaring.

* From `targetSdk 30`, `PackageManager` results are filtered. `getPackageInfo(pkg, 0)` throws `PackageManager.NameNotFoundException` for packages your app cannot see; `queryIntentActivities`, `resolveActivity`, `getInstalledApplications`, `getLaunchIntentForPackage` silently omit them. `startActivity` with an explicit component still works even if the package is not visible, **as long as the target activity is exported** – otherwise `ActivityNotFoundException`, which is exactly the Android 13 symptom reported in Daijishō issues #579/#487 ("Unable to find explicit activity class {...}").
* Declare visibility in the manifest:
  * `<queries><package android:name="org.ppsspp.ppsspp"/> ...</queries>` – one `<package>` per emulator package you probe (all variants: `com.retroarch`, `com.retroarch.aarch64`, `com.retroarch.ra32`, `org.ppsspp.ppssppgold`, disguised ids such as `com.antutu.ABenchMark`, etc.). Docs: "If you declare a `<package>` element in your app's manifest, then the app associated with that package name appears in the results of any query to PackageManager that matches a component from that app."
  * `<queries><intent><action android:name="android.intent.action.VIEW"/><data android:mimeType="*/*"/></intent></queries>` to discover any VIEW handler; also one `<intent>` per custom action you use (`me.magnum.melonds.LAUNCH_ROM`, `app.gamenative.LAUNCH_GAME`, `gamehub.lite.LAUNCH_GAME`, `org.kenjinx.android.LAUNCH_GAME`, `aenu.intent.action.APS3E`, `aenu.intent.action.AX360E`, `android.nfc.action.TECH_DISCOVERED`). Restrictions: "You must include exactly one `<action>` element" per `<intent>`, and `path`/`mimeGroup` attributes are not allowed.
  * `QUERY_ALL_PACKAGES` makes everything visible but Google Play restricts it to app types like launchers, browsers, accessibility, device management and security apps; a frontend that is also set as the **home app** (ES-DE supports running as home) has a plausible justification, but sideloaded/F-Droid distribution avoids the policy entirely. Docs: "your app should request the smallest amount of package visibility necessary".
* Practical detection: for each emulator definition iterate its candidate `package/activity` pairs (ES-DE find-rule style), call `getPackageInfo(pkg, 0)` (or `getActivityInfo(ComponentName, 0)` to also verify the activity exists and `exported == true`), and pick the first hit. Prefer `PackageManager.MATCH_ALL`/`ResolveInfoFlags` for `resolveActivity` on API 33+.
* Play Store vs GitHub builds share package names for most emulators (Dolphin, PPSSPP, DuckStation, MAME4droid); RetroArch and Azahar differ (see §2, §7). melonDS/Mupen64Plus AE/ScummVM only declare `<queries>` for the document picker, which is irrelevant to you.

---

## 27. Detecting that the game session ended; play time

* No emulator surveyed calls `setResult()` for an external caller. Lemuroid's `REQUEST_PLAY_GAME`/`PLAY_GAME_RESULT_LEANBACK` result is internal to its own `ExternalGameLauncherActivity`. RetroArch, PPSSPP (`singleInstance`), melonDS/MAME4droid/Winlator (`singleTask`), Azahar/Vita3K (`singleTop`) run in their own task; the user returns with Back/Home or when the emulator finishes.
* Therefore frontends use lifecycle callbacks: record `System.currentTimeMillis()` (or `SystemClock.elapsedRealtime()`) right before `startActivity`, then on the frontend activity's next `onResume`/`onStart` compute the delta. ES-DE does exactly this: it stores `GameLaunchTime` before launching and in `FileData::setPlayMetadata()` adds `endTime - startTime` to the `playtime` metadata, discarding values above a user-set `MaxPlayTimeTracking` cap (0–24 h) to ignore "left the device on" sessions (ES-DE-SRC lines ~159–190). Use `ActivityResultLauncher`/`startActivityForResult` only to get a reliable "returned" callback (`RESULT_CANCELED` is what you will receive); do not expect data.
* Daijishō's "Kill process" option kills the emulator when you return (RetroArch, AetherSX2 players default to true) – that requires `android.permission.KILL_BACKGROUND_PROCESSES` (`ActivityManager.killBackgroundProcesses(pkg)`), which only kills background processes and is heavily restricted on Android 14+.
* Emulators that track play time internally (Azahar: `NativeLibrary.playTimeManagerStart(titleId)` in `EmulationActivity`; RetroArch: per-core content runtime logs in its `playlists/logs` directory; Eden/yuzu: internal) do **not** expose it through an API or broadcast. RetroArch's runtime logs are JSON files under `/storage/emulated/0/RetroArch/playlists/logs/<core>/<game>.lrtl` on legacy-storage builds and can be read by an "All files access" frontend; otherwise unavailable. (Directory location is from RetroArch defaults; LOW confidence for Android builds.)
* `UsageStatsManager.queryEvents()` gives exact foreground time per package but requires the special `PACKAGE_USAGE_STATS` permission (user grants it in Settings); it is the only accurate cross-emulator play-time source.
* Launch flags: when launching from a Compose `Activity` you do not need `FLAG_ACTIVITY_NEW_TASK`; add it only when launching from a non-Activity context. `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP` (as ES-DE/Daijishō do) ensure a stale emulator task is replaced so the ROM actually changes; `FLAG_ACTIVITY_NO_HISTORY` makes Back return straight to the frontend but can break emulators that relaunch themselves (Vita3K uses `AppStartParameters` for in-process relaunch).

---

## 28. Summary table

| Emulator | Package(s) | Component | ROM passing | Confidence |
|---|---|---|---|---|
| RetroArch | `com.retroarch`, `com.retroarch.aarch64`, `com.retroarch.ra32` | `…/com.retroarch.browser.retroactivity.RetroActivityFuture` | extras `ROM` (path), `LIBRETRO`, `CONFIGFILE` | HIGH |
| Dolphin | `org.dolphinemu.dolphinemu` | `/.ui.main.MainActivity` (or `TvMainActivity`) | data/clipData content URI, or extra `AutoStartFile(s)` (SAF URI / path) | HIGH |
| Dolphin MMJR / MMJR2 | `org.mm.jr`, `org.dolphinemu.mmjr` | `/org.dolphinemu.dolphinemu.ui.main.MainActivity` | VIEW + extra `AutoStartFile` | MEDIUM |
| PPSSPP / Gold | `org.ppsspp.ppsspp`, `org.ppsspp.ppssppgold` | `/org.ppsspp.ppsspp.PpssppActivity` | VIEW + data URI (content/file); extras `org.ppsspp.ppsspp.Shortcuts`/`.Args` | HIGH |
| AetherSX2 / NetherSX2 | `xyz.aethersx2.android` (+ `.tturnip`, `.cturnip`, `.custom`) | `/xyz.aethersx2.android.EmulationActivity` | MAIN + extra `bootPath` (SAF URI) | MEDIUM |
| DuckStation | `com.github.stenzek.duckstation` | `/.EmulationActivity` | extras `bootPath` (URI/path), `resumeState` (bool), `saveStatePath` | HIGH (legacy src) |
| Azahar / Lime3DS | `org.azahar_emu.azahar`, `io.github.lime3ds.android` | `/org.citra.citra_emu.activities.EmulationActivity` | VIEW + content data (octet-stream); legacy extras `SelectedGame`/`SelectedTitle` | HIGH |
| Citra / Canary / Mandarine | `org.citra.citra_emu`(`.canary`), `io.github.mandarine3ds.mandarine` | `…activities.EmulationActivity` | same as Azahar | MEDIUM |
| Citra MMJ | `org.citra.emu` (`com.antutu.ABenchMark`) | `/.ui.EmulationActivity` | extra `GamePath` (path) | HIGH |
| melonDS | `me.magnum.melonds` (`.nightly`, `me.magnum.melondualds`) | `/.ui.emulator.EmulatorActivity` | action `<pkg>.LAUNCH_ROM` + data URI (or deprecated `uri`/`PATH` extras) | HIGH |
| DraStic | `com.dsemu.drastic` | `/.DraSticActivity` | data URI (content/file); legacy `GAMEPATH` | MEDIUM |
| Redream | `io.recompiled.redream` | `/.MainActivity` | VIEW + data URI | MEDIUM |
| Flycast | `com.flycast.emulator` | `/com.flycast.emulator.MainActivity` (alias) | VIEW + data URI | HIGH |
| MAME4droid Current/2024 | `com.seleuco.mame4d2024` (`com.seleuco.mame4droid`) | `/com.seleuco.mame4droid.MAME4droid` | VIEW + data (content URI copied in, or machine name) + extra `cli_params` | HIGH |
| Mupen64Plus FZ / AE | `org.mupen64plusae.v3.fzurita`(`.pro`,`.amazon`), `org.mupen64plusae.v3.alpha` | `/paulscode.android.mupen64plusae.SplashActivity` | VIEW + data URI | HIGH |
| Pizza Boy GBA/GBC/SC | `it.dbtecno.pizzaboygba(pro)`, `it.dbtecno.pizzaboy(pro)`, `it.dbtecno.pizzaboysc(basic|pro)` | `/<pkg>.MainActivity` | VIEW + FileProvider data (recommended) or extra `rom_uri` | MEDIUM |
| Lemuroid | `com.swordfish.lemuroid` | `…ExternalGameLauncherActivity` | `lemuroid://…/play-game/id/<internalId>` only | HIGH (not path-launchable) |
| Yaba Sanshiro 2 (Pro) | `org.devmiyax.yabasanshioro2.pro` (`…2`) | `/org.uoyabause.android.Yabause` | extra `org.uoyabause.android.FileNameUri` (SAF URI) | MEDIUM |
| Play! | `com.virtualapplications.play` | `/.MainActivity` | VIEW + data URI | HIGH |
| Vita3K | `org.vita3k.emulator` (`.ikhoeyZX`) | `/.Emulator` | extra `AppStartParameters=["-r",titleId]` or `title_id` | HIGH |
| Eden | `dev.eden.eden_emulator` (`.nightly`, `dev.legacy.eden_emulator`, `com.miHoYo.Yuanshen`) | `/org.yuzu.yuzu_emu.activities.EmulationActivity` | `TECH_DISCOVERED` or VIEW + content data (octet-stream) | HIGH |
| Yuzu / Sudachi | `org.yuzu.yuzu_emu(.ea)` / `org.sudachi.sudachi_emu(.ea)` | `…activities.EmulationActivity` | `TECH_DISCOVERED` + data URI | MEDIUM |
| Citron | `org.citron.citron_emu` | presumably `…activities.EmulationActivity` | presumably as yuzu | LOW |
| Skyline (dead) | `skyline.emu` | `/emu.skyline.EmulationActivity` | VIEW + data URI | MEDIUM |
| Kenji-NX | `org.kenjinx.android` | `/.MainActivity` | action `org.kenjinx.android.LAUNCH_GAME` + extra `bootPath` | MEDIUM |
| ePSXe | `com.epsxe.ePSXe` | `/.ePSXe` | MAIN + extra `com.epsxe.ePSXe.isoName` (path) | MEDIUM |
| FPse / FPseNG | `com.emulator.fpse` / `com.emulator.fpse64` | `/.Main` | VIEW + data (FileProvider URI or file path) | MEDIUM |
| ScummVM | `org.scummvm.scummvm` | `/org.scummvm.scummvm.SplashActivity` | MAIN + data = target id | HIGH |
| Winlator Cmod | `com.winlator.cmod` (glibc: `com.winlator`; proot: `com.cmodded.winlator`) | `/.XServerDisplayActivity` | extra `shortcut_path` (.desktop path), optional `container_id` | HIGH |
| WinNative | `com.winnative.cmod` | `/com.winlator.cmod.runtime.display.XServerDisplayActivity` | extra `shortcut_path` | MEDIUM |
| GameHub Lite | `emuready.gamehub.lite` | `/com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity` | action `gamehub.lite.LAUNCH_GAME` + `steamAppId`/`localGameId`, `autoStartGame` | MEDIUM |
| GameNative | `app.gamenative` | `/.MainActivity` | action `app.gamenative.LAUNCH_GAME` + `game_source`, `app_id` | HIGH |
| Xenia | – | – | no real Android build; use aX360e / XenDroid | n/a |
| Moonlight | `com.limelight` | launcher | n/a | HIGH |
| Steam Link | `com.valvesoftware.steamlink` | launcher | n/a | HIGH |

---

## 29. Open questions / things to verify on a device

1. ES-DE's exact `%ROMSAF%` URI format (document vs. tree URI, `primary%3A` encoding) – the Java layer is not public. Test with Dolphin/DuckStation, which log the URI they receive.
2. Whether Redream/DraStic accept a **FileProvider** content URI (Daijishō's `{file.uri}` failures on Android 13 suggest they may require the SAF document URI form or that the frontend lacked `<queries>` visibility).
3. Citron's activity name and whether it kept the yuzu `TECH_DISCOVERED` filter.
4. Pizza Boy: confirm ES-DE's `rom_uri` (SAF URI) still works on current builds; Argosy moved to `ACTION_VIEW` + FileProvider.
5. Whether current NetherSX2 builds honour `FLAG_GRANT_READ_URI_PERMISSION` on a `bootPath` extra (they cannot – grants only apply to data/clipData – so the user must add the ISO directory in the emulator).
