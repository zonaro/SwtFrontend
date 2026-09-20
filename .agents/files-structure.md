# Estrutura de Arquivos — SwtFrontend

> Árvore com propósito. Caminhos relativos à raiz `/mnt/GIT/SwtFrontend`.

```
SwtFrontend/
├── agents.md                         # Sumário (este repo) → aponta para .agents/
├── .agents/                          # Documentação técnica detalhada (10 arquivos)
│   ├── README.md, architecture.md, files-structure.md (este), cores-libretrodroid.md,
│   │   ui-switch.md, game-player.md, scraping.md, dashboard-ktor.md, rcheevos.md, build-compat.md
│   └── (gerado por este task)
├── .omo/plans/swt-frontend.md        # Plano original em fases 0-6 (momus OKAY, getMemoryRegion fix)
├── settings.gradle.kts               # 6 includes + jitpack, FAIL_ON_PROJECT_REPOS
├── build.gradle.kts (raiz)           # alias apply false (application/library/kotlin-jvm)
├── gradle.properties                 # org.gradle.java.home=~/android-setup/jdk17, parallel, cache
├── gradle/libs.versions.toml         # Catalog: agp 8.11.1, kotlin 2.1.20, lifecycle 2.7.0, ksp 2.1.20-1.0.32, ktor 3.1.3, room 2.7.0, etc.
├── gradle/wrapper/ + gradlew        # Gradle 8.14
├── local.properties                  # sdk.dir=~/android-setup/sdk
├── install.sh                        # POSIX sh build+install via adb (autodetect adb, JAVA_HOME, unauthorized help, --launch)
├── app/
│   ├── build.gradle.kts              # namespace br.com.redclaw.swt, compileSdk 35, minSdk 24, 4 vendored + rcheevos + ktor cio/cors/status.pages
│   ├── proguard-rules.pro
│   ├── src/main/
│   │   ├── AndroidManifest.xml       # LibraryActivity LAUNCHER + MainActivity redirect + browser/GameGrid/Apps/Settings/Game/SecondaryDisplay + DashboardService dataSync + RA activities
│   │   ├── java/br/com/redclaw/swt/
│   │   │   ├── SwtApp.kt             # Application, ThemeManager.applyAtStartup()
│   │   │   ├── theme/ ThemeManager.kt, AccentManager.kt (12 accents, ThemeOverlay.Swt.Accent.*)
│   │   │   ├── utils/SwtPrefs.kt     # SharedPreferences wrapper p/ theme/accent
│   │   │   ├── ui/RetroArchIcons.kt  # Dock icons → ic_dock_* PNG + setColorFilter(accent)
│   │   │   ├── views/                # ScaledAppCompatActivity (accent before super.onCreate), LibraryActivity (home row + dock 5), GameGrid/Apps (CategoryTag)/Settings (SAF), adapters Dock/HomeGames/LibraryCards
│   │   │   ├── game/ GameActivity.kt (GLRetroView + menu overlay frameSpeed=0) + GameLoaderHelper.kt + GameTouchOverlay.kt (Views d-pad/A/B/L/R) + SecondaryDisplayActivity.kt (Presentation) + README.md
│   │   │   ├── cores/ CoreUpdaterImpl.kt (OkHttp, BASE_URL LemuroidCores 1.17.0, ProgressCallback) + README.md
│   │   │   ├── scraping/ KwiqIgdbClient.kt + KwiqTheGamesDbClient.kt + SteamGridDbClient.kt + GamingCovers.kt + SwtPrefs.kt (keys) + JsonHelpers.kt + SwtScrapingModels.kt
│   │   │   ├── browser/ InternalSimpleBrowser.kt (WebView + WebViewClient + SslErrorHandler + chips + long-press → filesDir/covers/)
│   │   │   ├── dashboard/ DashboardServer.kt (Ktor CIO embeddedServer) + DashboardService.kt (foreground) + SelfHostedDashboardApi/SessionStore/Settings + NetworkUtils + UserRole + README.md
│   │   │   └── ra/ui/ RaProfileActivity.kt (getProfile via RaUserProfileRepository) + AchievementsActivity.kt (nativeGetAchievementListJson → RecyclerView) + RaOverlayView.kt (unlock toast) + SquareFrameLayout.kt
│   │   ├── res/
│   │   │   ├── values/colors.xml (90 linhas, switch_bg/dark/light + 12 accent_* + switch_text_* + color_* M3 mappings) + values-night/colors.xml (73 linhas, dark)
│   │   │   ├── values/themes.xml (Theme.Swt Material3.DayNight + 12 ThemeOverlay.Swt.Accent.* + BottomSheet/Splash/SwitchButton/SwitchSectionHeader)
│   │   │   ├── values/strings.xml (app_name + browser_* + accent_* 12 + grid_* + apps_* CategoryTag + settings_ra_* + dock_* + RA ra_* + dialog_*)
│   │   │   ├── values/dimens.xml (40+ tokens Switch spacing/type)
│   │   │   ├── values-night/colors.xml (dark)
│   │   │   ├── drawable/ bg_switch_button[_focus].xml + bg_settings_nav_row[_focus|active].xml + bg_settings_section.xml + bg_switch_dialog.xml + ic_trophy.xml + view_ra_overlay bg
│   │   │   ├── drawable-nodpi/ ic_dock_* (12 PNGs RetroArch monochrome: games/apps/achievements/profile/settings/all_games/collections/quickmenu/network/exit/help/info)
│   │   │   ├── layout/ activity_library.xml (Space + focused label + home_row + cards_grid + dock) + activity_game.xml (retro_view_container + touch_overlay + menu_overlay + RaOverlayView) + activity_game_secondary.xml + activity_game_grid.xml + activity_apps.xml + activity_settings.xml + activity_ra_profile.xml + activity_achievements.xml + item_* (home_game, library_card, game_grid, app, category_header, rom_folder, dock_button, ra_achievement, ra_profile_*) + view_ra_overlay.xml + dialog_achievements.xml
│   │   │   ├── anim/ slide_in_up.xml + slide_out_down.xml
│   │   │   ├── color/ switch_thumb.xml + switch_track.xml + switch_text_field_stroke.xml (state lists)
│   │   │   └── mipmap-anydpi-v26/ic_launcher.xml (adaptive, accent_cyan bg + vector foreground)
│   │   └── assets/dashboard/         # HTML/JS/CSS servidos via Ktor staticResources
│   └── build/outputs/apk/debug/app-debug.apk (40 MB) [gerado]
├── libretrodroid/                    # vendored HylianBox 0.13.2, com.swordfish.libretrodroid, oboe 1.5 b15f5e39 + libretro-common, CMake + getMemoryRegion ByteBuffer passthrough
├── retrograde-util/                  # vendored Lemuroid, com.swordfish.lemuroid.common (common 5 arquivos duplicatas removidos de touchinput)
├── retrograde-app-shared/            # vendored Lemuroid, com.swordfish.lemuroid.lib (ksp Room, paging 3.3.6)
├── lemuroid-metadata-libretro-db/    # vendored, com.swordfish.lemuroid.metadata.libretrodb (kapt, libretro-db.sqlite)
├── lemuroid-touchinput/              # vendored, com.swordfish.touchinput.controller (Compose, padkit 1.0.0, Xcontext-receivers)
├── rcheevos/                         # nativo br.com.redclaw.swt.ra, rcheevos C v12.5.0 1433173220a7 (RC_DISABLE_LUA=1), src/main/cpp (ra_jni.c + rcheevos/src/* + CMakeLists.txt), src/main/java (RAApi + RaHttpClient + RaUserAgent + RaSessionManager + RaUserProfileRepository + RaOverlay)
└── lemuroid-cores/                   # pasta referência (não include), CORES_VERSION 1.17.0 + update_cores.sh (KNOWN_CORES 20, curl/jq, buildbot)
```

**Pontos de atenção:**
- `app` é o único `application`; demais são `library` (exceto `lemuroid-cores` que não é módulo).
- `retrograde-app-shared` precisa de `retrograde-util` e `lemuroid-touchinput` → `lemuroid-touchinput` depende de `retrograde-util` (GraphicsUtils/GeometryUtils removidos dele para evitar `Type is defined multiple times`).
- `lemuroid-metadata-libretro-db` depende de `retrograde-app-shared` + `retrograde-util` (descomentado após Fase 1).
- `rcheevos` é `applicationId` não necessário (library), mas `buildCMakeDebug[4 ABIs]` gera `libra_jni.so` por ABI.
- `app/build` e `*/build` são `UP-TO-DATE` após Fase 5 (176 tasks, 23 executed no último `assembleDebug`).
