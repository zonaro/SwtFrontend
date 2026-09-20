# Dashboard — Self-hosted via Ktor (CIO)

> Migrated from the legacy self-hosted HTTP server to Ktor (HylianBox pattern).
> License: GPLv3.

## Architecture

```
dashboard/
├── SelfHostedSettings.kt       # Config (port, users, SharedPreferences)
├── SelfHostedSessionStore.kt   # Persistent sessions (tokens in SharedPreferences)
├── UserRole.kt                 # Enum ADMIN / VISITOR
├── DashboardServer.kt          # Ktor CIO server with routing, CORS, auth
├── SelfHostedDashboardApi.kt   # Data layer (gaming, library, dashboard)
├── DashboardService.kt         # ForegroundService with persistent notification
├── NetworkUtils.kt             # IP/localization helpers
└── README.md                   # This file
```

## Endpoints

| Method | Route | Auth | Description |
|--------|-------|------|-------------|
| GET | `/` | Yes | Dashboard home |
| GET | `/login` | No | Login page |
| POST | `/api/login` | No | Authentication (cookie `swt_token`) |
| POST | `/api/logout` | Yes | End session |
| GET | `/api/session` | Yes | Current session info |
| GET | `/api/dashboard` | Yes | Summary (ROMs, storage, server) |
| GET | `/api/settings` | Admin | Server settings |
| GET | `/api/files` | Admin | List files |
| GET | `/api/gaming/platforms` | Admin | Supported platforms |
| GET | `/api/gaming/roms` | Admin | ROM list |
| GET | `/api/gaming/rom?key=` | Admin | ROM detail |
| DELETE | `/api/gaming/rom?key=` | Admin | Delete ROM |
| POST | `/api/gaming/rom/metadata` | Admin | Edit metadata |
| GET | `/api/gaming/rom/download?key=` | Admin | Download ROM |
| POST | `/api/gaming/rom/cover?key=` | Admin | Upload cover |
| GET | `/api/gaming/rom/cover?key=` | Admin | Download cover |
| DELETE | `/api/gaming/rom/cover?key=` | Admin | Remove cover |
| POST | `/api/gaming/rom/background?key=` | Admin | Upload background |
| GET | `/api/gaming/rom/background?key=` | Admin | Download background |
| DELETE | `/api/gaming/rom/background?key=` | Admin | Remove background |
| POST | `/api/gaming/upload` | Admin | Upload ROM |
| GET | `/api/gaming/search` | Admin | Search games (IGDB/TGB) |
| POST | `/api/gaming/resolve` | Admin | Resolve platform by extension |

## Web Assets

`app/src/main/assets/dashboard/` — Functional UI for managing library over the network:

- `index.html` — Dashboard home + library + settings
- `login.html` — Authentication screen
- `style.css` — Dark theme (Switch-like)
- `app.js` — Vanilla JavaScript (simple SPA)
- `favicon.svg` — Dashboard favicon

## Service

`DashboardService.kt` is a `ForegroundService` (type `DATA_SYNC`) that:

1. Starts the Ktor CIO server on the configured port (default: 7120)
2. Shows persistent notification with access URL
3. If port is busy, automatically walks up to the next available one
4. Stops the server when the service is destroyed

## Permissions

- `INTERNET` — HTTP server
- `POST_NOTIFICATIONS` — persistent notification (Android 13+)
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` — foreground service

## Integration

To start/stop the dashboard from an Activity:

```kotlin
import br.com.redclaw.swt.dashboard.DashboardService
import br.com.redclaw.swt.dashboard.SelfHostedPrefs
import br.com.redclaw.swt.dashboard.buildServerUrl

DashboardService.start(context)
DashboardService.stop(context)
val running = DashboardService.isRunning()
val settings = SelfHostedPrefs.load(context)
val url = buildServerUrl(settings.port)
```

## Default Credentials

| User | Password | Role |
|------|----------|------|
| Swt | (empty) | ADMIN |
| Visitante | (empty) | VISITOR |

## Supported Platforms

NES, SNES, GB, GBC, GBA, N64, NDS, Genesis/Mega Drive, Master System, Game Gear, PS1, PS2, PC Engine, Lynx, WonderSwan, Neo Geo Pocket, Atari 2600/7800, ColecoVision, Intellivision.

## Notes

- **No Compose/Hilt** — Views/Manual DI (HylianBox pattern)
- **Ktor 3.1.3 CIO** — lightweight, pure-Kotlin HTTP engine
- **Mock library** — ROMs are scanned from `filesDir/roms/<platform>/`. Populate via dashboard upload or copy ROMs manually.
- **Covers** — saved in `filesDir/covers/` and `filesDir/backgrounds/`
- **Search providers** — placeholder (IGDB/TGB integration to be connected in Phase 4)
