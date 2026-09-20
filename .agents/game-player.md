# Game Player — SwtFrontend

> **Player interno** via `GLRetroView` (libretrodroid) + **DS dual-screen** (Presentation + core options) + saves/states + touch/gamepad puro Views.

## Arquivos

- `game/GameActivity.kt` (extends `ScaledAppCompatActivity`, `GameTouchOverlay.TouchInputListener`) — lifecycle do jogo, GLRetroView, menu overlay, RA bridge, DS
- `game/GameLoaderHelper.kt` — bridge `CoreID` → `.so`
- `game/GameTouchOverlay.kt` — Views d-pad/A/B/L/R/Start/Select (HylianBox pattern, sem Compose)
- `game/SecondaryDisplayActivity.kt` — Presentation para bottom screen DS em display externo (Kwiq pattern)
- `res/layout/activity_game.xml` — `retro_view_container` (Frame) + `touch_overlay` + `menu_overlay` (scrim, pause) + `RaOverlayView`
- `res/layout/activity_game_secondary.xml` — painel info display externo

## GameLoaderHelper (CoreID → .so)

```kotlin
fun getCoreFile(context, coreID: CoreID): File {
  val version = "1.17.0" // CoreUpdaterImpl.CORES_VERSION
  val abi = Build.SUPPORTED_ABIS[0] // arm64-v8a
  return File(context.filesDir, "cores/$version/$abi/lib${coreID.coreName}_libretro_android.so")
}
fun getGameFile(uri: Uri): File // via StorageProviderRegistry SAF ou DirectoriesManager
fun applyDsCoreVariables(retroView: GLRetroView, system: GameSystem) {
  if (system == GameSystem.NINTENDO_DS) {
    retroView.setVariable("melonds_screen_layout1", "0") // top/bottom stacked
    retroView.setVariable("desmume_screens_layout", "0")
  }
}
```

- Usa `DirectoriesManager` (retrograde-app-shared) para `filesDir` + `CoreUpdaterImpl` para versão.
- `GameSystem.NINTENDO_DS` detectado via `LibretroDBMetadataProvider` (ext `.nds`).

## GameActivity — fluxo

```
onCreate → setContentView(R.layout.activity_game) → find retro_view_container
        → GameLoaderHelper.getCoreFile() existe? se não → CoreUpdaterImpl.downloadCores(coreID) com ProgressCallback (notificação)
        → GameLoader.loadGame(coreFile, gameFile) → GLRetroView (programmatic, addView) → lifecycle-observe
        → GameTouchOverlay (addView, TouchInputListener → GLRetroView.sendKeyEvent)
        → Gamepad: onKeyDown/onKeyUp/onGenericMotionEvent → GLRetroView.sendKeyEvent/sendMotionEvent (HylianBox gamepad/ pkg)
        → RaSessionManager.attach(retroView, gameId, hash) → getMemoryRegion callback
        → menu_overlay: pause (retroView.frameSpeed=0) + botões Resume/Save(slot0)/Load/Reset/Quit
        → SecondaryDisplay: DisplayManager.DisplayListener → Presentation
```

- **GLRetroView:** criado programaticamente (não XML), `layoutParams MATCH_PARENT`, `frameSpeed = 1f` (60fps), `0f` pause. `render` em `GLThread`.
- **Saves:** `StatesManager` (slot 0 `saveState(0)` + auto-save WorkManager `saveStateAsync`) + `SavesManager` (SRAM `saveSRAM()` em `onPause`/`onDestroy`, `loadSRAM()` em `loadGame`). `SaveState` com `metadata` (HylianBox).
- **Menu:** overlay `View.GONE` default, `KEYCODE_BACK`/`KEYCODE_BUTTON_START` toggle, `frameSpeed=0` pausa, `RaOverlayView.showUnlock` continua visível por cima.
- **Lifecycle:** `onPause` → `frameSpeed=0` + `saveSRAM()`, `onResume` → `frameSpeed=1f`, `onDestroy` → `GLRetroView.onDestroy()` + `RaSessionManager.detach()`.

## DS dual-screen

- **On-device:** core cuida do stacking via `screen_layout` variável; `GameTouchOverlay` desenha 2 áreas (top/bottom) com `GeometryUtils`.
- **Externo (Kwiq `SecondaryDisplayActivity` pattern):** `DisplayManager.registerDisplayListener` → `onDisplayAdded(id)` → `SecondaryDisplayActivity` como `Presentation` (extends `Dialog`) com `Display` do `DisplayManager.getDisplay(id)` → `FrameLayout` com `ImageView` que `GameActivity` atualiza via `RaSessionManager` ou `retroView` screenshot. `onDisplayChanged/onDisplayRemoved` dismiss. `AndroidManifest` `launchMode singleTop`.

## Touch / Gamepad (Views puro)

- **GameTouchOverlay:** custom `View` (não Compose) desenhando `GraphicsUtils` + `GeometryUtils.computeSizeOfItemsAroundCircumference` + `ComposeUtils.pxToDp` (via retrograde-util). 8 botões: `d-pad` (4 direções merge `mergeDPADAndLeftStickEvents`), `A/B` (face), `L/R` (shoulder), `Start/Select` (center). `TouchInputListener` (`onButtonDown/up`) → `GLRetroView`.
- **Tilt:** `TiltSensor` + `TiltConfiguration` (`TILT_CONFIGURATION_DISABLED/CROSS/L_R/...` de `lemuroid-touchinput`) via `TouchControllerID.ATARI2600` etc. (definição em `ControllerConfigs.kt` de `retrograde-app-shared`).
- **Gamepad:** `HylianBox/app/src/main/java/br/com/redclaw/hylianbox/gamepad/` copiado (mapeamento `KeyEvent.KEYCODE_BUTTON_*` → `Retro pad`), `onKeyDown` retorna `true` se consumido, senão `super`.

## Integração RA

- `GameActivity.onCreate` após `loadGame` → `RaSessionManager.start(identity, retroView)` → `rc_client` `begin_login` → `identify_and_load_game` (hash) → `do_frame` por `GLRetroView.FrameRendered` + `idle` em pause.
- `RaOverlayView` (app `ra/ui/`) é filho de `activity_game.xml` topo, `showUnlock(title, desc, badgeUrl)` chamado por `RaSessionManager` `SharedFlow<RaClientEvent>` (`RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED`).
- Hardcore: `RaSessionManager.setHardcore(true)` bloqueia `StatesManager`/`SavesManager` já documentado em `.agents/rcheevos.md`.
