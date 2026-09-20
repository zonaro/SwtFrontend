# br.com.redclaw.swt.game

Game player (internal emulator) and DS dual-screen support for Swt Frontend.

## Overview

This package implements the core game-playing functionality:

- **GameActivity** — renders games via `GLRetroView` (libretrodroid)
- **GameLoaderHelper** — bridges `CoreID` to resolved `.so` file paths
- **GameTouchOverlay** — View-based on-screen controls (d-pad, buttons, shoulders)
- **SecondaryDisplayActivity** — DS bottom screen on external displays via `Presentation`

## How to Start a Game

```kotlin
// From your library/grid Activity:
GameActivity.launch(
    context = this,
    game = gameEntity,           // Room entity from retrograde-app-shared
    systemCoreConfig = config,   // SystemCoreConfig from GameSystem
    loadSave = true,             // restore previous save on launch
)
```

### Internal Flow

1. `GameActivity.onCreate()` extracts `Game` + `SystemCoreConfig` from intent extras
2. Resolves core `.so` via `GameLoaderHelper.resolveCorePath(coreID)` → `CoreUpdaterImpl.getCoreFile()`
3. Resolves game file from `Game.fileUri` (file:// or content://)
4. Builds `GLRetroViewData` with: core path, game path, system/saves dirs, core variables, SRAM
5. Creates `GLRetroView` and adds to `retro_view_container`
6. Registers as lifecycle observer for automatic `LibretroDroid.create()` / `LibretroDroid.destroy()`
7. Restores auto-save if available (via `StatesManager.getAutoSave()`)

## Core Path Resolution

```
filesDir/cores/<CORES_VERSION>/<coreID.libretroFileName>
```

Example: `filesDir/cores/1.17.0/libmgba_libretro_android.so`

`CoreUpdaterImpl` handles downloading cores from GitHub when not yet available.

## Save States

- **Slot save**: `StatesManager.setSlotSave(game, saveState, coreID, index)` — 4 slots (0-3)
- **Auto-save**: `StatesManager.setAutoSave(game, coreID, saveState)` — on pause/quit
- **SRAM**: `SavesManager.setSaveRAM(game, data)` — battery saves

All save operations run on `Dispatchers.IO` via the `StatesManager`/`SavesManager`.

## Gamepad Input

Physical gamepad input is handled via:

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0)
    return true
}

override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
    // Analog sticks → sendMotionEvent(source, xAxis, yAxis, port)
    retroView?.sendMotionEvent(0, xAxis, yAxis, 0) // Left stick
    return true
}
```

Button mapping follows Android's standard gamepad key codes:
- `KEYCODE_BUTTON_A/B/X/Y` — face buttons
- `KEYCODE_BUTTON_L1/R1` — shoulders
- `KEYCODE_BUTTON_START/SELECT` — start/select
- `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT` — d-pad

## Touch Overlay

When no physical gamepad is detected, `GameTouchOverlay` renders on-screen controls:

- **D-pad** (left side): 4-direction cross with hit-test rectangles
- **A/B buttons** (right side): circular face buttons
- **L/R shoulders** (top): rectangular shoulder buttons
- **Start/Select** (center bottom): rectangular buttons

Touch events dispatch `onKeyDown`/`onKeyUp` via `TouchInputListener` → `GLRetroView.sendKeyEvent()`.

## In-Game Menu

Press BACK or START to toggle the in-game menu:

- **Resume** — resume emulation
- **Save State** — save to slot 0
- **Load State** — load from slot 0
- **Reset** — reset the core
- **Restart Game** — save snapshot, finish, re-launch
- **DS Bottom Screen** — toggle secondary display (DS games only)
- **Quit** — save SRAM + auto-state, finish activity

Menu pauses emulation by setting `retroView.frameSpeed = 0`.

## DS Dual-Screen

### On-Device (Core Options)

DS games (NDS system) use core options for screen layout:

| Core      | Variable                    | Values                      |
|-----------|-----------------------------|-----------------------------|
| MelonDS   | `melonds_screen_layout1`    | `top-bottom`, `left-right`  |
| DeSmuME   | `desmume_screens_layout`    | `top/bottom`, `left/right`  |

These are set automatically by `GameActivity` when loading a DS game.

### External Display (Presentation Pattern)

For showing the DS bottom screen on an external display (HDMI, Miracast, USB-C):

1. `GameActivity.toggleSecondaryDisplay()` detects external displays via:
   ```kotlin
   displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
       .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
   ```

2. Creates a `SecondaryGamePresentation` (extends `android.app.Presentation`)
3. The Presentation renders on the external display with immersive mode
4. `DisplayManager.DisplayListener` monitors display connect/disconnect

Pattern adapted from Kwiq's `SecondaryDisplayPresentation`.

### TODO: Full GLRetroView Mirror

Currently, the secondary display shows an info panel. Full DS bottom-screen rendering
via a second `GLRetroView` on the Presentation requires surface sharing, which is a
future enhancement. The core's built-in dual-screen layout handles on-device rendering.

## Files

| File | Description |
|------|-------------|
| `GameActivity.kt` | Main game activity with GLRetroView, input, menu, save states |
| `GameLoaderHelper.kt` | Core path resolution, system directory lookup, DS config |
| `GameTouchOverlay.kt` | View-based on-screen touch controls |
| `SecondaryDisplayActivity.kt` | External display Presentation for DS bottom screen |
| `activity_game.xml` | Layout: retro_view_container + touch_overlay + menu_overlay |
| `activity_game_secondary.xml` | Layout for secondary display (info panel) |

## Dependencies

- `libretrodroid` — `GLRetroView`, `GLRetroViewData`, `Variable`, `ShaderConfig`
- `retrograde-app-shared` — `CoreID`, `SystemCoreConfig`, `GameSystem`, `Game`, `StatesManager`, `SavesManager`, `DirectoriesManager`
- `app.cores` — `CoreUpdaterImpl` (core .so download/resolution)

## GPLv3

Derived from Lemuroid's `BaseGameActivity`, `GameViewModelRetroGameView`, and `GameLoader`.
See license headers in each file.
