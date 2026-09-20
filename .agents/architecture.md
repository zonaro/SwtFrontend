# Arquitetura — SwtFrontend

## Visão de módulos (Gradle)

```
SwtFrontend/
├── :app (br.com.redclaw.swt, Views, AGP application)
│   ├─ depende de :libretrodroid, :retrograde-util, :retrograde-app-shared,
│   │            :lemuroid-metadata-libretro-db, :lemuroid-touchinput, :rcheevos
│   └─ packaging jniLibs legacy=false, viewBinding=true, largeHeap=true
├── :libretrodroid (com.swordfish.libretrodroid, HylianBox 0.13.2 vendored)
├── :retrograde-util (com.swordfish.lemuroid.common)
├── :retrograde-app-shared (com.swordfish.lemuroid.lib, Room+KSP)
├── :lemuroid-metadata-libretro-db (com.swordfish.lemuroid.metadata.libretrodb, kapt)
├── :lemuroid-touchinput (com.swordfish.touchinput.controller, Compose, -Xcontext-receivers)
└── :rcheevos (br.com.redclaw.swt.ra, NDK CMake, RC_DISABLE_LUA=1)

lemuroid-cores/  pasta referência (não é include), CORES_VERSION=1.17.0
```

**`settings.gradle.kts`:**
```kotlin
include(":app", ":libretrodroid", ":retrograde-util",
        ":retrograde-app-shared", ":lemuroid-metadata-libretro-db",
        ":lemuroid-touchinput", ":rcheevos")
```

**Dependências chave (`app/build.gradle.kts`):**
- `libs.androidx.core.ktx / appcompat / material / lifecycle-runtime / activity-ktx / recyclerview / constraintlayout`
- `libs.coil / okhttp / coroutines.android / workmanager / gson`
- `libs.ktor.server.core / cio / cors / status.pages` (dashboard)
- `project(:rcheevos)` (RetroAchievements nativo)

## Fluxo de dados (ROM → jogo → conquistas)

1. **SettingsActivity** — SAF `ActivityResultContracts.OpenDocumentTree` → URIs persistidas → `StorageProviderRegistry` (retrograde-app-shared) + `SwtPrefs` (prefs).
2. **LemuroidLibrary** — WorkManager scan em background, `LibretroDBMetadataProvider` cascade `CRC → serial → filename → ext` (via `libretro-db.sqlite` Room) classifica `SystemID`/`CoreID`/`GameSystem`.
3. **LibraryActivity** — Observa `Game` + `GameSystem`, exibe home row (recentes, horizontal) + cards `Coleções / Todos os Jogos / Apps / Dashboard` + dock 5 círculos (tint accent). Foco D-pad, `ThemeManager` DayNight.
4. **GameGridActivity** — `RecyclerView` grid, search `EditText` + filter por `GameSystem` + sort alfabético/recente, `Coil` carrega `GamingCovers.localCoverUri ?: remoteUrl`.
5. **CoreUpdaterImpl** — `CoreID` → `Build.SUPPORTED_ABIS[0]` → `https://github.com/Swordfish90/LemuroidCores/raw/1.17.0/lemuroid_core_<core>/src/main/jniLibs/<abi>/lib<core>_libretro_android.so` → `filesDir/cores/1.17.0/` (OkHttp, `ProgressCallback`, limpeza old versions).
6. **GameActivity** — `GLRetroView` (libretrodroid) + `GameLoaderHelper` (resolve `.so` via `CoreUpdater` + `DirectoriesManager` + `Game` file) → `libretro API` + `GameTouchOverlay` (Views) + `GamepadInputHandler` (`onKeyDown`/`onGenericMotionEvent` → `GLRetroView.sendKeyEvent/sendMotionEvent`) + `StatesManager` (slot 0 + auto-save) + `SavesManager` (SRAM) + DS layout via `melonds_screen_layout1`/`desmume_screens_layout`.
7. **SecondaryDisplayActivity** — `Presentation` (Kwiq pattern) para bottom screen DS em display externo (`DisplayManager`).
8. **RaSessionManager** (`:rcheevos`) — `rc_client_t` callbacks → `getMemoryRegion(int id) → ByteBuffer` (libretrodroid passthrough `LibretroDroid.java:146-159`) + `RaBridgeHttp` (OkHttp, `dorequest.php`) + `RAApi` (Web API `API_GetUserSummary` etc., assinatura `MD5(id+user+hardcore)` via `AndroidRASignatureProvider`, `User-Agent: SwtFrontend/1.0.0 (Android) rcheevos/12.4.0`) → `RaOverlayView` unlock popup + `RaProfileActivity`/`AchievementsActivity` + rich presence.
9. **DashboardServer** — Ktor CIO `embeddedServer(CIO, port, host=0.0.0.0)` + `CORS` + `StatusPages` + cookie `swt_token` via `SelfHostedSessionStore` (SharedPreferences) + `SelfHostedDashboardApi` (gaming/ROMs/covers) + `DashboardService` foreground `dataSync` + `NetworkUtils` LAN IP.
10. **Scraping** — `KwiqIgdbClient` (OAuth2 Twitch, 4 req/s) / `KwiqTheGamesDbClient` (`ByGameName`) / `SteamGridDbClient` (`Bearer`, `autocomplete`+`grids/game/{id}`, 1 req/s) → `GamingCovers` salva `filesDir/covers/` → `InternalSimpleBrowser` (WebView `WebViewClient` + `SslErrorHandler` + site chips + long-press download).

## ABIs e storage

- **ABIs:** `arm64-v8a` (primário), `armeabi-v7a`, `x86_64`, `x86` — `CoreUpdater` usa `Build.SUPPORTED_ABIS[0]`.
- **Files:** `filesDir/cores/<version>/` (`.so`), `filesDir/covers/` (capas), `assets/dashboard/` (HTML/JS/CSS Ktor `staticResources`), `cacheDir/ra_user_profile.json` (RA cache 10 min).

## Por que sem flavors / sem Compose

1 flavor (build universal) para simplificar distribuição; Compose evitado para manter Views puro idêntico ao HylianBox (D-pad focus, `ScaledAppCompatActivity` before `super.onCreate()`, tint via código).
