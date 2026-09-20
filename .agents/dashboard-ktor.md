# Dashboard Ktor — SwtFrontend

> **Self-hosted** via **Ktor 3.1.3 CIO** (não NanoHTTPD), igual HylianBox. Gerencia coleção pela rede sem emulador.

## Por que Ktor e não NanoHTTPD

- **HylianBox usa Ktor 3.1.3 CIO** (não NanoHTTPD/Netty). `CIO` é puro Kotlin, leve, sem Netty (pesado, `~3 MB` + `~2000 métodos`), ideal para Android (Ktor recomenda CIO para mobile). `NanoHTTPD 2.3.1` (Kwiq) é `~100 KB` mas bloqueante (1 thread por request, `NanoHTTPD` `serve()` sincronizado) e sem `ContentNegotiation`/`CORS`/`StatusPages` nativos.
- **Task re-migrou** NanoHTTPD → Ktor (bg_2574a054, 1h15) a pedido do usuário: Ktor permite `embeddedServer(CIO, port=8066, host=0.0.0.0)` + `CORS` + `StatusPages` + `staticResources` + `ContentNegotiation(json)` + `Compression` sem `NanoHTTPD` `Response` manual.
- **Catalog:** `ktor = "3.1.3"` + `ktor-server-core/cio/content-negotiation/serialization-kotlinx-json/cors/status-pages` em `gradle/libs.versions.toml`; `app/build.gradle.kts` `implementation(libs.ktor.server.core/cio/cors/status.pages)` (removido `libs.nanohttpd` do código, mantido no catalog como deprecated para não quebrar cache).

## Arquivos (app/dashboard/)

- **`DashboardServer.kt`** — `embeddedServer(CIO, port, host)` + lifecycle.
  - `class DashboardServer(context) { val isRunning/isStarting: Boolean; var address: String?; var currentSettings: SelfHostedSettings; fun start(); fun stop() }`
  - `companion TAG`, `lock Any()`, `@Volatile engine: EmbeddedServer<*,*>?`, `state: STOPPED/STARTING/RUNNING` (`synchronized(lock)`).
  - `installContext` (HylianBox pattern: `application.attributes.put(CONTEXT_KEY, context)`) + `configureCors { allowHost("0.0.0.0") etc. }` + `configureStatusPages { exception<Throwable> { call.respond(HttpStatusCode.InternalServerError) } }` + `configureRouting()`.
  - `resolveLanAddress(): String` (`NetworkInterface` `Inet4Address` `isSiteLocalAddress` → `192.168.x.x` ou `WifiManager`) + `port` de `SelfHostedPrefs.load(context).port`.

- **`DashboardService.kt`** — `Service` foreground `dataSync` (Android 14+ `FOREGROUND_SERVICE_DATA_SYNC`).
  - `onStartCommand` → `createNotificationChannel` (`dashboard_channel_*`) + `startForeground(NOTIF_ID, NotificationCompat.Builder(...).setContentTitle(dashboard_notif_title).setContentText(dashboard_notif_active.format(address)).setSmallIcon(ic_trophy).addAction(stop))` + `DashboardServer.start()` (port walk: se `8066` em uso → tenta `8067`...).
  - `onDestroy` → `DashboardServer.stop()` (`engine.stop(2s, 5s)`) + `stopForeground(STOP_FOREGROUND_REMOVE)`.
  - `companion start(context)` / `stop(context)` (Intent).

- **`SelfHostedDashboardApi.kt`** — data layer (Kotlin puro, sem Ktor): `fun getDashboardData(): Map<String, Any?>` (counts por `GameSystem`, `recentlyAdded`, `coversMissing`), `fun getCollection(platforms: Boolean): Map` etc., usado pelas rotas.

- **`SelfHostedSessionStore.kt`** — `cookie swt_token=<token>` (não `Authorization: Bearer`), `SharedPreferences swt_dashboard_sess` (`putString(token, userJson)`), `fun createSession(username, role): String` (UUID), `fun validate(token): UserRole?`, `fun clear()`.

- **`SelfHostedSettings.kt`** — `data class SelfHostedSettings(port: Int=8066, users: List<User>)` + `SelfHostedPrefs.load/save(context)` (SharedPreferences `swt_dashboard`).

- **`UserRole.kt`** — `enum ADMIN/VISITOR`.

- **`NetworkUtils.kt`** — `fun getLocalIp(): String` (`NetworkInterface`).

- **`assets/dashboard/`** — `index.html` + `dashboard.js` + `style.css` (servidos via `staticResources("dashboard", "dashboard")`), Ktor `staticResources("dashboard", "dashboard")` + `default("index.html")`.

## Endpoints (routing DSL)

```kotlin
embeddedServer(CIO, port, host) {
  install(CORS) { anyHost(); allowHeader(HttpHeaders.Authorization) }
  install(StatusPages) { exception<Throwable> { call.respond(HttpStatusCode.InternalServerError) } }
  routing {
    // Public
    post("/api/login") { val (u,p)=call.receive<LoginReq>(); val token=sessionStore.create(...); call.response.cookies.append("swt_token", token, path="/", httpOnly=true); call.respond(mapOf("token" to token)) }
    get("/dashboard/{path...}") { call.respondBytes(assets) } // staticResources
    get("/favicon.ico") { ... }
    // Auth (cookie swt_token)
    authenticate("session") {
      get("/api/session") { call.respond(mapOf("user" to session.username)) }
      post("/api/logout") { sessionStore.clear(token); call.respond(mapOf("ok" to true)) }
      get("/api/dashboard") { call.respond(dashboardApi.getDashboardData()) }
      // Admin-only
      route("/api/collection") {
        get("/platforms") { call.respond(dashboardApi.getPlatforms()) }
        get("/roms") { call.respond(dashboardApi.getRoms()) }
        post("/rom/metadata") { val body=call.receive<Map>(); dashboardApi.setMetadata(body); call.respond(mapOf("ok" to true)) }
        get("/rom/download") { val key=call.parameters["key"]; call.respondFile(File(filesDir, "roms/$key")) }
        post("/gaming/upload") { val multipart=call.receiveMultipart(); /* FileItem → filesDir/roms/ */ }
        // covers/background em /api/gaming/rom/* (copiado do Kwiq, agora via Ktor multipart + Gson)
      }
      get("/api/settings") { call.respond(settings) }
      get("/api/files") { call.respond(File(filesDir, "roms").listFiles()?.map { it.name } ?: emptyList()) }
    }
  }
}.start(wait=false)
```

- **Auth:** plugin `SessionAuthPlugin` (cookie `swt_token` vs HylianBox `Bearer`), `onCall` intercept checa `call.request.cookies["swt_token"]` → `sessionStore.validate` → `401` se `null`, `call.attributes.put(ROLE_KEY, role)`.
- **Admin:** `route("/api/collection") { intercept { if (role != ADMIN) call.respond(HttpStatusCode.Forbidden) } }`.

## Notificação e Settings

- `SettingsActivity` toggle `dashboard_enabled` + `EditText port` (8066 default) + botão `dashboard_open` (`Intent ACTION_VIEW http://$ip:$port`). `DashboardService` inicia no `SwtApp.onCreate` se `enabled`.
- `AndroidManifest` `service android:foregroundServiceType="dataSync"` + `POST_NOTIFICATIONS` runtime (Android 13+).

## Diferença HylianBox vs Kwiq

- HylianBox Ktor já tinha `DashboardManager` singleton + `installContext` + `CIO` + cookie auth; Kwiq usava `NanoHTTPD` `serve()` monólito sem `CORS`/`StatusPages`. Swt portou HylianBox Ktor fielmente, mas manteve `SelfHostedDashboardApi` simples (sem `EmulatorJS`/`WebSocket` de HylianBox).
