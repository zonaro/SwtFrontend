# SwtFrontend — Documentação Técnica (agents.md)

> **Launcher Android para emuladores** — Frontend no estilo Nintendo Switch que roda cores libretro internos (via LibretroDroid) e lança emuladores externos, com scraping automático de capas, browser interno, categorias de apps, dashboard self-hosted e RetroAchievements.
>
> **Stack:** Kotlin 2.1.20 + AGP 8.11.1 + compileSdk 35 + minSdk 24 + JDK 17 + NDK 27.2 + Gradle 8.14 + Ktor 3.1.3 (CIO) — **Views puro (sem Compose)**, estética Switch do HylianBox.
>
> **Licença:** GPLv3 (derivado de Lemuroid + HylianBox + LibretroDroid + rcheevos — headers preservados, namespace vendored mantido `com.swordfish.*` para facilitar merge).
>
> **Namespace do app:** `br.com.redclaw.swt` | **Repo:** `/mnt/GIT/SwtFrontend` | **Plano original:** `.omo/plans/swt-frontend.md`

---

## Índice rápido

Este arquivo é o **sumário**. O detalhamento vive em `.agents/`:

| Arquivo em `.agents/` | O que cobre |
|---|---|
| `README.md` | Índice e convenções |
| `architecture.md` | Visão geral, grafo de módulos, fluxo de dados, flavors/ABIs |
| `files-structure.md` | Árvore de arquivos com propósito de cada pasta/arquivo |
| `cores-libretrodroid.md` | LibretroDroid (vendored HylianBox 0.13.2 + `getMemoryRegion`), `CoreUpdater` runtime, `lemuroid-cores` (CORES_VERSION 1.17.0), storage `filesDir/cores/<version>` |
| `ui-switch.md` | Regras de UI Switch: 12 accents, `ThemeManager`/`AccentManager`, `ScaledAppCompatActivity`, tokens `switch_*`, drawables, ícones RetroArch monochrome com `setColorFilter`, dock/home row/grid, foco D-pad |
| `game-player.md` | `GameActivity` + `GLRetroView`, `GameLoaderHelper`, saves/states, `SecondaryDisplayActivity` (Presentation), `GameTouchOverlay`, gamepad HylianBox |
| `scraping.md` | `KwiqIgdbClient`/`KwiqTheGamesDbClient`/`SteamGridDbClient`, `GamingCovers` (`localCoverUri ?: remoteUrl` → `filesDir/covers/`), `InternalSimpleBrowser` (WebView), throttling e chaves em `SwtPrefs` |
| `dashboard-ktor.md` | **Ktor 3.1.3 CIO** (não NanoHTTPD), `DashboardServer` (`embeddedServer(CIO)`), `DashboardService` foreground `dataSync`, endpoints `/api/*`, `SelfHostedSessionStore`, `NetworkUtils`, assets `assets/dashboard/` |
| `rcheevos.md` | `:rcheevos` nativo (CMake, `RC_DISABLE_LUA=1`, `rc_client_t`), `getMemoryRegion` passthrough, `RAApi`/`RaHttpClient`/`RaUserAgent`/`AndroidRASignatureProvider`, `RaProfileActivity`/`AchievementsActivity`/`RaOverlayView`, hardcore gating |
| `build-compat.md` | Matriz de compatibilidade (AGP/Kotlin/JDK/SDK/NDK/CMake/Gradle), `libs.versions.toml`, `settings.gradle.kts`, comandos de build, `install.sh` + task VS Code |

---

## 1. Arquitetura em 10 segundos

```
:app (br.com.redclaw.swt, Views)
  ├─ theme/          ThemeManager + AccentManager + RetroArchIcons
  ├─ views/          LibraryActivity (LAUNCHER) + GameGrid + Apps (CategoryTag) + Settings (SAF) + ScaledAppCompatActivity
  ├─ game/           GameActivity (GLRetroView + GameLoaderHelper) + SecondaryDisplayActivity (Presentation) + GameTouchOverlay
  ├─ cores/          CoreUpdaterImpl (OkHttp → filesDir/cores/1.17.0/<abi>/lib<core>_libretro_android.so)
  ├─ scraping/       KwiqIgdbClient + KwiqTheGamesDbClient + SteamGridDbClient + GamingCovers + SwtPrefs
  ├─ browser/        InternalSimpleBrowser (WebView + chips + long-press → covers/)
  ├─ dashboard/      DashboardServer (Ktor CIO) + DashboardService (foreground) + SelfHostedDashboardApi/SessionStore/Settings + NetworkUtils
  ├─ ra/ui/          RaProfileActivity + AchievementsActivity + RaOverlayView (popup unlock)
  ├─ utils/          SwtPrefs (SharedPreferences wrapper p/ theme/accent)
  └─ ui/             RetroArchIcons helper
  │
  ├─ :libretrodroid          (vendored HylianBox tag 0.13.2, namespace com.swordfish.libretrodroid, JNI getMemoryRegion → ByteBuffer, oboe + libretro-common)
  ├─ :retrograde-util        (vendored Lemuroid, com.swordfish.lemuroid.common — coroutines, compose utils, graphics, math)
  ├─ :retrograde-app-shared  (vendored Lemuroid, com.swordfish.lemuroid.lib — SystemID/CoreID/GameSystem, SerialScanner, LemuroidLibrary/WorkManager, GameLoader, StorageProviderRegistry/SAF, Room + KSP)
  ├─ :lemuroid-metadata-libretro-db (vendored, com.swordfish.lemuroid.metadata.libretrodb — LibretroDBMetadataProvider, cascade CRC→serial→filename→ext, libretro-db.sqlite via Room+kapt)
  ├─ :lemuroid-touchinput    (vendored, com.swordfish.touchinput.controller — overlays Compose, TiltConfiguration, TouchControllerID)
  └─ :rcheevos               (nativo, br.com.redclaw.swt.ra — rcheevos C v12.5.0 SHA 1433173220a7, CMake, RC_DISABLE_LUA=1, rc_client_t + RAApi)

lemuroid-cores/ (pasta referência, não módulo — CORES_VERSION=1.17.0, update_cores.sh replica buildbot nightly)
```

**Fluxo de dados (detecção → jogo):** `SettingsActivity` (SAF `OpenDocumentTree` → `StorageProviderRegistry`) → `LemuroidLibrary` (WorkManager scan) → `LibretroDBMetadataProvider` (cascade) → `LibraryActivity` (home row + cards) → `GameGridActivity` (search/filter) → `GameActivity` (CoreUpdater resolve `.so` → `GameLoaderHelper` → `GLRetroView.loadGame`) → `RaSessionManager` (rcheevos `rc_client` + `getMemoryRegion`) → `RaOverlayView` (unlock) + `RaProfileActivity`.

**Flavors:** sem flavors (1 APK). Cores baixados por ABI do device (`Build.SUPPORTED_ABIS[0]` → `arm64-v8a`/`armeabi-v7a`/`x86_64`/`x86`).

---

## 2. Como foi implementado (resumo build)

**Fase 0 — Esqueleto:** JDK 17 Temurin `~/android-setup/jdk17` + SDK `~/android-setup/sdk` (platforms android-35, build-tools 35, platform-tools, NDK 27.2, CMake 3.22.1) + Gradle 8.14. `settings.gradle.kts` com `include` dos 6 módulos + `local.properties`, `gradle/libs.versions.toml`, `:libretrodroid` copiado do HylianBox (22 MB src, namespace preservado), `:app` mínimo (`SwtApp` + `MainActivity` redirect + `colors.xml` Switch + `themes.xml` enxuto + adaptive `ic_launcher`). `BUILD SUCCESSFUL` 43s (Kotlin 1.9.22→2.1.20 fix para `Incompatible classes` do stdlib 2.1.20/lifecycle 2.7.0).

**Fase 1 — Vendoring Lemuroid:** `Swordfish90/Lemuroid` SHA `53752bf`, 4 módulos copiados com `build.gradle.kts` reescritos p/ AGP 8.11.1/Kotlin 2.1.20/compileSdk 35/Java 17 + deps via `libs.versions.toml` (sem Dagger, Room mantido com KSP/kapt). Correções: `flow-preferences` `com.fredporciuncula.flow-preferences` → `com.fredporciuncula` 1.8.0, `RetrogradeAppCompatActivity` typo `appCompatActivity`, `toLowerCase`→`lowercase`, `lemuroid-touchinput` duplicatas `com.swordfish.lemuroid.common/*` removidas + `implementation(project(":retrograde-util"))`, `LibretroDBMetadataProvider` lowercase. `CoreUpdaterImpl` (OkHttp, `BASE_URL=https://github.com/Swordfish90/LemuroidCores/raw/1.17.0`, `ProgressCallback`, `DownloadResult`, `BASE_URL` por ABI) + `lemuroid-cores/CORES_VERSION` + `update_cores.sh` (buildbot). `BUILD SUCCESSFUL` 15s após fixes.

**Fase 2A — Tema Switch:** `ThemeManager`/`AccentManager` portados de HylianBox `ui/switchui/` (DayNight via `AppCompatDelegate`, 12 accents com `ThemeOverlay.Swt.Accent.*`, `colorPrimary`/`colorAccent` + helpers `drawableBuilders` para focus/border/ripple), `ScaledAppCompatActivity` (aplica overlay antes de `super.onCreate()`), `SwtPrefs` (SharedPreferences wrapper p/ theme/accent), `RetroArchIcons` (helper mapeando dock → `ic_dock_*` PNG com `setColorFilter(accent)`), `themes.xml` expandido (BottomSheet/Splash/SwitchButton/SwitchSectionHeader etc.), drawables `bg_switch_*`/`switch_thumb|track` color states, `dimens.xml` 40+ tokens, `anim/slide_in_up|out_down`, `splash_artwork.xml`, 12 PNGs RetroArch monochrome em `drawable-nodpi` via `libretro/retroarch-assets` shallow clone. `SwtApp.onCreate → ThemeManager.applyAtStartup()`, `MainActivity → ScaledAppCompatActivity`. `BUILD SUCCESSFUL`.

**Fase 2B-E + 3 + 4 + 5 + 6 (paralelo):** 4 agentes deep/visual-engineering em paralelo:
- `Dashboard` NanoHTTPD (Kwiq) → **migrado para Ktor 3.1.3 CIO** a pedido (ver `dashboard-ktor.md`)
- `Scraping` (IGDB OAuth2 + TGB `ByGameName` + SGDB `Bearer` + `GamingCovers` + `InternalSimpleBrowser`)
- `ThemeManager` base para `LibraryActivity` (home row + dock 5 círculos) e `GameGrid/Apps/Settings` (3 Activities) + `GameActivity` (GLRetroView + DS)
- `rcheevos` (`:rcheevos` CMake + `rc_client_t` + `RAApi`)

Todos com `BUILD SUCCESSFUL` incremental (2s–26s), manifest com `LibraryActivity` LAUNCHER + 7 activities + `DashboardService` foreground `dataSync`.

**Estado final:** `176 tasks, BUILD SUCCESSFUL`, APK `app/build/outputs/apk/debug/app-debug.apk` (40 MB), 10/10 fases verdes.

---

## 3. Comandos essenciais

```bash
./gradlew :app:assembleDebug --console=plain          # build debug
./gradlew :rcheevos:assembleDebug :libretrodroid:assembleDebug # nativo só
./install.sh                                          # build + adb install -r
./install.sh --launch                                 # + am start LibraryActivity
./install.sh --build-only | --install-only | --help
adb devices -l && adb logcat | grep -E "Swt|DashboardServer|rcheevos"
```

**Instalar via ADB:** device em `unauthorized` (`R9QX602Y1CM`) — desbloquear tela → aceitar diálogo “Permitir depuração USB” (RSA). Se não aparecer: `adb kill-server && adb start-server && adb devices`. Script `install.sh` (POSIX sh, distro-agnostic, autodetect `adb` em PATH/`~/android-setup/sdk`/`$ANDROID_SDK_ROOT`) detecta `unauthorized`/`offline`/multi-device e instrui. Task VS Code `.vscode/tasks.json` → `Swt: Build & Install` / `Build & Install & Launch`.

---

## 4. Onde ler cada detalhe

- **Regras de ouro + por quê Views/Switch/12 accents** → `.agents/ui-switch.md`
- **Por que runtime download (rcheevos não exige copiar cores, `getMemoryRegion` ByteBuffer)** → `.agents/cores-libretrodroid.md`
- **Por que Ktor CIO e não NanoHTTPD/Netty (Android, HylianBox pattern)** → `.agents/dashboard-ktor.md`
- **Throttling IGDB 4 req/s, SGDB 1 req/s, chaves em prefs** → `.agents/scraping.md`
- **Hardcore gating, `RC_DISABLE_LUA=1`, `rcheevos/User-Agent` validado por RAdmin** → `.agents/rcheevos.md`
- **Saves (`StatesManager` slot 0 + `SavesManager` SRAM), menu overlay `frameSpeed=0`** → `.agents/game-player.md`
- **AGP 8.11.1 + Kotlin 2.1.20 compat, `libs.versions.toml` catalog, `settings.gradle.kts` includes** → `.agents/build-compat.md`
- **Árvore completa com propósito de cada arquivo/pasta** → `.agents/files-structure.md`
- **Visão geral com grafo e fluxo** → `.agents/architecture.md`
