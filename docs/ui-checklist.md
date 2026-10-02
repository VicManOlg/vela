# Vela UI checklist

Every user-facing screen and function, as of 0.3.3. Walk it after each UI change: a redesign must
keep every line working, with the controller and by touch. Files are under `feature/*` unless noted.

## Navigation

- [ ] Destinations (`app/.../ui/VelaApp.kt`): Setup → Shell; Game grid (platform, collection,
      favorites, all); Android; Game detail (can stack another detail: other versions, franchise).
- [ ] Navigation from a screen that is not resumed is ignored (double press, mid-animation).
- [ ] Screen transitions follow theme motion; none under Reduce motion.
- [ ] Shell tabs: Home, Library, Collections, Search, Settings. Tab kept across process death;
      each tab keeps scroll and open pickers; tab switch animates and plays TAB.
- [ ] Home button: back to the Shell on the Home tab, even with a detail on top.
- [ ] B: on a tab other than Home goes to Home; on Home leaves; on pushed screens goes back.
- [ ] L1/R1 previous/next tab (wraps); SELECT → Search; L2/R2 Settings pages.
- [ ] Tab bar mode THEME / ALWAYS / HIDDEN; hidden tabs show mark, battery and clock.
- [ ] Top bar: mark, L1/R1 hints, tab chips (tap or focus), keel under the selected tab, battery, clock.
- [ ] Hint bars: Home A Play X Menu L1 Tabs · Library A Open L1 · Collections A Open X New L1 ·
      Search A Play X Type Y System L1 · Settings A Change L2 Pages L1 · Grid A Play X Menu Y Sort
      START View B Back · Android A Launch X Games Y Apps B Back · Detail A Select X Menu Y Favorite B Back.
- [ ] Every hint is tappable and acts as that button.

## Setup (`setup/SetupScreen.kt`)

- [ ] Welcome → Storage → Folders → Scanning → Done; B goes back a step (not on Welcome or while scanning).
- [ ] Storage: all-files access screen (rechecked on return), folder picker instead, Continue.
- [ ] Folders: up to 5 suggestions (with access) marked Added, Type a path, Browse, Skip / Scan N folders.
- [ ] Scanning: live source, files seen, games found.
- [ ] Done: Fetch box art and start / Start without artwork / Start; syncs Android apps.

## Home (`home/*`)

- [ ] Empty library state with Open settings. X = menu of the spotlighted game; long-press on cards.
- [ ] Spotlighted item drives the backdrop.
- [ ] Sections in the user's order, hidden ones removed, never empty: Continue playing, Recent,
      Favorites, Systems, Collections, Android games, Because you play, Recently added, Quick apps
      (Edit → Android screen), Your top rated.
- [ ] Layouts: Rails (header + rails, first rail focused), Spotlight (logo/title, facts, cover row,
      system chips), Tiles (tile row + round buttons to systems, Library, Collections, Search,
      Settings), Strip (tiles, focused game with Play/Continue + Details, chips), Dashboard (hero +
      tiles, Systems, Quick apps), Carousel (snapping tilted shelf, caption, Play/Details, chips).

## Library tab (`library/PlatformsScreen.kt`, `SystemStage.kt`, `SystemViews.kt`, `BookViews.kt`)

- [ ] Entries: All games, Favorites, each system with games, Android; optional user system art.
- [ ] Header (not in Stage, Wheel, Book); empty state "No systems yet" → Settings.
- [ ] Views: Stage (one system, dial, Left/Right, A opens, swipe, tap bead, tap stage), Showcase,
      Grid, Wheel, Mosaic, Columns, Book. Entrance stagger; focus memory.

## Collections (`library/CollectionsScreen.kt`)

- [ ] Count line, tiles (A opens grid), New collection; X creates; long-press → Rename / Delete
      (confirm, games stay); empty state focused; backdrop cleared.

## Search (`search/SearchScreen.kt`)

- [ ] Field wrapper focused first; A or X types; keyboard Search moves to results.
- [ ] Full-text title/genre, 180 ms debounce; system filter (Y or tap) cycles systems with matches.
- [ ] Genre chips (empty query); result count; results grid (A plays, long-press menu, backdrop).

## Settings (`settings/*`)

- [ ] Section list left, content right; focus selects; L2/R2 move section and focus.
- [ ] Library: folders (assign system, enable, rescan, remove + confirm), Add folder (suggested,
      path, browse), Scan now / cancel / last result, toggles: scan on startup, remove missing,
      show hidden, merge regional versions.
- [ ] Systems: with games / other; enable, Automatic, emulator (not installed disabled), core picker.
- [ ] Emulators: installed / supported, rechecked on open; details; Add your own (info).
- [ ] Metadata: fetch now / cancel / last result; auto-fetch, Wi-Fi only, videos, Wikipedia;
      metadata source; ScreenScraper user/password, SteamGridDB key; preferred region.
- [ ] Appearance: theme carousel (reload on open), reset tweaks; colours (accent, secondary,
      background, colour from artwork); background (scene, blur, dim, saturation); cards (size,
      shape, corners, titles, focus zoom, columns); text (fonts, size); effects (glass, opacity,
      shadow, glow, reduce motion); layout (Home layout, Home sections editor, Systems view,
      Library view, tab bar); interface (size, console icons, margins, hints, clock, battery);
      sound (on, volume); theme files (import, remove custom). Steppers Left/Right; "Theme default".
- [ ] Controller: layout, confirm swap, analog sticks, vibration, hold-to-scroll speed, button map.
- [ ] Android apps: detect games, system apps in picker. Storage: all files access, preferred
      access, artwork size. Advanced: home app role, default home app, Android settings, About.

## Game grid (`library/GameGridScreen.kt`, `GameViews.kt`, `BookViews.kt`)

- [ ] Header with title, subtitle or focused game, tappable "View · Sorted by".
- [ ] X menu, Y cycle sort, START Display menu (views + Sort by…); view saved globally.
- [ ] Sorts: Title, Last played, Most played, Recently added, Release year, Rating, Your rating.
- [ ] Empty state (Favorites differs). A launches, long-press menu, focus → backdrop and header.
- [ ] Views: Grid, Compact, List (+ preview panel), Showcase (tilted wheel), Hero, Wall, Details
      (table), Book. Mixed lists show the system label.

## Game detail (`library/GameDetailScreen.kt`)

- [ ] Box art or app icon; entrance animation (none under Reduce motion); logo or title.
- [ ] Pills: system, year, genre, players, rating, age rating, completion.
- [ ] Play/Continue (focused), Favorite, Collections, Launch with (ROMs), More.
- [ ] Rating stars: D-pad preview, confirm sets, confirm again clears; taps.
- [ ] Stats (last played, play time, launches, emulator, red when missing); facts panel;
      description with Read more and Wikipedia credit; file info (ROMs).
- [ ] Other versions and franchise rails open details. X menu, Y favorite. Hide closes the screen;
      closes itself when the game disappears.

## Android (`apps/AndroidScreen.kt`)

- [ ] Header with counts, green backdrop; Games rail + Edit or empty state; Apps rail + Edit or
      suggestions + More…; X game picker, Y app picker (Shown / other section / detected); sync on open.

## Overlays

- [ ] Game menu: Play, Details, Favorite, Collections (toggles + New), Launch with (emulator · core,
      not installed disabled, current marked), Status, Rate (5..1, none), Refresh metadata, Hide (confirm).
- [ ] Launch overlay: logo or title, "Launching in …", pulse, LAUNCH sound; clears on return or timeout.
- [ ] Toast 3.2 s, red for errors. Menu dialogs (right panel, B or outside closes, own the buttons,
      B Back footer), Confirm (Cancel first), Text input (Done confirms, blank disabled), Swatches.

## Input and system

- [ ] A/B via Android (confirm swap applied first); MENU = START; sticks and hat = D-pad with
      repeat timings; analog triggers = L2/R2; long-press A 450 ms or touch = menu.
- [ ] Touch: hints, grid header label, Stage swipe/beads/tap, outside-dialog tap, tab chips.
- [ ] Backdrop modes hero / artwork / platform / static, crossfade, Ken Burns, vignette, dim.
- [ ] Colour from artwork (ArtworkPalette) when the theme or user enables it.
- [ ] Sounds FOCUS, CONFIRM, BACK, TAB, LAUNCH; haptics; clock, battery; focus memory;
      reduce motion; UI scale.

## Known gaps before the redesign (not regressions)

1. `videoPreviews` is stored but nothing plays a video.
2. Launch with says "hold to make default" but holding does nothing.
3. Controller > Button map says Start is the sort picker; it opens the Display menu.
4. Detail `openCompletion` / `refreshMetadata` and Home `rescan` have no button of their own.
5. Grid sort resets to Title every time.
