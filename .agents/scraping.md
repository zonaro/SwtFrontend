# Scraping + Browser — SwtFrontend

> **Capas automáticas** via 3 providers + **gestão local** + **browser interno** para captura manual. Tudo em `app/scraping/` + `app/browser/`.

## Clientes (Kwiq ports, OkHttp, sem Compose/Hilt)

- **`scraping/KwiqIgdbClient.kt`** — IGDB v4 (Twitch OAuth2 `client_credentials` → `access_token` cache, `client_id/secret` em `SwtPrefs` `igdb_client_*`, `libs.okhttp`, `libs.gson`, throttling `4 req/s` via `delay(250)` + `kotlinx.coroutines`).
  - `POST https://id.twitch.tv/oauth2/token` → token → `POST https://api.igdb.com/v4/games` (`fields name,cover.url,first_release_date; where name ~ "*term*"; limit 10;`) + `POST /v4/covers` (image id → `t_thumb`/`t_cover_big`, `https://images.igdb.com/igdb/image/...`).
  - Headers: `Client-ID`, `Authorization: Bearer <token>`.

- **`scraping/KwiqTheGamesDbClient.kt`** — TheGamesDB v1 (`https://api.thegamesdb.net/v1`, `apikey` em `SwtPrefs` `tgdb_api_key`, gratuita).
  - `GET /Games/ByGameName?apikey=&name=&include=boxart,platform&fields=players,publishers,genres,overview` → `data.games[]` + `include.boxart.data[gameId]` (`filename` → `https://cdn.thegamesdb.net/images/original/...`).
  - Sem throttling agressivo, mas `delay(500)` entre jogos.

- **`scraping/SteamGridDbClient.kt`** — **Novo** (Kwiq não tinha, criado para Swt). SteamGridDB `/api/v2` Bearer.
  - `GET https://www.steamgriddb.com/api/v2/search/autocomplete/{term}` (Auth `Bearer <key>` em `SwtPrefs` `sgdb_api_key`) → `data[].id` → `GET /api/v2/grids/game/{id}?dimensions=600x900&types=static` → `data[].url` (600x900, static).
  - Throttling `1 req/s` (`delay(1000)`), `User-Agent` `SwtFrontend/1.0.0`.
  - Spec salva em `/tmp/sgdb-openapi.yml` (limpo após Fase 1, re-obter via `curl https://www.steamgriddb.com/api/v2` se precisar).

- **Comum:** `ScrapingModels.kt` (`ScrapedGame`, `ScrapedCover`, `Provider` enum), `JsonHelpers.kt` (`JSONObject` helpers), `SwtPrefs.kt` (`igdb_client_id/secret`, `tgdb_api_key`, `sgdb_api_key` em `SharedPreferences` `swt_prefs`, nunca hardcoded), `SwtScrapingModels.kt` (mapeamento `Game` → `ScrapedCover`).

## GamingCovers (gestão local)

- **`scraping/GamingCovers.kt`** — pattern `KwiqGamingCovers`: `fun getCoverUri(game: Game): Uri?` = `localCoverUri(game) ?: remoteUrl(game)`.
  - `localCoverUri`: `File(filesDir, "covers/${game.id}.jpg")` ou `File(filesDir, "covers/${game.filename}.jpg")` → `toUri()` se `exists()`.
  - `remoteUrl`: `game.coverUrl` (de IGDB/TGB/SGDB, persistido via `RaInstallMetadataStore` ou `Game` DB `coverUrl` field).
  - `fun setLocalCover(game: Game, bitmap: Bitmap)` → `filesDir/covers/` + `covers/` mkdirs + `compress JPEG 90%` + notificação `MediaScanner`.
  - `fun deleteLocalCover(game: Game)` + `fun hasLocalCover`.
  - Usado por `LibraryActivity` (home row), `GameGridActivity` (Coil `load(coverUri)` + `placeholder(R.drawable.color_cover_background)`), `AchievementsActivity` (badge).

## InternalSimpleBrowser (WebView)

- **`browser/InternalSimpleBrowser.kt`** — port `Kwiq InternalSimpleBrowser` (WebView + site chips) para Views (`ScaledAppCompatActivity`, `activity_internal_simple_browser.xml`).
  - **Chips:** `ChipGroup` com 3 sites (IGDB `https://www.igdb.com`, TGB `https://thegamesdb.net`, SGDB `https://www.steamgriddb.com`) + `EditText` URL + `ImageButton go`.
  - **WebView:** `WebViewClient` (`onPageStarted/Finished` → `ProgressBar`, `shouldOverrideUrlLoading` → stay in WebView), `WebChromeClient` (`onProgressChanged`), `SslErrorHandler` (mostra `SwitchDialog` “Certificado inválido — Continuar?” → `handler.proceed()`), `setSupportMultipleWindows(false)`, `mixedContentMode`.
  - **Long-press imagem:** `setOnLongClickListener` + `HitTestResult.IMAGE_TYPE` → `Request` URL → `OkHttp` download → `GamingCovers.setLocalCover` → `filesDir/covers/` → `Toast` + `finish()` com `RESULT_OK` + `coverUri`.
  - **Disclaimer:** `TextView` inicial “Selecione um site abaixo…” (`browser_disclaimer_*`).
  - **Permissões:** `INTERNET` já no `AndroidManifest`, não precisa `WRITE_EXTERNAL_STORAGE` (SAF + `filesDir`).

## Fluxo de uso

1. `GameGridActivity` long-press jogo (sem capa) → `InternalSimpleBrowser` (`EXTRA_GAME_ID` + `EXTRA_GAME_TITLE` para busca).
2. Browser chip SGDB → `search/autocomplete/{title}` → grid SGDB → long-press capa → `GamingCovers`.
3. Ou `SettingsActivity` Providers preenche chaves → `GameGrid` auto-scrape em background (`WorkManager` + `KwiqIgdbClient` com `delay`).

## Chaves e throttling

- Chaves por usuário (nunca commit), `SwtPrefs` `SharedPreferences` `swt_prefs` (não `EncryptedSharedPreferences` como RA).
- `IGDB 4 req/s`, `SGDB 1 req/s`, `TGB ~2 req/s` — implementado via `kotlinx.coroutines.delay` + `OkHttp` `logging-interceptor` (`libs.okhttp.logging`).
