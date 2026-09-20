# .agents/ — Índice

> Pasta de documentação técnica do SwtFrontend. Cada arquivo aprofunda um domínio que o `agents.md` da raiz apenas sumariza.

| Arquivo | Quando ler |
|---|---|
| `architecture.md` | Visão geral, grafo de módulos, fluxo de dados, ABIs, storage |
| `files-structure.md` | Árvore completa com propósito de cada pasta/arquivo |
| `cores-libretrodroid.md` | LibretroDroid vendored + CoreUpdater runtime (1.17.0) |
| `ui-switch.md` | Regras visuais Switch, 12 accents, ThemeManager, ícones, dock |
| `game-player.md` | GameActivity, GLRetroView, saves, DS dual-screen, touch/gamepad |
| `scraping.md` | IGDB/TGB/SGDB, GamingCovers, InternalSimpleBrowser, throttling |
| `dashboard-ktor.md` | Ktor 3.1.3 CIO, DashboardServer/Service, endpoints, auth, assets |
| `rcheevos.md` | :rcheevos nativo, rc_client_t, getMemoryRegion, RAApi, UI |
| `build-compat.md` | Matriz AGP/Kotlin/JDK/SDK/NDK, libs.versions.toml, comandos |

**Convenções:**
- Código em `br.com.redclaw.swt` (app) — Views puro, `ScaledAppCompatActivity`.
- Vendored `com.swordfish.*` mantido com namespace original + headers GPLv3.
- `app` depende dos 4 módulos Lemuroid + `libretrodroid` + `rcheevos`; `rcheevos` é nativo (CMake) com `RC_DISABLE_LUA=1`.
- Build sempre via `gradle/libs.versions.toml` (catalog) e `settings.gradle.kts` (6 includes).
