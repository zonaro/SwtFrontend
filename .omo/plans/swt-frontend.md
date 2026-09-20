# Swt Frontend — Plano de Implementação

> Launcher Android para emuladores (frontend) com cores libretro internos + emuladores externos.
> Base: UI do HylianBox (Views/XML, estética Switch), biblioteca do Lemuroid (GPLv3), padrões do Kwiq (categorias, browser, dashboard, APIs), rcheevos (RetroAchievements).
> Namespace: `br.com.redclaw.swt` | Licença: GPLv3 (derivado de Lemuroid + HylianBox + LibretroDroid + rcheevos).

---

## Decisões travadas (confirmadas com o usuário)

1. **UI**: Views puro (Activities + XML), estilo HylianBox. Sem Compose. Tudo portado do Kwiq vai para Views.
2. **Cores**: integração rcheevos NÃO exige copiar cores (rcheevos = lib C separada que lê memória via libretrodroid `getMemoryRegion(int id)` → ByteBuffer — passthrough JNI já presente no libretrodroid vendado do HylianBox, `LibretroDroid.java:146-159`). Portanto: **cores via download runtime** (pattern Lemuroid free: submodule LemuroidCores como referência de versão + `CoreUpdater` baixa `.so` do GitHub raw por ABI, tag `CORES_VERSION`).
3. **Namespace**: `br.com.redclaw.swt`.
4. **libretrodroid**: vendado do HylianBox (`/mnt/GIT/HylianBox/libretrodroid`, tag 0.13.2 + passthrough de memória RA) → módulo `:libretrodroid` local. Não via JitPack (precisamos do passthrough de memória que HylianBox já adicionou).

---

## Arquitetura de módulos Gradle

```
SwtFrontend/
├── settings.gradle.kts          # include dos módulos + submodule cores
├── buildSrc/                    # versions catalog (deps.kt style, do Lemuroid)
├── :app                         # UI launcher em Views (br.com.redclaw.swt)
│                                #  - Activities (Library, Apps, Grid, Achievements, Profile, Browser, Settings, Game, Dashboard)
│                                #  - service locator (SwtApp), categorias, capas, scraping clients, dashboard NanoHTTPD
│                                #  - ThemeManager/AccentManager (12 accents, Switch aesthetic)
├── :retrograde-util             # VENDORED do Lemuroid (sem Android, utils puros) — GPLv3
├── :retrograde-app-shared       # VENDORED do Lemuroid — biblioteca/sistemas/scanner/storage/game loader
│                                #  (SystemID, CoreID, GameSystem, SerialScanner, LemuroidLibrary, GameLoader, StorageProviderRegistry)
├── :lemuroid-metadata-libretro-db  # VENDORED — LibretroDBMetadataProvider + asset libretro-db.sqlite
├── :lemuroid-touchinput         # VENDORED — controles touch do Lemuroid
├── :libretrodroid               # VENDORED do HylianBox — wrapper JNI libretro (+ memória RA)
├── :rcheevos                    # NATIVO (CMake/NDK) — vendored rcheevos C + wrapper JNI (pattern melonDS-android)
│                                #  + RAApi (Kotlin, cliente dorequest.php + Web API) no mesmo módulo ou em :app
├── lemuroid-cores/              # git submodule → Swordfish90/LemuroidCores (referência p/ CoreUpdater, NÃO compilado no APK)
└── :dashboard                   # (opcional) módulo separado p/ servidor web NanoHTTPD + assets HTML
```

- Flavors: **sem flavors no início** — 1 app. Cores baixados em runtime por ABI do device (arm64-v8a/armeabi-v7a/x86_64/x86).
- minSdk 24, targetSdk/compileSdk 35, Kotlin 2.x, AGP 8.11.x (igual HylianBox), JDK 17.

---

## Fases de implementação

### FASE 0 — Esqueleto compilável
**Objetivo**: projeto Gradle multi-módulo que compila, com app vazio + libretrodroid + tema base.
**Passos**:
1. CI/ambiente: instalar JDK 17 + Android SDK (platforms;android-35, build-tools), NDK (rcheevos/libretrodroid). `local.properties` com `sdk.dir`.
2. `settings.gradle.kts` + `build.gradle.kts` raiz + `buildSrc/deps` (versions) + `gradle.properties` + wrapper Gradle 8.14.
3. `:libretrodroid` copiado de `/mnt/GIT/HylianBox/libretrodroid` (namespace com.swordfish.libretrodroid mantido).
4. `:app` mínimo: `SwtApp` (Application, service locator vazio), `MainActivity` com `ActivityMain` XML placeholder + tema Material3 DayNight + `res/values/colors.xml` (tokens Switch) + `themes.xml`.
5. Manifest com launcher activity + permissões (`MANAGE_EXTERNAL_STORAGE` não — usar SAF; `POST_NOTIFICATIONS`; `INTERNET`).
**Done**: `./gradlew :app:assembleDebug` → APK instalável.
**Riscos**: SDK/NDK ausentes na máquina (instalar); JDK 25 default incompatível (usar JDK 17 via `org.gradle.java.home` ou toolchain).

### FASE 1 — Núcleo de biblioteca vendored (detecção automática de ROMs/sistemas)
**Objetivo**: indexar ROMs (SAF + local), classificar por sistema automaticamente, listar coleções.
**Passos**:
1. Clonar `Swordfish90/Lemuroid` (SHA pin) → copiar `:retrograde-util`, `:retrograde-app-shared`, `:lemuroid-metadata-libretro-db`, `:lemuroid-touchinput` para o projeto (remover deps desnecessárias: Dagger, Room).
2. Adaptar namespace/imports p/ `br.com.redclaw.swt.lib.*` (ou manter com.swordfish.*? — decisão: manter `com.swordfish.lemuroid.lib` MÍNIMO de mudanças para facilitar merge futuro de updates; documentar). [default proposto: manter namespace original + git tag source]
3. Submodule `lemuroid-cores` + script `update_cores.sh` (replicar update_cores.ipy: buildbot.libretro.com nightly → lemuroid_core_* → lista de CORE_VERSION).
4. `CoreUpdater` (app): baixa `.so` do `raw/<CORES_VERSION>/lemuroid_core_<core>/src/main/jniLibs/<abi>/...` → `filesDir/cores/<version>/`, limpeza de versões antigas, UI de progresso.
5. Estratégia de storage: `StorageProviderRegistry` do Lemuroid (SAF roots escolhidos pelo usuário em Settings) — detecção automática = user aponta pasta de ROMs, app escaneia e classifica.
**Done**: app escaneia pasta de ROMs (mock UI de "Biblioteca" lista sistemas + jogos detectados por CRC/serial/extensão).
**Riscos**: GPLv3 obriga manter licenses/headers; dependências do Lemuroid (Room, Dagger, WorkManager) precisam ser resolvidas — preferir WorkManager do AndroidX, remover Dagger (service locator manual do HylianBox).

### FASE 2 — UI HylianBox (Switch aesthetic) + launcher
**Objetivo**: launcher com visual HylianBox e navegação completa.
**Passos**:
1. `ThemeManager.kt`, `AccentManager.kt` (12 accents), `colors.xml` + `values-night`, `themes.xml` — copiar/adaptar do HylianBox (HEADERS GPL).
2. `LibraryActivity`: home row (jogos recentes) + cards "Coleções"/"Todos os Jogos"/"Apps"/"Dashboard" + dock inferior (Jogos, Apps, Conquistas, Perfil, Config) — estética Switch (ícones monochrome tintados).
3. `GameGridActivity` (grid "Todos os Jogos" com search/filter/sort) — adaptar SwitchGridActivity.
4. `AppsActivity`: lista de apps Android agrupados por categoria (porter data layer do Kwiq: `CategoryTag` + prefs + `ApplicationInfo.CATEGORY_*` + tags manuais) — UI em RecyclerView estilo Switch.
5. `SettingsActivity`: pastas de ROMs (SAF), accent, tema, cores download, providers (IGDB/TGB/SGDB keys), RA login.
6. Assets RetroArch Monochrome: baixar de `libretro/retroarch-assets` (pasta `monochrome/` ou similar) → `res/drawable*`, tint via `ImageView.setColorFilter(accent)` (12 accents do HylianBox).
**Done**: launcher navega entre telas, apps categorizados, temas aplicam em runtime.
**Riscos**: localizar o conjunto correto de ícones monochrome no repo de assets do RetroArch (verificar `pkg/` e `media/`); volume de port do Kwiq (mantê-lo enxuto).

### FASE 3 — Emulação (player interno) + 2 telas DS
**Objetivo**: jogar ROMs com cores libretro via LibretroDroid.
**Passos**:
1. `GameActivity` adaptada do HylianBox/Lemuroid: `GLRetroView` + `GameLoader` (loadGame do core .so baixado), save states, VRAM/otros, exit route.
2. Controles touch (lemuroid-touchinput) + gamepad (HylianBox gamepad pkg).
3. Dual-screen DS: opções do core melonDS/desmume (screen layout como overlay/stacked) + `Presentation`/MediaRouter para exibir bottom screen em display externo (pattern kwiq `SecondaryDisplayActivity`).
**Done**: joga ROM real (NES/GBA test) com saves; DS mostra 2 telas (tela dupla interna) e opcionalmente em display externo.
**Riscos**: integração GameLoader do Lemuroid é acoplada ao app Compose dele — portar com cuidado; OpenGL ES version por core.

### FASE 4 — Emuladores externos + scraping de capas + browser interno
**Objetivo**: jogar com emuladores externos (Daijishou-style) + capas/arte automática.
**Passos**:
1. Camada de plataformas externas: JSON por plataforma (formato Daijishou adaptado: `acceptedFilenameRegex`, `playerList[]` com `amStartArguments` usando `{file.uri}/{file.path}`), default player = core interno. Detecção automática = mesmos regex aplicados à biblioteca.
2. Scraping: `KwiqIgdbClient` + `KwiqTheGamesDbClient` portados (OkHttp, credentials em prefs) + novo `SteamGridDbClient` (Bearer key, `/search/autocomplete/{term}`, `/grids/game/{id}`).
3. `BrowserActivity` (port `InternalSimpleBrowser` p/ Views): WebView + site chips, long-press imagem → baixa capa → `filesDir/covers/`, SslErrorHandler.
4. Gestão de capas por jogo: `localCoverUri ?: remoteUrl` (pattern `KwiqGamingCovers`), mudar capa pelo menu de contexto.
**Done**: jogos mostram capas (IGDB/TGB/SGDB + manual via browser); lançamento por emulador externo configurável por plataforma.
**Riscos**: rate limits (IGDB 4 req/s, SGDB ~1 req/s) — throttling; chaves de API por usuário.

### FASE 5 — RetroAchievements (rcheevos)
**Objetivo**: conquistas integradas nos cores + telas HylianBox.
**Passos**:
1. `:rcheevos` módulo CMake: vendored rcheevos (SHA pin) + `RC_DISABLE_LUA=1` + wrapper JNI (bench: identificar jogo → hash por `rc_hash`, `rc_client_*` ou `rc_runtime_*` — **decisão**: usar caminho `rc_client_t` (RetroArch-style) por ser o mais completo: login token/password, identify+load, doFrame, savestates progress, fila de unlocks, rich presence).
2. Memory read: conectar callback JNI ao passthrough do libretrodroid — `LibretroDroid.getMemoryRegion(int id)` (retorna ByteBuffer; JNI `getMemoryRegion` em `libretrodroidjni.cpp`).
3. `RAApi` Kotlin (pattern melonDS `RAApi.kt`): `dorequest.php` (login2, startsession, patch, unlocks, awardachievement com assinatura MD5, submitlbentry, ping) + Web API (`API_GetUserSummary`, `API_GetGameInfoAndUserProgress`, `API_GetUserRecentlyPlayedGames`). User-Agent `<product>/<version> (Android) rcheevos/<version>`.
4. UI (adaptar HylianBox): `RaProfileActivity` (métricas, conquistas recentes) + `AchievementsActivity` (lista por jogo, grid/search) + popup unlock em runtime + rich presence.
5. Captura de API key via browser interno (controlpanel.php) + login por usuário/senha → token persistido (nunca senha).
**Done**: jogo com core compatível ganha conquistas reais (teste com conta RA), perfil e progresso na UI.
**Riscos**: RC versão/ABI no Android; hardcore gating (bloquear save state/rewind/cheat quando hardcore); accounts de teste.

### FASE 6 — Dashboard self-hosted
**Objetivo**: gerenciar coleção (ROMs, capas, descrições) da rede — sem emulador.
**Passos**:
1. Port `SelfHostedHttpServer` (NanoHTTPD) + `SelfHostedDashboardApi` p/ Views (os dois já são Kotlin puro — portar quase direto).
2. Endpoints: `/api/collection/*` (platforms, roms, covers upload/download, metadata edit via IGDB/TGB/SGDB), `/api/dashboard`, `/api/settings`, `/api/auth` (cookie de sessão), `/api/files`.
3. Assets web (HTML/JS/CSS) em `assets/dashboard/` — UI básica própria (não a do Kwiq).
4. Início automático do servidor (notificação persistente com URL/IP + QR).
**Done**: navegador desktop gerencia biblioteca (ver capas, editar desc, importar ROMs via upload).
**Riscos**: NanoHTTPD thread model; upload de arquivos grandes (streaming).

### FASE 7 — 2 telas na UI + polish + QA
**Objetivo**: frontend em display externo (Presentation) + acabamento.
**Passos**:
1. `SecondaryDisplayActivity` (pattern kwiq): espelhar/mirror a UI do launcher em display externo (HDMI/USB-C) — `MediaRouter` + `Presentation`.
2. Gamepad navigation (D-pad nos RecyclerViews — HylianBox já tem padrão), sons de UI opcionais.
3. QA final: `lsp_diagnostics` limpos, build release, teste em device, verificação do `/review-work`.

---

## Paralelização entre fases (delegação de subagentes)

| Janela | Fase(s) paralelas | Motivo |
|---|---|---|
| F1 ∥ F2 | Library vendored ∥ UI HylianBox | UI independe da library (dados mock primeiro) |
| F4 ∥ F5-part1 | Scraping/browser ∥ RAApi Kotlin + telas | Disjuntos (rede/UI diferentes) |
| F6 ∥ F5-part2 | Dashboard ∥ rcheevos nativo | Independência total |
| F7 | tudo converge | — |

Cada fase = 1+ subagente `deep`/`unspecified-high` com prompt contendo: TASK/EXPECTED OUTCOME/TOOLS/MUST DO/MUST NOT DO/CONTEXT.

---

## Riscos globais e mitigação

- **GPLv3**: todo derivado (Lemuroid, HylianBox, LibretroDroid, rcheevos) obriga GPLv3 + manter headers/licenças. Projeto será open-source.
- **Build local**: sem SDK/NDK/JDK17 na máquina atual — instalar na Fase 0.
- **Cores em runtime**: 1º uso precisa de rede; UX de progresso + retry; fallback aviso.
- **libretro-db.sqlite**: asset pesado no APK (aceito, ~10-20MB).
- **Compatibilidade de cores**: nem todo core funciona em todo device (ABI/GLES) — mostrar apenas cores baixáveis pra ABI do device.
- **Api keys (IGDB/TGB/SGDB/RA)**: campos em Settings, sem hardcode.

## Dívidas técnicas conscientes (aceitas)
- Namespace do Lemuroid mantido `com.swordfish.lemuroid.lib` para facilitar merge de updates (documentar no README).
- 1 módulo app grande (Views); só extrair :dashboard se crescer demais.
- libretrodroid vendado (já modificado pelo HylianBox p/ RA memory) — difícil voltar ao upstream.