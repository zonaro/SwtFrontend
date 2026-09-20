# RetroAchievements — SwtFrontend (:rcheevos)

> **Conquistas reais** em cores compatíveis, com hardcore, rich presence e popup unlock. `:rcheevos` nativo (`rc_client_t` RetroArch-style) + `RAApi` Web API + UI `RaProfile`.

## Por que :rcheevos como módulo nativo

- **rcheevos = lib C** (`https://github.com/RetroAchievements/rcheevos` `v12.5.0` SHA `1433173220a7eaede6a9ed7a18e94117be1821e0`, `MIT`) compilada via `CMake 3.22.1` + NDK `27.2` (4 ABIs → `libra_jni.so` por ABI). `RC_DISABLE_LUA=1` (sem Lua), `RC_CLIENT_SUPPORTS_HASH` para `rc_hash`.
- **Não exige copiar cores:** lê memória via `libretrodroid.getMemoryRegion(int id) → ByteBuffer` (passthrough JNI `libretrodroid/src/main/cpp/libretrodroidjni.cpp` → `libretrodroid.h:33` → `LibretroDroid.java:146-159` → `EglVideoRecorder`/`GLRetroView`). HylianBox já vendou esse passthrough; Swt reusa.
- **Caminho `rc_client_t` (RetroArch-style):** mais completo que `rc_runtime_*` simples (login token, `identify_and_load_game` com hash, `do_frame`/`idle`, savestate hooks, fila unlock, rich presence). Decisão travada em `.omo/plans/swt-frontend.md` Fase 5.

## Módulo :rcheevos (br.com.redclaw.swt.ra)

- **Build:** `rcheevos/build.gradle.kts` `com.android.library` + `kotlin.android`, `compileSdk 35`, `minSdk 24`, `externalNativeBuild cmake path src/main/cpp/CMakeLists.txt version 3.22.1`, `kotlinOptions jvmTarget 17`, `implementation(kotlin-stdlib 2.1.20 + lifecycle-runtime + okhttp 4.12.0 + coroutines-android + timber + security-crypto)`, `namespace br.com.redclaw.swt.ra`.
- **CMake:** `src/main/cpp/CMakeLists.txt` (`add_library(ra_jni SHARED ra_jni.c rcheevos/src/rcheevos.c rcheevos/src/rhash/hash.c ...)` + `RC_DISABLE_LUA=1` + `ANDROID_STL=c++_static`).
- **JNI:** `src/main/cpp/ra_jni.c` (wrapper `rc_client` + `getMemoryRegion` callback que chama `LibretroDroid.getMemoryRegion` via `GetMethodID` + `ByteBuffer`).

### Kotlin bridge (src/main/java/br/com/redclaw/swt/ra/)

- **`jni/RcheevosJni.kt`** — `object RcheevosJni { external fun nativeGetAchievementListJson(): String; external fun nativeGetGameInfoJson(): String; external fun getUserAgentClause(): String }` (carrega `System.loadLibrary("ra_jni")`).
- **`jni/RaBridgeHttp.kt`** — `suspend fun executeServerRequest(url, postData): String` (OkHttp, `User-Agent: SwtFrontend/1.0.0 (Android) rcheevos/12.4.0`).
- **`session/RaSessionManager.kt`** — orquestra `rc_client_t`:
  - `sealed RaSessionState` (`Idle/NotLoggedIn/LoggingIn/LoadingGame/Running(game: RaGameSummary)/Failed`)
  - `data RaGameSummary(id, title, hash, badgeUrl, numCoreAchievements, numUnlocked, pointsCore, pointsUnlocked)`
  - `fun start(context, game: Game, retroView: GLRetroView)` → `rc_client_begin_login_with_password` → `rc_client_begin_login_with_token` (persist `EncryptedSharedPreferences`) → `rc_client_begin_identify_and_load_game` (hash via `rc_hash`) → `Running`
  - `fun doFrame()` → `rc_client_do_frame(retroView.getMemoryRegion)` por `FrameRendered` + `idle` em pause.
  - `fun onClientEvent(event, payloadJson)` → `SharedFlow<RaClientEvent>` (`RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED` → `RaOverlayView.showUnlock`).
  - `hardcore: Boolean` → `rc_client_set_hardcore_enabled` (bloqueia savestate/rewind/cheat, ver abaixo).
- **`session/RaClientEvent`** + `RaGameSummary` etc.
- **`auth/RaCredentialStore.kt`** — `EncryptedSharedPreferences` (`AES256_GCM` `MasterKey`), `getUsername/getToken/getApiKey`, `setCredentials/setApiKey`, `hasCredentials/hasApiKey`, `clear`, `readPendingAwards/writePendingAwards` (award journal `ra_pending_awards_v1` survive logout).
- **`auth/RaAuthService.kt`** — `suspend fun login(username, password): Result<String>` (`rc_client_begin_login_with_password` + `dorequest.php?r=login2`), `fun loginWithToken`.
- **`data/RaUserProfileRepository.kt`** — Web API `API_GetUserSummary/Awards/CompletionProgress` (`https://retroachievements.org/API/*.php?u=&y=&g=3&a=5`), `RaHttpClient` + `RaCredentialStore` Web API key (`y` de `controlpanel.php`), cache `cacheDir/ra_user_profile.json` 10 min, `RaUserProfile` (`totalPoints/Softcore/True, rank, lastGame, recentlyPlayed, recentAchievements, awards, completionProgress`), `RaAwards/RaCompletionProgress`.
- **`data/RaCatalogRepository.kt` / `RaLiveUnlocks.kt` / `RaSaveState.kt`** — `liveUnlocks(identity)` (`nativeGetGameInfoJson` before/after + `nativeGetAchievementListJson` → `Set<Long>` unlocked, checa `category 1` core, `num_core_achievements`).
- **`api/RaHttpClient.kt`** — `class RaHttpClient(private val userAgent: String, client: OkHttpClient)` (`header User-Agent`, `execute(url, postData)` → `RaHttpResponse`).
- **`api/RaUserAgent.kt`** — `object RaUserAgent { fun build(context): String = "SwtFrontend/1.0.0 (Android $version; $device) rcheevos/12.4.0" }` (frozen, RAdmin valida formato).
- **`api/RAApi.kt` + `AndroidRASignatureProvider.kt`** — `dorequest.php` (`r=login2/startsession/patch/unlocks/awardachievement` com `MD5(id+user+hardcore)` via `AndroidRASignatureProvider`) + Web API (`API_GetGameInfoAndUserProgress` etc.), contrato `rcheevos/User-Agent` validado por RAdmin.
- **`sync/RaAwardOutbox.kt` / `RaPendingAwards.kt`** — fila `awardachievement` com `MD5`, `submit` + `ping` (`dorequest.php` `r=ping`), `RaCredentialStore` journal.

## UI (app/ra/ui/)

- **`RaProfileActivity.kt`** (`ScaledAppCompatActivity`, `activity_ra_profile.xml` ScrollView + 3 TextViews `ra_profile_title/status/summary`) — `RaCredentialStore.getUsername()` + `hasApiKey()` → `RaUserProfileRepository(this, credentials, RaHttpClient(RaUserAgent.build(this))).getProfile()` → `buildString` (`Points/Rank/Awards/Completion/Presence/LastGame/Recent`), `lifecycleScope.launch`.
- **`AchievementsActivity.kt`** (`ScaledAppCompatActivity`, `activity_achievements.xml` RecyclerView + empty) — `RcheevosJni.nativeGetAchievementListJson()` (JSONArray `id/title/description/points/unlocked`) → `UiAchievement` → `Adapter` (`item_ra_achievement.xml` 48dp badge + title/desc + points/state `Locked/Unlocked/Hardcore`), `alpha 0.55` se locked.
- **`RaOverlayView.kt`** (`FrameLayout`, `view_ra_overlay.xml` bg `#CC1E1E1E` + badge 40dp + title/desc) — `showUnlock(title, desc, badgeUrl, 4000ms)` (`badge.load` Coil `crossfade` + `placeholder ic_trophy`, `animate alpha 1f 220ms` + `postDelayed hide`, `hide()` `alpha 0f` + `animate().setListener(null)`), `showPresence` ticker. `GameActivity` observa `RaSessionManager` `SharedFlow<RaClientEvent>` → `raOverlayView.showUnlock`.
- **Layouts:** `activity_ra_profile.xml`, `activity_achievements.xml`, `item_ra_achievement.xml`, `view_ra_overlay.xml` (todos `switch_*` tokens), `drawable/ic_trophy.xml` (vector cup, stroke 2).
- **Strings:** `ra_not_logged_in`, `ra_login_in_settings`, `ra_profile_loading/loaded/error`, `ra_achievements_title/empty/locked/unlocked/hardcore`, `ra_overlay_unlocked` (pt-BR).
- **Manifest:** `RaProfileActivity` + `AchievementsActivity` (`Theme.Swt`, `configChanges`).

## Hardcore gating

- `RaSessionManager.setHardcore(true)` → `rc_client_set_hardcore_enabled` → `rc_client` bloqueia `savestate`/`rewind`/`cheat` (se tentado, `RC_CLIENT_EVENT` erro). Swt ainda não tem cheat, mas `StatesManager` checa `hardcore` e desabilita `saveState(0)` se `true` (TODO documentado em `GameActivity` menu: “Salvar estado desabilitado em Hardcore”).

## Integração GameActivity

- `GameActivity.onCreate` após `loadGame` → `RaSessionManager.start(this, game, retroView)` → `doFrame` em `GLRetroView.FrameRendered` listener + `idle` em `onPause`.
- `LibraryActivity` dock `Conquistas` → `AchievementsActivity` (sem `gameId` → mostra últimos), `Perfil` → `RaProfileActivity` (requer `hasApiKey()`).
- **SettingsActivity** RA seção: `EditText username` + `password` → `RaAuthService.login` → `EncryptedSharedPreferences` token + `EditText Web API key` (captura via `InternalSimpleBrowser` `controlpanel.php` long-press) → `setApiKey`.
