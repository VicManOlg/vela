# Android platform research for a gamepad-first emulation frontend (Kotlin + Jetpack Compose)

Date: 2026-09-17
Scope: platform facts needed to build a 100% gamepad-controllable frontend that can also be the Android HOME app, targeting handhelds with built-in controllers (AYN Odin 2, Retroid Pocket, AYN) as well as phones/tablets with paired controllers.

Legend used throughout:

- **[CONFIRMED]** – read directly in official docs or AOSP/AndroidX source during this research (URL given).
- **[REPORTED]** – from community sources (issue trackers, project docs, forum posts); reliable but not from Google.
- **[UNVERIFIED]** – from prior knowledge of the framework source or behaviour; consistent with everything read, but not re-checked in this session. Verify on-device before relying on it.

---

## 1. Game controller input on Android

### 1.1 Key codes and axes (buttons vs. analog)

**[CONFIRMED]** Source: https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input

| Physical control | Delivered as | Constant(s) |
|---|---|---|
| Face buttons | `KeyEvent` | `KEYCODE_BUTTON_A`, `_B`, `_X`, `_Y` |
| Bumpers | `KeyEvent` | `KEYCODE_BUTTON_L1`, `KEYCODE_BUTTON_R1` |
| Triggers | `MotionEvent` axis and/or `KeyEvent` | `AXIS_LTRIGGER`/`AXIS_RTRIGGER` (0.0–1.0), always duplicated as `AXIS_BRAKE`/`AXIS_GAS`; some controllers also/only send `KEYCODE_BUTTON_L2`/`_R2` |
| Stick clicks | `KeyEvent` | `KEYCODE_BUTTON_THUMBL`, `KEYCODE_BUTTON_THUMBR` |
| Start / Select | `KeyEvent` | `KEYCODE_BUTTON_START`, `KEYCODE_BUTTON_SELECT` |
| Mode / guide / home-glyph | `KeyEvent` | `KEYCODE_BUTTON_MODE` ("On a game controller, the button labeled Mode") |
| Left stick | `MotionEvent` | `AXIS_X`, `AXIS_Y` (−1.0..1.0) |
| Right stick | `MotionEvent` | `AXIS_Z`, `AXIS_RZ` |
| D-pad | `KeyEvent` **or** `MotionEvent` | `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT` **or** `AXIS_HAT_X`/`AXIS_HAT_Y` (exactly −1.0, 0.0, 1.0) |

Direct quotes worth remembering:

- "Some controllers have left and right shoulder triggers. When these triggers are present, they emit an `AXIS_*TRIGGER` or `KEYCODE_BUTTON_*2` event or both... Games must support both `AXIS_` and `KEYCODE_BUTTON_` events to remain compatible with all common game controllers."
- "All controllers that send `AXIS_LTRIGGER` will also send `AXIS_BRAKE`, similarly for `AXIS_RTRIGGER` and `AXIS_GAS`."
- "Some controllers instead report D-pad presses with a key code... you should treat the hat axis events and the D-pad key codes as the same input events."
- "The button positions on a controller will remain consistent across controllers (For example, `KEYCODE_BUTTON_A` is always the south most button even if it's labelled 'B') as long as you use `keyCode`." Switch-style controllers therefore report the button labelled **B** as `KEYCODE_BUTTON_A`.
- Android "reuses the same key or axis ids for different input device types... you must check the source type to properly interpret input events."
- "Android has compatibility layers to help games and applications by mapping newer or less common input types into more common or legacy input types. When receiving events, avoid marking events as handled (returning true) unless that event is used" – this is the fallback mechanism described in 1.4; consuming everything breaks it.

Compose exposes the same codes as `androidx.compose.ui.input.key.Key.ButtonA`, `Key.ButtonB`, `Key.ButtonL1`, `Key.ButtonMode`, `Key.DirectionUp`, etc.; `keyEvent.nativeKeyEvent` gives the framework `KeyEvent` (source, repeatCount, deviceId). **[UNVERIFIED for the exact constant list, but the `Key` object mirrors `KeyEvent.KEYCODE_*`]** Reference: https://developer.android.com/reference/kotlin/androidx/compose/ui/input/key/Key

### 1.2 Detecting controllers

**[CONFIRMED]** (same page) Enumerate `InputDevice.getDeviceIds()` and keep devices where `supportsSource(SOURCE_GAMEPAD) || supportsSource(SOURCE_JOYSTICK)`. `SOURCE_GAMEPAD` = has gamepad buttons, `SOURCE_JOYSTICK` = has analog sticks, `SOURCE_DPAD` = has a D-pad. Filter events with `event.isFromSource(SOURCE_GAMEPAD)` / `SOURCE_JOYSTICK`. Register an `InputManager.InputDeviceListener` to react to hot-plug (the doc has a section "Verify a game controller is connected" / `onInputDeviceAdded/Removed/Changed`).

Deadzones: "A joystick at rest does not always report an absolute position of (0,0). Use the `getFlat()` method to determine the range of values bounding the joystick axis center." (`InputDevice.getMotionRange(axis, source).flat`). Stick events arrive as `ACTION_MOVE` with batched history: iterate `0 until event.historySize` with `getHistoricalAxisValue`, then the current sample.

### 1.3 Key repeat: does the system auto-repeat gamepad buttons?

**[CONFIRMED]** Yes. The official controller guide's `onKeyDown` sample explicitly checks `repeatCount == 0` for `SOURCE_GAMEPAD` events with the comment "avoid processing the keycode repeatedly". `KeyEvent` class docs: "A key press starts with a key event with ACTION_DOWN. If the key is held sufficiently long that it repeats, then the initial down is followed additional key events with ACTION_DOWN and a non-zero value for getRepeatCount(). The last key event is a ACTION_UP." `FLAG_LONG_PRESS` is "Set for the first key repeat that occurs after the long press timeout." Sources: controller guide above; https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/view/KeyEvent.java

**[CONFIRMED]** Timing (`ViewConfiguration.java`, https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/view/ViewConfiguration.java):

- `DEFAULT_KEY_REPEAT_TIMEOUT_MS` = 400 ms ("Historically, Android used the long press timeout as the key repeat timeout, so its default value is set to long press timeout's default").
- `DEFAULT_KEY_REPEAT_DELAY_MS` = 50 ms between repeats.
- Both are user-configurable via `Settings.Secure.KEY_REPEAT_TIMEOUT_MS` / `KEY_REPEAT_DELAY_MS` (read through `getKeyRepeatTimeout()` / `getKeyRepeatDelay()`); long-press timeout via `Settings.Secure.LONG_PRESS_TIMEOUT` (default 400 ms).

**[REPORTED]** Repeats are synthesized by the native `InputDispatcher` only when the down has `POLICY_FLAG_TRUSTED` and not `POLICY_FLAG_DISABLE_KEY_REPEAT`; a hardware-generated repeat cancels the synthetic timer; synthesis only happens when the dispatch queue is idle. Source (Chinese-language AOSP walk-throughs): https://blog.csdn.net/littleyards/article/details/139140759 , https://zhuanlan.zhihu.com/p/255133156

**[UNVERIFIED]** Pressing a second key resets repeat of the first (`resetKeyRepeatLocked`), so holding D-pad while tapping A stops the D-pad repeat until released/re-pressed. Device-specific input device configuration (`.idc`) files can set `keyboard.handlesKeyRepeat`, which disables system repeat for that device. Implication: **do not rely on system repeat for hold-to-scroll**; drive scrolling from your own timer keyed to DOWN/UP (see 2.9).

### 1.4 What Android does by default with gamepad buttons: the `Generic.kcm` fallbacks

**[CONFIRMED]** `frameworks/base/data/keyboards/Generic.kcm` (https://android.googlesource.com/platform/frameworks/base/+/main/data/keyboards/Generic.kcm) declares:

```
key BUTTON_A      { base: fallback DPAD_CENTER }
key BUTTON_B      { base: fallback BACK }
key BUTTON_C      { base: fallback DPAD_CENTER }
key BUTTON_X      { base: fallback DEL }
key BUTTON_Y      { base: fallback SPACE }
key BUTTON_Z      { base: fallback DPAD_CENTER }
key BUTTON_THUMBL { base: fallback DPAD_CENTER }
key BUTTON_THUMBR { base: fallback DPAD_CENTER }
key BUTTON_START  { base: fallback DPAD_CENTER }
key BUTTON_SELECT { base: fallback MENU }
key BUTTON_MODE   { base: fallback HOME }
key BUTTON_1..16  { base: fallback DPAD_CENTER }
```

Note: `BUTTON_L1`, `BUTTON_R1`, `BUTTON_L2`, `BUTTON_R2` have **no** fallback – if you don't handle them, nothing happens.

**[CONFIRMED]** Mechanism: `KeyCharacterMap.getFallbackAction()` javadoc: "When an application does not handle a particular key, the system may translate the key to an alternate fallback key (specified in the fallback action) and dispatch it to the application. The event containing the fallback key is flagged with `KeyEvent#FLAG_FALLBACK`." `KeyEvent.FLAG_FALLBACK`: "Set when a key event has been synthesized to implement default behavior for an event that the application did not handle." Sources: https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/view/KeyCharacterMap.java , KeyEvent.java above.

**[REPORTED/UNVERIFIED]** The fallback is generated in `PhoneWindowManager.dispatchUnhandledKey()` → `interceptFallback()` after the app's window reports the original event as *unhandled*; the lookup happens on the initial `ACTION_DOWN` with `repeatCount == 0` and is cached so repeats and the `ACTION_UP` get matching fallbacks. Source: https://blog.csdn.net/jwg1988/article/details/123631476 (walk-through of `getFallbackAction` in `PhoneWindowManager`).

Practical consequences for us:

1. If the app does **not** consume `BUTTON_A`, the window receives a second, synthesized `KEYCODE_DPAD_CENTER` event (with `FLAG_FALLBACK`). Compose's `clickable` reacts to `DPAD_CENTER` (see 2.3), so **A "just works" as Confirm on any clickable/Button** with zero code.
2. Same for `BUTTON_B` → `KEYCODE_BACK` → `OnBackPressedDispatcher` / Compose `BackHandler`.
3. `BUTTON_START` → `DPAD_CENTER` and `BUTTON_SELECT` → `MENU` – both usually harmless, but if you want Start = "open menu", consume `BUTTON_START` yourself; otherwise it will also click the focused item.
4. `BUTTON_MODE` → `KEYCODE_HOME`. **[CONFIRMED]** `KEYCODE_HOME`: "This key is handled by the framework and is never delivered to applications." So an unhandled Mode/guide button behaves like the hardware Home key: it goes to the system, which launches the HOME activity. If our app **is** the HOME app, a Home press brings us to front / delivers `onNewIntent` (see 3.3). If the app *consumes* `BUTTON_MODE` (returns true), the fallback is not generated and Home is not triggered. On devices where the "home" glyph button is wired as `KEYCODE_HOME` at the kernel/keylayout level (rather than `BUTTON_MODE`), the app never sees it at all.
5. If you handle a key **only in `onKeyEvent` and return `true`**, you suppress the fallback. This is the single most common reason gamepad-navigated Compose apps "stop responding to A": some ancestor consumes `ButtonA` for another purpose.
6. `BUTTON_X` → `DEL` and `BUTTON_Y` → `SPACE`: `SPACE` is one of the keys Compose `clickable` treats as a click (2.3), so **an unhandled Y press will also click the focused item**. Consume X/Y deliberately if they mean something else.

### 1.5 Touch mode vs. key mode (framework level)

**[UNVERIFIED – from prior reading of `ViewRootImpl`]** In `ViewRootImpl`'s `EarlyPostImeInputStage.processKeyEvent`, `checkForLeavingTouchModeAndConsume()` runs before the key reaches the view: if the window is in touch mode and a *navigation key* (`DPAD_UP/DOWN/LEFT/RIGHT/CENTER`, `MOVE_HOME/END`, `PAGE_UP/DOWN`, `SPACE`, `ENTER`, `TAB`) is pressed, the window leaves touch mode and, if that results in a view gaining focus, **that first key press is consumed** (the user sees "first D-pad press does nothing except show focus"). "Typing keys" (with a unicode char) leave touch mode without being consumed. Gamepad `BUTTON_*` keys are neither, so a bare `BUTTON_A` in touch mode is delivered normally – but its `DPAD_CENTER` fallback *is* a navigation key. Community write-up: https://medium.com/@wanxiao1994/how-android-dispatch-keyevent-and-perform-focus-navigation-8565327bd12e ; historical AOSP: https://android.googlesource.com/platform/frameworks/base/+/android-4.2.2_r1.2/core/java/android/view/ViewRootImpl.java

Compose mirrors this state as `InputMode.Touch` / `InputMode.Keyboard` (2.5), so a Compose app sees the same "first press exits touch mode" behaviour.

### 1.6 Built-in controllers on AYN Odin 2 / Retroid Pocket – known quirks

**[REPORTED]**

- Odin 2 firmware exposes a **"controller style" (Odin vs. Xbox)** and an **L2/R2 mode (Analog / Digital / Both)**; OdinTools adds quick-settings tiles and per-app overrides for both. OdinTools also has a "single press home button setting to allow going to the home screen by a single press of the home button", implying the stock firmware requires a **double press** of the home glyph to go Home (single press is reserved by AYN's own software). Sources: https://github.com/langerhans/OdinTools , https://github.com/dfdevx2/OdinTools , https://retrohandhelds.gg/ayn-odin-2-portal-setup-guide/ , https://www.joeysretrohandhelds.com/guides/odintools-setup-guide/
- Odin (Lite/original) **M3 = a "back" button on the left side**; PPSSPP issue #17245: "Every time I press the button marked as M3 (i.e. the back one on the left side), the emulator exits" – i.e. it arrives as (or falls back to) `KEYCODE_BACK`. Source: https://github.com/hrydgard/ppsspp/issues/17245
- M1/M2 (back paddles) can be remapped to face buttons in firmware; the default mapping is device-specific. Source: OdinTools README above.
- Retroid Pocket Flip has a setting "Prevent press the Home button accidentally" which, when enabled, requires a long/double press; users disable it so a single press returns to the frontend. The default Home app is changed under Settings → Apps → Default apps → Home app. Sources: https://www.adinwalls.com/2023/03/22/retroid-pocket-flip-3-ultimate-setup-guide/ , https://retrogamecorps.com/2022/01/16/retroid-pocket-2-starter-guide/
- Retroid inputs work in three layers: "system buttons handled by Android, emulator specific bindings, and per game overrides"; third-party remapping apps turn the built-in pad into a "virtual" controller that some games mis-detect. Source: https://pulsegeek.com/articles/retroid-pocket-button-mapping-and-controls-master-guide/
- Pegasus on Android reports **double input when the D-pad and left stick both generate navigation** ("a single input is recognized but outputs both a d-pad press and analog stick input") – exactly the class of bug our stick-to-dpad layer must avoid (issue #328). Source: https://github.com/mmatyas/pegasus-frontend/issues/328
- ES-DE on Android: the Home button "is equivalent to pressing Alt+tab on a desktop operating system" (app pauses but doesn't close); the Back button is regular navigation. Source: https://gitlab.com/es-de/emulationstation-de/-/blob/master/FAQ-ANDROID.md

**[UNVERIFIED]** Whether Odin 2's home glyph reaches apps as `KEYCODE_BUTTON_MODE` or is intercepted as `KEYCODE_HOME` depends on the firmware key layout (`.kl`) and on the "controller style". Whether the second controller (docked/USB) on Odin 2 gets a different mapping is a known complaint (XDA thread https://xdaforums.com/t/ayn-odin2-trying-to-fix-controller-mapping-for-controller-2.4735454/). **Design for this by shipping an in-app "press each button" calibration screen that stores per-`InputDevice` (vendorId/productId/descriptor) mappings**, rather than hard-coding a device table.

---

## 2. Jetpack Compose focus and D-pad navigation

### 2.1 What Compose does with D-pad keys

**[CONFIRMED]** Official focus docs (https://developer.android.com/develop/ui/compose/touch-input/focus):

- "Arrow keys: two-dimensional navigation, going left, right, up, or down. Two-dimensional navigation can be achieved through a D-Pad on a TV or arrow keys on a keyboard, and its traversal order only visits elements at a given level." "You can use the D-Pad center and Back button to go down and back up to a different level."
- TAB/`Next`/`Previous` is 1-D traversal in declaration order.
- "Focus properties are a special case, in which the parents always win in case of collisions or duplicates."

**[CONFIRMED]** `AndroidComposeView.dispatchKeyEvent` → `focusOwner.dispatchKeyEvent(...) || super.dispatchKeyEvent(event)`. `FocusOwnerImpl.dispatchKeyEvent` traverses ancestors of the focused node: first `onPreKeyEvent` from outermost ancestor inward, then the focused node, then `onKeyEvent` outward; "Only if all handlers decline does the system proceed to default focus movement via `moveFocus()`." 1-D search (`Next`/`Previous`) wraps around; 2-D does not. Sources: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidComposeView.android.kt , https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusOwnerImpl.kt

**[UNVERIFIED]** The key→direction map is `DirectionUp/PageUp→Up`, `DirectionDown/PageDown→Down`, `DirectionLeft→Left`, `DirectionRight→Right`, `Tab→Next/Previous`, `DirectionCenter/Enter/NumPadEnter→Enter`, `Back/Escape→Exit`. In current versions a Back press is only consumed as `Exit` when a custom `exit` is defined in `focusProperties` (otherwise Back propagates to `BackHandler`). **Test this on-device**: if a `focusGroup()` ever swallows B/Back, add `focusProperties { exit = { FocusRequester.Default } }` or handle `Key.Back` in `onPreviewKeyEvent` at the screen root.

If Compose's `moveFocus` fails (e.g. nothing focusable in that direction), the event is returned unconsumed to the View system, then to the Activity (`onKeyDown`), and finally the framework's fallback logic (1.4) runs. So `Activity.onKeyDown`/`dispatchKeyEvent` still see events Compose didn't consume.

### 2.2 Focus modifiers you will use

**[CONFIRMED]** https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior and https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-traversal-order

- `Modifier.focusable()` – makes a node a focus target; emits `FocusInteraction.Focus/Unfocus` into an `InteractionSource`; **when it gains focus it calls `bringIntoView()` on itself** ("Focusable node newly receives focus – always bring entire node into view. That's what this BringIntoViewRequester does."). That is why `LazyRow`/`LazyColumn` auto-scroll to the focused child. Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Focusable.kt
- `Modifier.focusRequester(fr)` – must be placed **before** the `focusable`/`clickable` in the chain ("A focusRequester is associated with the first focusable element below it"). Call `requestFocus()` outside composition (e.g. `LaunchedEffect`).
- `Modifier.focusProperties { up/down/left/right/next/previous = fr; enter = { dir -> ... }; exit = { dir -> ... }; canFocus = false }`. `enter`/`exit` lambdas return `FocusRequester.Cancel` (block movement), `FocusRequester.Default` (system behaviour) or a specific requester. Parents override children.
- `Modifier.focusGroup()` – "makes a whole group appear like a single entity in terms of focus, but the group itself will not get the focus— instead, the closest child will gain focus". Prevents 2-D search from jumping out of a row to a visually closer item in another row.
- `Modifier.focusRestorer(fallback: FocusRequester = Default)` – "This modifier can be used to save and restore focus to a focus group. When focus leaves the focus group, it stores a reference to the item that was previously focused. Then when focus re-enters this focus group, it restores focus to the previously focused item." The older `focusRestorer(onRestoreFailed: (() -> FocusRequester)?)` is `@Deprecated("Use focusRestorer(FocusRequester) instead")`. Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/FocusRestorer.kt . Apply it to each `LazyRow` (and to the outer `LazyColumn`) to get "remember last focused card per row".
- `Modifier.onFocusChanged { it.isFocused / hasFocus / isCaptured }` and `Modifier.onFocusEvent`. `hasFocus` = self or a descendant is focused. Source: https://developer.android.com/develop/ui/compose/touch-input/focus/react-to-focus
- `interactionSource.collectIsFocusedAsState()` – preferred when you already pass an `InteractionSource` to `clickable`. Source: https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions
- `LocalFocusManager.current.moveFocus(FocusDirection.X)` – programmatic navigation (this is what you call from a stick-to-dpad layer).
- `Modifier.bringIntoViewRequester(requester)` + `requester.bringIntoView()` – explicit scroll-into-view for non-focus cases (e.g. after data reload).

### 2.3 `clickable` and gamepad buttons

**[CONFIRMED]** `Clickable.kt`: a key press counts as click when `type == KeyDown/KeyUp` and the key is one of `Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar`. `clickable` creates its `FocusableNode` with `Focusability.SystemDefined`. Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Clickable.kt

**[CONFIRMED]** `Focusability.SystemDefined`: "This should be used for clickable components such as buttons and checkboxes: these components should only gain focus when they are used with certain types of input devices, such as keyboard / d-pad." I.e. **in touch mode, `clickable`/`Button` are not focusable at all**; `Focusability.Always` (text fields) and plain `Modifier.focusable()` are. Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/focus/Focusability.kt

Consequences: `BUTTON_A` itself is not a click key, but its `DPAD_CENTER` fallback is (1.4). `BUTTON_Y`'s `SPACE` fallback is also a click key. `BUTTON_START`'s fallback is `DPAD_CENTER` too.

### 2.4 `onPreviewKeyEvent` vs `onKeyEvent`

**[CONFIRMED]** https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands : "The `onPreviewKeyEvent()` lambda is called on the parent component first, then `onPreviewKeyEvent()` in the child component is called." "Unconsumed key events are propagated from the component where the event occurred to the enclosing outer component." Return `true` to consume. Key events reach only the focused node and its ancestors, so a root-level `onPreviewKeyEvent` sees everything **only while something in the tree has focus**; put a `focusable()` root or request initial focus.

### 2.5 Input mode (touch vs. keyboard) and hidden focus

**[CONFIRMED]** `InputModeManager` "is accessible as a CompositionLocal, that provides the current InputMode". `InputMode.Touch`: "The system is put into Touch mode when a user touches the screen." `InputMode.Keyboard`: "...when a user presses a hardware key." `requestInputMode()` "may not succeed, depending on platform implementation." On Android it is initialised from `View.isInTouchMode` and updated via `ViewTreeObserver.OnTouchModeChangeListener`; requesting `Keyboard` calls `requestFocusFromTouch()`. Sources: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/input/InputModeManager.kt , AndroidComposeView above.

Implications for a handheld with a touchscreen: the moment the user touches the screen, clickables lose focusability and any visible focus ring disappears; the next D-pad press switches back to `Keyboard` mode (possibly consumed by the framework, 1.5). Read `LocalInputModeManager.current.inputMode` to decide whether to draw focus highlights and whether to auto-focus the first card.

### 2.6 Lazy lists: the disposal problem and current solutions

**[CONFIRMED]** Compose Foundation 1.7+: "standard lazy layouts (like `LazyRow` and `LazyColumn`) include built-in support for focus-positioning features... These components automatically handle D-pad navigation and bring the focused item into view." Pivot ("focused item at 30% from the left edge") is done with `BringIntoViewSpec` provided through `LocalBringIntoViewSpec` (replaces `TvLazyRow`'s `pivotOffsets`); the doc includes a full `PositionFocusedItemInLazyLayout(parentFraction, childFraction)` sample and an opt-out by providing an empty spec. Source: https://developer.android.com/training/tv/playback/compose/lists

**[REPORTED]** Known pitfalls:

- Lazy layouts dispose off-screen children; focus cannot be moved to a composable that does not exist. `focusRestorer` restoring to an item that has scrolled off-screen can fail (and older versions crashed with `FocusRequesterNotInitialized`); `BringIntoViewRequester` alone does not stop the list from disposing the target. Sources: https://slack-chats.kotlinlang.org/t/16379255/seems-there-is-a-bug-with-focusrequester-modifier-it-s-in-ex , https://issuetracker.google.com/issues/179203700 , https://issuetracker.google.com/issues/184670295
- Combining `focusRestorer { firstItem }` on a `LazyRow` with per-item `onPreviewKeyEvent` handlers that call `moveFocus(Up)` produced "pressing dPad up results in focusing the first item" (tv-samples #194). Source: https://github.com/android/tv-samples/issues/194
- Compose UI release notes mention a fix for "focusRestorer not properly restoring focus when multiple save calls occur for the same layout" – keep Compose UI ≥ 1.8/1.9. Source: https://developer.android.com/jetpack/androidx/releases/compose-ui

Mitigations: (a) for "jump to index N" first `lazyListState.scrollToItem(N)` (or `animateScrollToItem`), wait one frame (`withFrameNanos`/`snapshotFlow { layoutInfo }`), then `requestFocus()` on that item's `FocusRequester`; (b) give items stable `key`s so `rememberSaveable` state survives; (c) keep `focusRestorer` on the container, not on items; (d) for grids of thousands of items prefer a **single `LazyVerticalGrid`** per screen and handle up/down at the edge yourself.

### 2.7 Compose for TV (`androidx.tv`) – what it offers, and is it usable on a handheld?

**[CONFIRMED]** Releases page (https://developer.android.com/jetpack/androidx/releases/tv): `androidx.tv:tv-material:1.1.0` (stable) and `androidx.tv:tv-foundation:1.0.0` (stable) as of May 2026. "Tv Lazy Layouts have been deprecated from tv-foundation library" (1.0.0-alpha11, July 2024); "Cleanup lazy layouts from tv-foundation" (1.0.0-alpha12, Jan 2025) – `TvLazyRow/Column/Grid` are gone; migration is "swap `TvLazy*` for `Lazy*`, `rememberTvLazy*State` for `rememberLazy*State`, `pivotOffsets` for `BringIntoViewSpec`". Sources: releases page, https://issuetracker.google.com/issues/348896032 , https://github.com/android/tv-samples/issues/178

`tv-material` 1.1.0 components: Carousel, NavigationDrawer/ModalNavigationDrawer, Button/OutlinedButton/IconButton/WideButton, Card/ClassicCard/CompactCard/WideClassicCard/StandardCardContainer/WideCardContainer, Surface (interactive, focus-scale built in), ListItem/DenseListItem, TabRow/Tab, RadioButton/Switch/Checkbox, Chips, Text/Icon. `ImmersiveList` was removed in beta01 (sample exists in androidx samples).

**[REPORTED / judgement]** Nothing technically prevents using `tv-material` on a phone/handheld: it is plain Compose with `minSdk 21` and its own `MaterialTheme` (`androidx.tv.material3`). Google's guidance is only that each library is *optimised* for its form factor. Trade-offs: tv-material's `Surface`/`Card` give you focus-scale/border/glow "for free" and are designed for D-pad, but they are sized for 10-foot UIs, don't integrate with `androidx.compose.material3` theming, and their interactive components assume focus-driven input (touch works but ripple/pressed states are TV-styled). Source: https://developer.android.com/training/tv/playback/compose , https://developer.android.com/training/tv/get-started/libraries

Recommendation: use **Foundation `Lazy*` + our own focusable card** (with `interactionSource`, `graphicsLayer` scale, border) rather than `tv-material`, and borrow ideas (pivot spec, Carousel) from the TV samples. That keeps M3 theming for touch fallback and avoids two Material systems.

### 2.8 Activity-level events vs Compose

- `Activity.dispatchKeyEvent` runs **before** the Compose view (Window → DecorView → `AndroidComposeView`), `Activity.onKeyDown/onKeyUp` run **after** Compose declined. Compose gets the event through `AndroidComposeView.dispatchKeyEvent` (2.1).
- **Compose has no API for joystick axes**; `AndroidComposeView.dispatchGenericMotionEvent` only handles rotary and pointer events. Analog sticks and `AXIS_HAT_*` must be handled in `ComponentActivity.onGenericMotionEvent` (or `dispatchGenericMotionEvent`) and forwarded into Compose as state/`FocusManager.moveFocus` calls. **[CONFIRMED that Compose only has rotary handling in that path; UNVERIFIED that no other axis handling exists in newer versions]** Source: AndroidComposeView above; controller guide `onGenericMotionEvent` sample.
- A clean architecture: a `GamepadInputController` owned by the Activity that (1) receives raw `KeyEvent`/`MotionEvent` in `dispatchKeyEvent`/`onGenericMotionEvent`, (2) normalises per-device mappings, deadzones and repeat, (3) emits **semantic actions** (`Confirm`, `Back`, `Menu`, `PageLeft`, `NavUp`...) into a `SharedFlow`, and (4) Compose screens collect them. Physical D-pad `KEYCODE_DPAD_*` should generally be left to Compose's own focus system (return false), while the stick emulates D-pad by calling `focusManager.moveFocus()`.

### 2.9 Stick-to-D-pad with deadzone and repeat; "hold to fast scroll" in ES-DE/Pegasus

**[CONFIRMED]** ES-DE (`es-core/src/components/IList.h`, https://gitlab.com/es-de/emulationstation-de/-/raw/master/es-core/src/components/IList.h) implements acceleration tiers `{length ms, scrollDelay ms}`:

- `QUICK_SCROLL_TIERS = {500, 500}, {1200, 114}, {0, 16}`
- `MEDIUM_SCROLL_TIERS = {500, 500}, {1100, 180}, {0, 80}`
- `SLOW_SCROLL_TIERS = {500, 500}, {0, 200}`

Reading: after the initial press (which moves once), hold for 500 ms → start stepping every 500 ms; after another 1200 ms → every 114 ms; then every 16 ms (one item per frame) until release. `listInput()` sets `mScrollVelocity` and resets `mScrollTier`, `mScrollTierAccumulator`, `mScrollCursorAccumulator`; `listUpdate(deltaTime)` advances the accumulators, steps the cursor when `mScrollCursorAccumulator >= scrollDelay`, and promotes the tier when `mScrollTierAccumulator >= length`. ES-DE also exposes a user setting to pick quick/medium/slow.

**[REPORTED]** Pegasus converts axis movement to navigation key events with a threshold and has had Android bugs where D-pad and stick both produce navigation (double input). Source: https://github.com/mmatyas/pegasus-frontend/issues/328 , https://github.com/mmatyas/pegasus-frontend/wiki/Controls

Recommended algorithm for us (Kotlin, in the Activity-owned controller):

1. Per axis pair, read `x`, `y` (`AXIS_X/Y` for left stick, optionally `AXIS_Z/RZ` for right stick as page/section navigation). Apply a **radial** deadzone: `mag = hypot(x, y)`; ignore if `mag < max(0.25f, flat)`; use hysteresis (press at 0.5, release at 0.3) to avoid chatter at the threshold.
2. Convert to one of 4 directions (dominant axis, or 8-way if diagonals are wanted). Emit `NavPress(dir)` on entering the zone, `NavRelease` on leaving. Treat `AXIS_HAT_X/Y` identically but with thresholds at ±0.5 (values are exactly ±1.0).
3. Deduplicate: keep one "held direction" state shared by D-pad keys, hat axes and stick, so a controller that reports the D-pad both as `KEYCODE_DPAD_*` and `AXIS_HAT_*` (common) doesn't double-step. Only the *first* source to assert a direction owns it until release.
4. Repeat: on press, move once; then a coroutine with ES-DE-style tiers (`500ms → 500ms`, then `114ms`, then a per-frame tier capped by list size). Cancel on release or on any other key down. Do **not** use system key repeats (`repeatCount > 0` → ignore).
5. For each step call `focusManager.moveFocus(direction)`; if it returns false (edge of list), stop the repeat to avoid burning frames.
6. Optional "fast tier" skips: in ES-DE's quick tier at 16 ms the cursor moves 60 items/s; for grids of 10k games also offer L1/R1 = page or letter jump (ES-DE binds shoulder buttons to jump ±10 / letters).

---

## 3. Acting as the Android HOME launcher

### 3.1 Manifest

**[CONFIRMED]** AOSP Launcher3 declares (https://android.googlesource.com/platform/packages/apps/Launcher3/+/main/AndroidManifest.xml):

```xml
<activity android:name="com.android.launcher3.Launcher"
    android:launchMode="singleTask"
    android:clearTaskOnLaunch="true"
    android:stateNotNeeded="true"
    android:windowSoftInputMode="adjustPan"
    android:screenOrientation="unspecified"
    android:configChanges="keyboard|keyboardHidden|mcc|mnc|navigation|orientation|screenSize|screenLayout|smallestScreenSize"
    android:resizeableActivity="true"
    android:resumeWhilePausing="true"
    android:taskAffinity=""
    android:exported="true" android:enabled="true">
  <intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <action android:name="android.intent.action.SHOW_WORK_APPS" />
    <category android:name="android.intent.category.HOME" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.MONKEY"/>
    <category android:name="android.intent.category.LAUNCHER_APP" />
  </intent-filter>
</activity>
```

Comment in the file: "When extending only change the name, and keep all the attributes and intent filters the same". Launcher3 does **not** set `excludeFromRecents` (the system already hides the home task from Recents).

**[CONFIRMED]** Attribute meanings (https://developer.android.com/guide/topics/manifest/activity-element):

- `stateNotNeeded`: "Whether the activity can be terminated and successfully restarted without having saved its state." The system may kill/restart the home activity freely; **persist UI state (selected system, scroll index) yourself**, e.g. in DataStore, not only in `SavedStateHandle`.
- `clearTaskOnLaunch`: "Whether all activities are removed from the task, except for the root activity, when it is re-launched from the home screen." Ensures pressing Home from a settings sub-screen returns to the root.
- `launchMode="singleTask"`: existing instance receives `onNewIntent()` instead of being recreated.
- `excludeFromRecents`: hides the task from Recents if it is the root of a new task.
- `taskAffinity=""`: keeps launched apps out of the launcher's task.

Recommendation: copy Launcher3's attributes verbatim; additionally handle `Intent.CATEGORY_HOME` arrival in `onNewIntent` to "go to root screen" (Home-press behaviour, 3.3). Keep a **separate** `LAUNCHER` intent-filter (or an `<activity-alias>`) so the app is still launchable when it is *not* the default home. Being both `HOME` and `LAUNCHER` on the same activity is what Launcher3 does with `LAUNCHER_APP`; a plain `CATEGORY_LAUNCHER` on the same activity is also fine.

### 3.2 Becoming / un-becoming the default home

**[CONFIRMED]** `RoleManager` (Android 10+, `android.app.role`, now shipped in the Permission mainline module: https://android.googlesource.com/platform/packages/modules/Permission/+/main/framework-s/java/android/app/role/RoleManager.java):

- `ROLE_HOME = "android.app.role.HOME"` – "The name of the home role."
- `createRequestRoleIntent(roleName)` returns an Intent "which prompts the user to grant a role to this application"; result `RESULT_OK` on success, `RESULT_CANCELED` otherwise (use `registerForActivityResult(StartActivityForResult())`).
- `isRoleAvailable(roleName)` – check first ("applications should always query if the role is available... before trying to do anything with it"); `isRoleHeld(roleName)` – "whether the calling application is holding a particular role". Both are `@UserHandleAware(enabledSinceTargetSdkVersion = VANILLA_ICE_CREAM)`.
- Requirements: "meet certain requirements, including defining certain components in its manifest" (the HOME intent-filter above) and "user consent".

**[CONFIRMED]** Settings actions (https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/provider/Settings.java):

- `Settings.ACTION_HOME_SETTINGS = "android.settings.HOME_SETTINGS"` – "Show Home selection settings. If there are multiple activities that can satisfy the `Intent.CATEGORY_HOME` intent, this screen allows you to pick your preferred activity." (API 21).
- `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS = "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"` – "Show Default apps settings." (API 24).
- `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `package:` URI.

**[REPORTED]** Resolution order for a Home press: persistent preferred activity (device-owner/enterprise), then user-selected preferred activity, then normal resolution (chooser appears if several). Source: https://medium.com/@robertmanukovich/how-android-chooses-the-default-launcher-intent-resolution-explained-6aa517811c72

**[REPORTED]** ES-DE's documented approach to *leaving* home mode: when set as home, ES-DE removes its own "Quit" entry ("it doesn't make sense to exit the home app"), auto-starts at boot, and "pressing the home button will return to ES-DE regardless of which app you have running"; recovery is via the notification shade → Settings cog → Default apps → Home. It also warns that if the SD card is not mounted within ~4.5 s of boot the onboarding runs again (OS bug) and that the update check may fail because the network is not up yet at boot. Source: https://gitlab.com/es-de/emulationstation-de/-/blob/master/ANDROID.md , FAQ-ANDROID.md

Recommended UX:

1. Settings → "Use as home screen" → `isRoleAvailable(ROLE_HOME)` ? launch `createRequestRoleIntent(ROLE_HOME)` : launch `ACTION_HOME_SETTINGS` (fallback `ACTION_MANAGE_DEFAULT_APPS_SETTINGS`). Some OEM skins ignore the role dialog and just open Settings; handle `RESULT_CANCELED` by offering the Settings route.
2. Always keep a **"Restore stock launcher"** entry that opens `ACTION_HOME_SETTINGS` (there is no API to hand the role to another app; only the user can). Also show which package currently holds HOME by resolving `Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)` with `PackageManager.resolveActivity(..., MATCH_DEFAULT_ONLY)`.
3. Never block Back/exit paths completely: when we are HOME, Back on the root screen should do nothing (Launcher behaviour), and hardware Home returns to root.
4. Robustness: because of `stateNotNeeded`, assume cold starts; keep first frame < 500 ms (cached library in Room, thumbnails from disk cache) so a Home press feels instant.

### 3.3 Behaviour when the user presses Home while we are HOME

**[CONFIRMED]** `KEYCODE_HOME` is never delivered to apps; the system starts the current HOME activity with `ACTION_MAIN`/`CATEGORY_HOME`. With `singleTask` this arrives as `onNewIntent()`; with `clearTaskOnLaunch` any child activities are removed. **[UNVERIFIED]** Launcher3 additionally checks `intent.hasCategory(CATEGORY_HOME)` in `onNewIntent` and, when already resumed, treats it as "go to default page / close open folders" (`FLAG_ACTIVITY_BROUGHT_TO_FRONT`). Mirror this: on `onNewIntent` with `CATEGORY_HOME` → pop the Compose back stack to the root and scroll to top.

**[REPORTED]** Odin 2's stock firmware treats *single* press of its home glyph specially (double press = Home unless OdinTools' "single press home" is enabled); Retroid Flip has "Prevent press the Home button accidentally". Document this in onboarding ("if Home does nothing, disable ... in device settings").

### 3.4 Listing apps and distinguishing games

**[CONFIRMED]** `LauncherApps` (API 21+): "Class for retrieving a list of launchable activities for the current user and any associated managed profiles... This is mainly for use by launchers." `getActivityList(packageName = null, user)` returns "activities that specify `ACTION_MAIN` and `CATEGORY_LAUNCHER`, across all apps" (a synthesized entry is added for apps with none); `registerCallback` gives `onPackageAdded/Removed/Changed/onPackagesAvailable` (the last covers removable storage reappearing); `startMainActivity(component, user, sourceBounds, opts)` launches; `LauncherActivityInfo.getIcon(density)` / `getBadgedIcon` / `getLabel`. Source: https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/content/pm/LauncherApps.java . Alternative: `packageManager.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), 0)`.

TV apps: query `CATEGORY_LEANBACK_LAUNCHER` in addition (some emulators/streaming apps only declare that on TV builds). **[UNVERIFIED that any handheld-relevant emulator ships Leanback-only entries; cheap to include.]**

**[CONFIRMED]** Game detection (`ApplicationInfo.java`, https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/content/pm/ApplicationInfo.java):

- `FLAG_IS_GAME = 1<<25` – "@deprecated use `CATEGORY_GAME` instead." (still set by the system when `android:isGame="true"`, useful as a secondary signal).
- `category` – "Set from the `android.R.attr#appCategory` attribute in the manifest. If the manifest doesn't define a category, this value may have been provided by the installer via `PackageManager#setApplicationCategoryHint`." Values: `CATEGORY_UNDEFINED = -1`, `CATEGORY_GAME = 0`, `AUDIO = 1`, `VIDEO = 2`, `IMAGE = 3`, `SOCIAL = 4`, `NEWS = 5`, `MAPS = 6`, `PRODUCTIVITY = 7`, `ACCESSIBILITY = 8`. `getCategoryTitle(context, category)` gives a localized title.
- Heuristic to use: `category == CATEGORY_GAME || (flags and FLAG_IS_GAME) != 0`, plus a user override list (many games leave category undefined; Play sets the hint for Play-installed games, sideloaded APKs often have none). Source for `android:isGame`/`appCategory` background: https://dev.to/tkuenneth/the-new-gamemanager-class-in-android-12-2gk7

### 3.5 Package visibility (Android 11+)

**[CONFIRMED]** "When an app targets Android 11 (API level 30) or higher and queries for information about the other apps that are installed on a device, the system filters this information by default." Affects `queryIntentActivities()`, `getPackageInfo()`, `getInstalledApplications()`. Declare `<queries>` with `<intent>` filters, or "In the rare cases where the `<queries>` element doesn't provide adequate package visibility, you can use the `QUERY_ALL_PACKAGES` permission. If you publish your app on Google Play, your app's use of this permission is subject to approval." Source: https://developer.android.com/training/package-visibility

**[CONFIRMED]** Play policy permits `QUERY_ALL_PACKAGES` when "core user facing functionality" needs it, naming "device search, antivirus apps, file managers, and browsers"; requires the Permissions Declaration Form. Launchers are widely accepted under this policy (a launcher is "broken" without it), but the text quoted does not literally list "launchers" – **[REPORTED]** that launcher apps get approved. Source: https://support.google.com/googleplay/android-developer/answer/10158779

Practical: for an open-source, sideloaded/F-Droid app, declare `QUERY_ALL_PACKAGES`. For a Play build, prefer:

```xml
<queries>
  <intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent>
  <intent><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent>
  <intent><action android:name="android.intent.action.VIEW"/><data android:scheme="content"/></intent>
</queries>
```

which is sufficient for `queryIntentActivities`/`LauncherApps` and for resolving emulator `ACTION_VIEW` handlers; add `<package android:name=.../>` entries for the emulators you launch by explicit component. `LauncherApps` javadoc also notes callers may need to "declare `<queries>` element with specific package name, have `android.permission.QUERY_ALL_PACKAGES`, or be the session owner".

### 3.6 Icons

- `LauncherActivityInfo.getIcon(densityDpi)` / `getBadgedIcon` return the launcher icon at a chosen density (pass `DisplayMetrics.DENSITY_XXHIGH` for grid icons; badged variant adds work-profile badges). `PackageManager.getApplicationIcon` gives the app icon (may differ from the activity icon). **[UNVERIFIED specifics of adaptive-icon rendering]** Icons are `AdaptiveIconDrawable` on API 26+; draw them into a bitmap at a fixed size once (or use Coil with a custom `Fetcher` keyed by component + `lastUpdateTime`) and cache in Coil's disk/memory cache – never call `getIcon` on the composition thread per frame. `IconCompat`/`AdaptiveIconDrawable.setBounds` + `draw(Canvas)` is the usual approach.

---

## 4. Storage: scanning thousands of ROMs

### 4.1 Storage Access Framework (SAF)

**[CONFIRMED]** https://developer.android.com/training/data-storage/shared/documents-files

- `ACTION_OPEN_DOCUMENT_TREE` (API 21+) lets the user grant a whole directory tree; optional `DocumentsContract.EXTRA_INITIAL_URI`.
- Android 11+ restrictions: cannot pick the **root** of internal storage or of a "reliable" SD card, the **Download** directory, or `Android/data` / `Android/obb` (and their subdirectories). Users must therefore pick e.g. `/ROMs`, not `/`.
- Persist with `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)`; limits: **128 persisted URIs on Android 10 and below, 512 on Android 11+**; access is lost if the document is moved/deleted.
- "Caution: Iterating through a large number of files in a directory accessed via `ACTION_OPEN_DOCUMENT_TREE` can reduce your app's performance significantly."

**[CONFIRMED]** Why `DocumentFile` is slow (CommonsWare, https://commonsware.com/blog/2019/11/23/scoped-storage-stories-documentscontract.html): `DocumentFile.findFile()` calls `listFiles()` and then `getName()` per child; "For almost every method, there is a query to `ContentResolver`"; each query is two IPC hops (app → system `DocumentsContract` proxy provider → the actual `DocumentsProvider`). `DocumentFile.listFiles()` also ignores `DocumentsContract.EXTRA_LOADING`, so it can silently return partial results from cloud providers (https://commonsware.com/blog/2019/12/14/scoped-storage-stories-listfiles-woe.html).

**[REPORTED] Numbers**: DocumentFileCompat README: "One sample run had directory listing at roughly 48 seconds with `DocumentFile` compared to roughly 3.5 seconds with `DocumentFileCompat`" (≈14×); the library "gathers useful metadata while listing files so you do not keep paying for the same queries again and again." Source: https://github.com/ItzNotABug/DocumentFileCompat . Other reports: ~30 s for a 600-file directory with `DocumentFile`.

Fast recursive listing pattern (one `query` per directory, no per-file calls):

```kotlin
val projection = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE,
    DocumentsContract.Document.COLUMN_LAST_MODIFIED)
fun children(tree: Uri, dirDocId: String): List<Entry> {
    val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirDocId)
    resolver.query(uri, projection, null, null, null)?.use { c -> /* read rows; MIME_TYPE_DIR marks folders */ }
}
// start: DocumentsContract.getTreeDocumentId(treeUri); file Uri = buildDocumentUriUsingTree(tree, docId)
```

Run on `Dispatchers.IO` with bounded parallelism per directory (2–4 concurrent queries is enough; the provider serialises anyway), compare `(size, lastModified)` against Room to skip unchanged files, and honour `EXTRA_LOADING` + `ContentObserver` if you ever support cloud providers. Expect roughly 5–10 ms per directory query on the built-in `ExternalStorageProvider` plus file-open costs for hashing – hashing 10k ROMs through `openFileDescriptor` is the real bottleneck, so hash lazily (on first launch/scrape) and only for systems that need it (No-Intros/CRC). **[UNVERIFIED timings; measure.]**

### 4.2 `MANAGE_EXTERNAL_STORAGE` ("All files access")

**[CONFIRMED]** https://developer.android.com/training/data-storage/manage-all-files

- Grants: "Read and write access to all files within shared storage", "Access to the contents of the `MediaStore.Files` table", "Access to the root directory of both the USB on-the-go (OTG) drive and the SD card", "Write access to all internal storage directories except `/Android/data/`, `/sdcard/Android`, and most subdirectories of `/sdcard/Android`. This write access includes direct file path access." I.e. plain `java.io.File` works on `/storage/emulated/0/...` and `/storage/XXXX-XXXX/...`.
- Still **cannot** read other apps' `Android/data/<pkg>` (matters for emulator save/BIOS folders on Android 11+ – you can't scrape or back those up).
- Request: declare the permission, send the user to `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (with `package:` URI) or `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`; check with `Environment.isExternalStorageManager()`.
- Play policy (since May 2021): "Request the `MANAGE_EXTERNAL_STORAGE` permission only when your app can't effectively make use of the more privacy-friendly APIs"; allowed examples include "File managers", "Backup and restore apps", "Document management apps", "On-device file search". Source also: https://support.google.com/googleplay/android-developer/answer/10467955

**[REPORTED]** How the two big Android frontends chose:

- **ES-DE** requires storage-manager permission. Rationale from its FAQ: it "functions as a storage manager" handling "hundreds of thousands of files", it depends on "a large amount of C and C++ libraries that have no awareness of or support for the Storage Access Framework", "substantial work was spent on attempting to work around the Android security model without having storage manager permission... it never worked 100% due to additional restrictions introduced in Android 13", and the JNI SAF bridge had unacceptable UTF-8 translation overhead. ES-DE is not on Google Play (Patreon, Samsung Galaxy Store, Huawei AppGallery). Sources: https://gitlab.com/es-de/emulationstation-de/-/blob/master/FAQ-ANDROID.md , ANDROID.md
- **Daijishō** uses SAF: it lets the user pick ROM folders and offers `{file.path}`, `{file.uri}`, `{file.documentUri}`, `{file.mime}` variables when building emulator launch intents, i.e. it keeps both a filesystem path (when derivable) and a SAF URI. Source: https://github.com/TapiocaFox/Daijishou/wiki/Start-Arguments

Recommendation: **support both**. Default to SAF (`ACTION_OPEN_DOCUMENT_TREE` per ROM root) with the fast `DocumentsContract` scanner; offer "All files access" as an optional accelerator (direct `File` walking via `Files.walkFileTree` is 10–50× faster than SAF and gives real paths that every emulator accepts). Derive a real path from a tree URI when it belongs to `com.android.externalstorage.documents` (`primary:ROMs/psx` → `/storage/emulated/0/ROMs/psx`, `1234-5678:ROMs` → `/storage/1234-5678/ROMs`) – **[REPORTED, widely used, undocumented; verify per device]** – because most emulators launched by intent still want `-e ROM /storage/...` style paths.

### 4.3 Handing a ROM to an emulator

**[CONFIRMED]** `FileProvider` (https://android.googlesource.com/platform/frameworks/support/+/androidx-main/core/core/src/main/java/androidx/core/content/FileProvider.java): declare `<provider android:name="androidx.core.content.FileProvider" android:authorities="..." android:exported="false" android:grantUriPermissions="true">` with `<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>`. Path elements: `<files-path>` (`getFilesDir()`), `<cache-path>`, `<external-path>` (`Environment.getExternalStorageDirectory()`), `<external-files-path>`, `<external-cache-path>`, `<external-media-path>`; the code also accepts an undocumented `<root-path>` (`TAG_ROOT_PATH` → `/`), which is what lets a FileProvider serve `/storage/XXXX-XXXX/...` on an SD card. Grant temporarily via `Intent.setFlags(FLAG_GRANT_READ_URI_PERMISSION)`; on API 16–22 attach `ClipData.newRawUri("", uri)`.

**[CONFIRMED]** Grants apply to the Intent's **data URI and ClipData URIs**, not to URIs in string extras: "The permissions you grant are temporary and expire automatically when the receiving app's task stack is finished." "Calling `setFlags()` is the only way to securely grant access to your files using temporary access permissions." Source: https://developer.android.com/training/secure-file-sharing/share-file . So when an emulator expects the ROM in an extra (e.g. Dolphin `-e AutoStartFile {file.uri}`), also call `intent.clipData = ClipData.newRawUri(null, uri)` so the grant travels with the intent. **[UNVERIFIED wording of `Intent.setClipData` javadoc, but this is the documented purpose of ClipData grants.]**

**Re-sharing a SAF URI from another tree**: an app that holds a (persisted) permission on a `content://com.android.externalstorage.documents/tree/...` URI can forward that URI to another app with `FLAG_GRANT_READ_URI_PERMISSION`; the system grants the receiver access to *that document URI* for the task lifetime. This works because the grant is checked against the sender's own permission on the URI, not on the provider's export flag. The receiver must be able to open `content://` URIs (`ContentResolver.openFileDescriptor`). **[REPORTED/UNVERIFIED as a general rule; confirmed in practice by Daijishō's `{file.uri}` launches with `--grant-read-uri-permission`.]** Limitations: only single-file grants (a `.cue` grant does not carry its `.bin`s), and emulators using native `fopen` on paths cannot use them.

**[REPORTED]** ES-DE: "A number of emulators support the FileProvider API which makes it possible for ES-DE to temporarily provide storage access to the game file on launch... Access can however only be passed for single files, so for systems that support multi-file games such as disc-based games in .bin/.cue format SAF URIs are often used instead" – and for those the user must "manually provide scoped storage access to each game system directory" inside the emulator (e.g. DuckStation → `ROMs/psx`, M64Plus FZ → `ROMs/n64`; MAME4droid wants the ROM root). Variables: `%ROMPROVIDER%` (FileProvider URI), `%ROMSAF%` (SAF URI), `%ACTION%`, `%DATA%`, `%EXTRA_FILE%`, `%EXTRA_STRING%`. Source: ES-DE ANDROID.md / FAQ-ANDROID.md.

**[REPORTED]** RetroArch: accepts `-e ROM <path>` plus `-e LIBRETRO <core .so>` and `-e CONFIGFILE ...`; it has its own `saf://[encoded tree uri]/sub/game.ext` scheme (PR #18336) but **does not accept plain `content://` file URIs from frontends** (issue #18753, still open with a linked PR #18805). Daijishō example: `-n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture -e ROM {file.path} -e LIBRETRO /data/data/com.retroarch.aarch64/cores/<core>_libretro_android.so -e CONFIGFILE /storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg`. PPSSPP: `-a VIEW -d {file.uri} -t application/octet-stream`; Dolphin: `-e AutoStartFile {file.uri}`; Citra/Lime3DS: `-a VIEW -d {file.uri}`. Sources: https://github.com/libretro/RetroArch/issues/18753 , https://github.com/TapiocaFox/Daijishou/wiki/Start-Arguments

Design consequence: keep an **emulator profile table** (like Daijishō's start-argument templates / ES-DE's `es_find_rules`) with per-emulator fields: component, action, whether it wants a path / `content://` data URI / extra, MIME, extra flags. Every ROM record stores both the SAF document URI and (when derivable) the absolute path.

---

## 5. Images, video and list performance for ~10k items in Compose

### 5.1 Coil 3

**[CONFIRMED]** Compose artifact: `io.coil-kt.coil3:coil-compose:3.6.2` (network via `coil-network-okhttp` or `coil-network-ktor3` if scraping remotely). Singleton: implement `SingletonImageLoader.Factory` on the `Application` (`override fun newImageLoader(context) = ImageLoader.Builder(context).crossfade(true).build()`) or call `setSingletonImageLoaderFactory` in Compose. "Coil performs best when you create a single `ImageLoader` and share it throughout your app". Sources: https://coil-kt.github.io/coil/compose/ , https://coil-kt.github.io/coil/getting_started/ , https://coil-kt.github.io/coil/image_loaders/

- `AsyncImage` "correctly determines the size your image should be loaded at based on the constraints of the composable and the provided `ContentScale`" – prefer it in grids. `rememberAsyncImagePainter` "always loads the image with its original dimensions" unless you pass `rememberConstraintsSizeResolver()`. `SubcomposeAsyncImage`: "Subcomposition is slower than regular composition so this composable may not be suitable for performance-critical parts of your UI (e.g. `LazyList`)."
- `ImageRequest.Builder.crossfade(true)`, `placeholder(...)`, `size(...)`/`precision` – set an explicit `size` (or give the `AsyncImage` fixed dp size) so decoded bitmaps are downsampled to card size; this matters more than anything else for 10k box arts.
- Disk cache defaults (`DiskCache.Builder`): `maxSizePercent = 0.02` (2% of the disk), clamped to `minimumMaxSizeBytes = 10 MB` and `maximumMaxSizeBytes = 250 MB`; LRU; "It is an error to have two `DiskCache` instances active in the same directory at the same time". Configure `.directory(context.cacheDir.resolve("image_cache"))` and raise the cap (e.g. 1 GB) for large scraped libraries. Source: https://raw.githubusercontent.com/coil-kt/coil/main/coil-core/src/commonMain/kotlin/coil3/disk/DiskCache.kt
- Memory cache (`MemoryCache.Builder`): `maxSizePercent(context, percent)` "as a percentage of this application's available memory", default `context.defaultMemoryCacheSizePercent()`; strong + weak references enabled by default. **[UNVERIFIED default value: 25 % of app memory, 15 % on low-RAM devices.]** For a grid of 200 dp cards at xxhdpi (~600×800 px ARGB ≈ 2 MB each) a 25 % cache on a 512 MB heap holds ~60 images – set `.memoryCachePolicy` and `size` accordingly, and consider `bitmapConfig(RGB_565)` for box-art thumbnails. Source: https://raw.githubusercontent.com/coil-kt/coil/main/coil-core/src/commonMain/kotlin/coil3/memory/MemoryCache.kt
- Video frames: `io.coil-kt.coil3:coil-video:3.6.2`, register `add(VideoFrameDecoder.Factory())`; per-request `videoFrameMillis(1000)`, `videoFramePercent(0.5)`, `videoFrameIndex(n)` (index needs API 28); "This feature is only available on Android"; default is the first frame. Good for a static poster of the preview video without spinning up ExoPlayer. Source: https://coil-kt.github.io/coil/videos/
- Loading from SAF: Coil can load `content://` URIs directly (`ContentResolver` fetcher), so scraped media stored in the user's SAF tree works, but local app storage (`filesDir/media`) is faster; ES-DE explicitly recommends keeping `downloaded_media` on internal storage for large collections.

### 5.2 Lazy grids, keys, content types, Paging 3

**[CONFIRMED]** https://developer.android.com/develop/ui/compose/lists

- Keys: "Providing a stable key enables item state to be consistent across dataset changes"; key type "must be supported by `Bundle`" (primitives, enums, Parcelable) – use the Room row id (`Long`), not a data class.
- `contentType`: "Compose is able to reuse compositions only between the items of the same type" – give game cards, headers and "add system" tiles distinct types.
- Avoid 0-px items: with async images the grid would "compose all of its items in the first measurement" – give cards a fixed aspect ratio/size placeholder.
- Paging: `androidx.paging:paging-compose`, `pager.flow.collectAsLazyPagingItems()`, `items(count = lazyPagingItems.itemCount, key = lazyPagingItems.itemKey { it.id }, contentType = lazyPagingItems.itemContentType { ... })`; placeholders when `item == null`.
- "You can only reliably measure the performance of a Lazy layout when running in release mode and with R8 optimization enabled."

**[REPORTED]** `LazyVerticalGrid` + Paging has had reports of requesting all pages at once (Foso/Jetpack-Compose-Playground #85) – verify with `PagingConfig(pageSize = 60, prefetchDistance = 40, enablePlaceholders = true)` and Room's `PagingSource`. Sources: https://github.com/Foso/Jetpack-Compose-Playground/issues/85 , https://medium.com/@android./a-full-guide-to-use-paging3-library-along-with-jetpack-composes-lazyrow-lazycolumn-and-lazygrid-7e6c6bf3812d

Judgement: for a *single* system with ≤ 10k rows, a Room `Flow<List<GameCard>>` with a slim projection (id, title, boxart path, favourite flag ≈ 100 B/row → ~1 MB) fed to `LazyVerticalGrid` is simpler and D-pad friendlier than Paging (placeholders break "jump to letter" and focus restoration). Use Paging only for the "All games" view if the total exceeds ~20k or memory shows pressure. Search/filter/sort should run in SQL (FTS4/5 table for titles), never in Compose.

### 5.3 Cheap focus animations

**[CONFIRMED]** `Modifier.graphicsLayer { scaleX = s; scaleY = s; ... }` – "You should prefer the lambda version of this modifier when performing animations or using a `State` object to update a `graphicsLayer` property" (reads state in the draw phase, skipping recomposition and layout). `alpha < 1f` forces an offscreen buffer unless `compositingStrategy = CompositingStrategy.ModulateAlpha`. Source: https://developer.android.com/develop/ui/compose/graphics/draw/modifiers

Pattern for a focus-scaled card: `val focused by interactionSource.collectIsFocusedAsState(); val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(150)); Modifier.graphicsLayer { scaleX = scale; scaleY = scale }` plus a border drawn in `drawWithContent`/`drawBehind` reading the same state. Source: https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions

**[CONFIRMED]** `Modifier.blur(radius, edgeTreatment)`: "Note this effect is only supported on Android 12 and above. Attempts to use this Modifier on older Android versions will be ignored." It "renders the corresponding composable into a separate graphics layer" and is clipped to bounds (use `BlurredEdgeTreatment.Unbounded` to avoid hard edges). Source: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/draw/Blur.kt . Cost: a `RenderEffect` blur re-renders the layer every frame it changes; blurring a full-screen background behind a moving grid is expensive on Odin/Retroid-class GPUs. Blur a **static, pre-downscaled** background bitmap once (or use Coil's `BlurTransformation`/RenderScript-free box blur when producing the background image) instead of `Modifier.blur` over live content.

### 5.4 Looping muted preview video

**[CONFIRMED]** Media3 Compose UI: `implementation("androidx.media3:media3-ui-compose:1.11.1")` provides `PlayerSurface(player, modifier, surfaceType = SURFACE_TYPE_SURFACE_VIEW)` and `ContentFrame`, plus state holders (`PlayPauseButtonState`, `PresentationState`, ...). `PlayerSurface` calls `setVideoSurfaceView`/`setVideoTextureView` when the player supports `COMMAND_SET_VIDEO_SURFACE` and clears the previous player's surface on change. Sources: https://developer.android.com/media/media3/ui/compose , https://raw.githubusercontent.com/androidx/media/release/libraries/ui_compose/src/main/java/androidx/media3/ui/compose/PlayerSurface.kt

**[REPORTED / judgement]**

- Use `SURFACE_TYPE_TEXTURE_VIEW` when the video sits inside a scaled/animated/clipped card (SurfaceView punches a hole and does not follow `graphicsLayer` transforms or rounded clips smoothly); use `SURFACE_TYPE_SURFACE_VIEW` for the full-screen/immersive hero, where it is cheaper and avoids an extra GPU copy. Source: https://levelup.gitconnected.com/stop-using-playerview-in-compose-media3-playersurface-done-right-8c0423c9723a (and Media3 "Choosing a surface type" referenced from the source).
- Create **one** `ExoPlayer` for the whole screen (`remember` + `DisposableEffect { onDispose { player.release() } }`, pause in `LifecycleEventEffect(ON_STOP)`), and re-target it to the focused card after a debounce (~600–800 ms of steady focus, ES-DE uses a similar delay). Set `repeatMode = REPEAT_MODE_ONE`, `volume = 0f` (or a user setting), `videoScalingMode`, and load the preview as a `MediaItem` from the local file or `content://` URI. Show the Coil poster (`videoFrameMillis`) until `PresentationState.coverSurface` reports the first frame to avoid a black flash.
- Never create players inside grid items; on hardware with a single video decoder pipeline (common on these SoCs) multiple simultaneous decodes stall.

---

## 6. Recommendations for our implementation

1. **Input pipeline (Activity-owned).** Intercept in `ComponentActivity.dispatchKeyEvent` + `onGenericMotionEvent`. Ignore `repeatCount > 0`. Normalise triggers (both `AXIS_LTRIGGER/BRAKE` and `BUTTON_L2`), D-pad (both `KEYCODE_DPAD_*` and `AXIS_HAT_*`) and sticks into semantic actions with a shared "held direction" state to avoid double stepping. Implement ES-DE-style tiered auto-repeat (500 → 114 → 16 ms) in a coroutine; call `FocusManager.moveFocus()` per step and stop when it returns false. Let real `KEYCODE_DPAD_*` events flow into Compose unchanged.
2. **Rely on the framework fallbacks, don't fight them.** Do not consume `BUTTON_A` or `BUTTON_B` – let them become `DPAD_CENTER`/`BACK` so `clickable` and `BackHandler` work everywhere. Do consume `BUTTON_X`, `BUTTON_Y`, `BUTTON_START`, `BUTTON_SELECT`, `BUTTON_MODE` (when you assign meanings), because their fallbacks (`DEL`, `SPACE`=click, `DPAD_CENTER`, `MENU`, `HOME`) would otherwise fire. Never return `true` from a root `onKeyEvent` for keys you don't use.
3. **Button mapping UI + per-device profiles.** Ship a "press each button" calibration screen keyed by `InputDevice.descriptor`/vendor+product, default to Xbox-style semantic layout, and detect Switch-style label swap by a user toggle ("A confirms / B confirms"). Document Odin 2 "controller style / L2-R2 mode / single-press Home" and Retroid "Prevent Home accidental press" in onboarding.
4. **Compose focus architecture.** Foundation `LazyColumn` of `LazyRow`s (or `LazyVerticalGrid`), each with `Modifier.focusRestorer()` and `focusGroup()`; stable `key`s and `contentType`s; `LocalBringIntoViewSpec` pivot for rows; explicit `focusProperties { up/down = ... }` at row boundaries; first-focus via `FocusRequester` in `LaunchedEffect` and only when `LocalInputModeManager.current.inputMode == InputMode.Keyboard`. Programmatic "jump to item N" = `scrollToItem` → await frame → `requestFocus`. Keep Compose UI/Foundation ≥ 1.8 (focusRestorer fixes). Don't adopt `androidx.tv:tv-material`; copy its focus-scale Surface idea with `graphicsLayer` + `collectIsFocusedAsState`.
5. **HOME launcher.** Copy Launcher3's activity attributes (`singleTask`, `clearTaskOnLaunch`, `stateNotNeeded`, `taskAffinity=""`, `resumeWhilePausing`), handle `CATEGORY_HOME` in `onNewIntent` → reset to root. Request the role with `RoleManager.createRequestRoleIntent(ROLE_HOME)` (check `isRoleAvailable`), fall back to `Settings.ACTION_HOME_SETTINGS`; always keep a "Restore default launcher" action that opens `ACTION_HOME_SETTINGS`. Persist UI state in DataStore because `stateNotNeeded` allows kills. Target sub-500 ms cold start from the Room cache.
6. **App list.** Use `LauncherApps` (`getActivityList(null, user)` + `registerCallback`), include `CATEGORY_LEANBACK_LAUNCHER`, classify games with `category == CATEGORY_GAME || FLAG_IS_GAME` plus user overrides. Declare `<queries>` intents for `MAIN/LAUNCHER`, `MAIN/LEANBACK_LAUNCHER`, `VIEW content:` and explicit emulator packages; add `QUERY_ALL_PACKAGES` in the sideload/F-Droid flavour only.
7. **Storage.** SAF by default with a `DocumentsContract`-based recursive scanner (one query per directory, projection of id/name/mime/size/lastModified, incremental diff against Room); optional `MANAGE_EXTERNAL_STORAGE` mode that walks `java.io.File` and stores real paths. Store both SAF URI and derived absolute path per ROM. Persist ≤ 512 tree URIs. Never let users pick `/`, `Download`, or `Android/*` (Android 11+ forbids it anyway).
8. **Launching emulators.** Data-driven emulator profiles (component, action, path-vs-URI, extras, MIME, flags). Grant via `FLAG_GRANT_READ_URI_PERMISSION` on the data URI **and** `clipData` when the URI is in an extra; offer `FileProvider` (`<external-path>` + `<root-path>` for SD cards) for emulators that want a `content://` but the ROM is on a real path; fall back to absolute paths for RetroArch and other native-`fopen` emulators; tell the user which emulators still need in-app SAF folder grants (multi-file games).
9. **Media.** Coil 3 singleton with a 1 GB disk cache in `cacheDir`, explicit request `size` matching card px, RGB_565 for thumbnails, `coil-video` posters; single shared `ExoPlayer` + `PlayerSurface(TEXTURE_VIEW)` for the focused card's muted looping preview after a focus debounce; pre-blurred static backgrounds instead of `Modifier.blur` on live content (API 31+ only anyway).
10. **Measure on target hardware in release/R8 builds** (Odin 2 Snapdragon 8 Gen 2 is fast; Retroid Pocket 4/5 Dimensity 1100/Snapdragon 865 and older Unisoc T618 devices are the floor). Key metrics: cold start to first focused card, frame time while holding D-pad at the 16 ms tier, scan time for 10k files over SAF vs File.

---

### Source index (primary)

- Controller input: https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input
- Generic.kcm fallbacks: https://android.googlesource.com/platform/frameworks/base/+/main/data/keyboards/Generic.kcm
- KeyEvent / KeyCharacterMap / ViewConfiguration: https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/view/
- Compose focus docs: https://developer.android.com/develop/ui/compose/touch-input/focus (+ change-focus-behavior, change-focus-traversal-order, react-to-focus), https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands
- Compose source: FocusRestorer.kt, Focusability.kt, FocusOwnerImpl.kt, InputModeManager.kt, Focusable.kt, Clickable.kt, AndroidComposeView.android.kt under https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/
- TV lists guide: https://developer.android.com/training/tv/playback/compose/lists ; TV releases: https://developer.android.com/jetpack/androidx/releases/tv ; migration ticket https://issuetracker.google.com/issues/348896032
- Launcher3 manifest: https://android.googlesource.com/platform/packages/apps/Launcher3/+/main/AndroidManifest.xml ; activity element: https://developer.android.com/guide/topics/manifest/activity-element
- RoleManager: https://android.googlesource.com/platform/packages/modules/Permission/+/main/framework-s/java/android/app/role/RoleManager.java ; Settings actions: https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/provider/Settings.java
- Package visibility: https://developer.android.com/training/package-visibility ; Play policy: https://support.google.com/googleplay/android-developer/answer/10158779
- ApplicationInfo / LauncherApps: https://android.googlesource.com/platform/frameworks/base/+/main/core/java/android/content/pm/
- SAF: https://developer.android.com/training/data-storage/shared/documents-files ; CommonsWare: https://commonsware.com/blog/2019/11/23/scoped-storage-stories-documentscontract.html , https://commonsware.com/blog/2019/12/14/scoped-storage-stories-listfiles-woe.html ; DocumentFileCompat: https://github.com/ItzNotABug/DocumentFileCompat
- All files access: https://developer.android.com/training/data-storage/manage-all-files ; policy https://support.google.com/googleplay/android-developer/answer/10467955
- FileProvider: https://android.googlesource.com/platform/frameworks/support/+/androidx-main/core/core/src/main/java/androidx/core/content/FileProvider.java ; sharing: https://developer.android.com/training/secure-file-sharing/share-file
- ES-DE Android: https://gitlab.com/es-de/emulationstation-de/-/blob/master/ANDROID.md , https://gitlab.com/es-de/emulationstation-de/-/blob/master/FAQ-ANDROID.md , IList.h https://gitlab.com/es-de/emulationstation-de/-/raw/master/es-core/src/components/IList.h
- Daijishō start arguments: https://github.com/TapiocaFox/Daijishou/wiki/Start-Arguments ; RetroArch SAF: https://github.com/libretro/RetroArch/issues/18753
- Odin: https://github.com/langerhans/OdinTools , https://github.com/hrydgard/ppsspp/issues/17245 , https://retrohandhelds.gg/ayn-odin-2-portal-setup-guide/ ; Retroid: https://www.adinwalls.com/2023/03/22/retroid-pocket-flip-3-ultimate-setup-guide/ ; Pegasus double-input: https://github.com/mmatyas/pegasus-frontend/issues/328
- Coil 3: https://coil-kt.github.io/coil/compose/ , https://coil-kt.github.io/coil/image_loaders/ , https://coil-kt.github.io/coil/videos/ , DiskCache.kt / MemoryCache.kt in https://github.com/coil-kt/coil
- Compose lists/perf: https://developer.android.com/develop/ui/compose/lists , https://developer.android.com/develop/ui/compose/graphics/draw/modifiers , Blur.kt source
- Media3 Compose: https://developer.android.com/media/media3/ui/compose , PlayerSurface.kt in https://github.com/androidx/media
