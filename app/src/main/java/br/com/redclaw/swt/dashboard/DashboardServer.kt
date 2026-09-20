/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.swt.dashboard

import android.content.Context
import android.util.Log
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.AttributeKey
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLDecoder
import java.time.Duration
import java.util.Collections
import java.util.Locale

/**
 * Embedded Ktor CIO server that powers the self-hosted dashboard.
 *
 * Migrated from the legacy self-hosted HTTP server to Ktor (HylianBox pattern).
 * Provides REST API for gaming/library management, static file serving,
 * and session-based authentication.
 *
 * Lifecycle is managed externally: call [start] to launch and [stop] to tear down.
 */
class DashboardServer(private val context: Context) {

    companion object {
        private const val TAG = "DashboardServer"
        private const val MAX_IMAGE_UPLOAD_BYTES = 32L * 1024L * 1024L

        val CONTEXT_KEY = AttributeKey<Context>("DashboardContext")
        val SERVER_KEY = AttributeKey<DashboardServer>("DashboardServer")
    }

    private val lock = Any()
    @Volatile private var engine: EmbeddedServer<*, *>? = null
    @Volatile private var state: State = State.STOPPED

    enum class State { STOPPED, STARTING, RUNNING }

    val isRunning: Boolean get() = state == State.RUNNING
    val isStarting: Boolean get() = state == State.STARTING

    @Volatile var address: String? = null
        private set

    var currentSettings: SelfHostedSettings = SelfHostedPrefs.load(context)
        set(value) { field = value; sessionCache.clear() }

    private val sessionCache: MutableMap<String, SelfHostedSessionStore.Session> =
        Collections.synchronizedMap(mutableMapOf())

    fun start(port: Int = SelfHostedPrefs.load(context).port) {
        synchronized(lock) {
            if (state != State.STOPPED) {
                Log.w(TAG, "Server already $state on $address")
                return
            }
            state = State.STARTING
            try {
                currentSettings = SelfHostedPrefs.load(context)
                val ctx = context.applicationContext
                val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                    attributes.put(CONTEXT_KEY, ctx)
                    attributes.put(SERVER_KEY, this@DashboardServer)
                    configureCors()
                    configureStatusPages()
                    configureRouting()
                }
                engine = server
                server.start(wait = false)
                address = resolveLanAddress() + ":$port"
                state = State.RUNNING
                Log.i(TAG, "Dashboard server started on $address")
            } catch (e: Exception) {
                engine = null
                address = null
                state = State.STOPPED
                throw e
            }
        }
    }

    fun stop() {
        val server = synchronized(lock) {
            val current = engine ?: return
            engine = null
            address = null
            state = State.STOPPED
            current
        }
        try {
            server.stop(Duration.ofSeconds(2).toMillis(), Duration.ofSeconds(5).toMillis())
            Log.i(TAG, "Dashboard server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping dashboard server", e)
        } finally {
            sessionCache.clear()
        }
    }

    // ─── Ktor configuration ────────────────────────────────────────────────

    private fun Application.configureCors() {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowHeader("X-Swt-Filename")
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowCredentials = true
            maxAgeInSeconds = 3600L
        }
    }

    private fun Application.configureStatusPages() {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "Unhandled exception in route", cause)
                call.respondText(
                    """{"error":"Internal server error"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.InternalServerError
                )
            }
        }
    }

    private fun Application.configureRouting() {
        routing {
            get("/") {
                val ctx = application.attributes[CONTEXT_KEY]
                serveAsset(ctx, call, "dashboard/index.html")
            }
            get("/favicon.ico") {
                val ctx = application.attributes[CONTEXT_KEY]
                serveAsset(ctx, call, "dashboard/favicon.svg")
            }
            get("/login") {
                val ctx = application.attributes[CONTEXT_KEY]
                serveAsset(ctx, call, "dashboard/login.html")
            }
            get("/dashboard/{path...}") {
                val ctx = application.attributes[CONTEXT_KEY]
                val path = call.parameters.getAll("path")?.joinToString("/") ?: return@get
                if (path.contains("..") || path.contains("\\")) {
                    call.respondText("""{"error":"Invalid path"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                    return@get
                }
                serveAsset(ctx, call, "dashboard/$path")
            }

            route("/api") {
                post("/login") { handleLogin(call) }

                get("/session") {
                    val role = authenticate(call)
                    if (role == null) {
                        call.respondText("""{"error":"Authentication required"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@get
                    }
                    val server = application.attributes[SERVER_KEY]
                    val username = server.resolveSession(call)?.username ?: ""
                    call.respondText(
                        org.json.JSONObject(mapOf(
                            "username" to username,
                            "role" to role.name,
                            "isAdmin" to (role == UserRole.ADMIN)
                        )).toString(),
                        ContentType.Application.Json
                    )
                }

                post("/logout") {
                    val role = authenticate(call)
                    if (role == null) {
                        call.respondText("""{"error":"Authentication required"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    handleLogout(call)
                }

                get("/dashboard") {
                    val role = authenticate(call)
                    if (role == null) {
                        call.respondText("""{"error":"Authentication required"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@get
                    }
                    val ctx = application.attributes[CONTEXT_KEY]
                    val server = application.attributes[SERVER_KEY]
                    val data = SelfHostedDashboardApi.dashboard(ctx, server.currentSettings, role == UserRole.ADMIN)
                    call.respondText(org.json.JSONObject(data).toString(), ContentType.Application.Json)
                }

                route("/gaming") {
                    get("/platforms") {
                        if (!requireAdmin(call)) return@get
                        call.respondText(org.json.JSONObject(SelfHostedDashboardApi.gamingPlatforms()).toString(), ContentType.Application.Json)
                    }

                    get("/roms") {
                        if (!requireAdmin(call)) return@get
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(org.json.JSONObject(SelfHostedDashboardApi.gamingRoms(ctx)).toString(), ContentType.Application.Json)
                    }

                    get("/rom") {
                        if (!requireAdmin(call)) return@get
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@get
                        }
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(org.json.JSONObject(SelfHostedDashboardApi.gamingRomDetail(ctx, key)).toString(), ContentType.Application.Json)
                    }

                    delete("/rom") {
                        if (!requireAdmin(call)) return@delete
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@delete
                        }
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(org.json.JSONObject(SelfHostedDashboardApi.gamingDeleteRom(ctx, key)).toString(), ContentType.Application.Json)
                    }

                    post("/rom/metadata") {
                        if (!requireAdmin(call)) return@post
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val body = org.json.JSONObject(call.receiveText())
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(org.json.JSONObject(SelfHostedDashboardApi.saveGamingRomMetadata(ctx, key, body)).toString(), ContentType.Application.Json)
                    }

                    get("/rom/download") {
                        if (!requireAdmin(call)) return@get
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@get
                        }
                        val ctx = application.attributes[CONTEXT_KEY]
                        val rom = SelfHostedDashboardApi.findGamingRom(ctx, key)
                        if (rom == null) {
                            call.respondText("""{"error":"ROM not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                            return@get
                        }
                        val file = File(key)
                        if (!file.exists()) {
                            call.respondText("""{"error":"File not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                            return@get
                        }
                        val filename = "${rom.name}.${rom.extension.ifBlank { "rom" }}".replace("\"", "")
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, filename).toString()
                        )
                        call.respondFile(file)
                    }

                    post("/rom/cover") {
                        if (!requireAdmin(call)) return@post
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val ctx = application.attributes[CONTEXT_KEY]
                        val bytes = readRawBody(call, MAX_IMAGE_UPLOAD_BYTES)
                        if (bytes == null) {
                            call.respondText("""{"error":"Invalid body"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val filename = runCatching {
                            URLDecoder.decode(call.request.headers["X-Swt-Filename"].orEmpty(), "UTF-8")
                        }.getOrDefault("cover.jpg")
                        call.respondText(
                            org.json.JSONObject(SelfHostedDashboardApi.gamingSaveCoverBytes(ctx, key, bytes, filename)).toString(),
                            ContentType.Application.Json
                        )
                    }

                    get("/rom/cover") {
                        if (!requireAdmin(call)) return@get
                        val key = call.parameters["key"]?.trim().orEmpty()
                        val ctx = application.attributes[CONTEXT_KEY]
                        val file = SelfHostedDashboardApi.coverFileFor(ctx, key)
                        if (file == null || !file.exists()) {
                            call.respondText("""{"error":"Not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                            return@get
                        }
                        call.respondFile(file)
                    }

                    delete("/rom/cover") {
                        if (!requireAdmin(call)) return@delete
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@delete
                        }
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(org.json.JSONObject(SelfHostedDashboardApi.gamingClearCover(ctx, key)).toString(), ContentType.Application.Json)
                    }

                    post("/rom/background") {
                        if (!requireAdmin(call)) return@post
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val ctx = application.attributes[CONTEXT_KEY]
                        val bytes = readRawBody(call, MAX_IMAGE_UPLOAD_BYTES)
                        if (bytes == null) {
                            call.respondText("""{"error":"Invalid body"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val filename = runCatching {
                            URLDecoder.decode(call.request.headers["X-Swt-Filename"].orEmpty(), "UTF-8")
                        }.getOrDefault("background.jpg")
                        call.respondText(
                            org.json.JSONObject(SelfHostedDashboardApi.gamingSaveBackgroundBytes(ctx, key, bytes, filename)).toString(),
                            ContentType.Application.Json
                        )
                    }

                    get("/rom/background") {
                        if (!requireAdmin(call)) return@get
                        val key = call.parameters["key"]?.trim().orEmpty()
                        val ctx = application.attributes[CONTEXT_KEY]
                        val file = SelfHostedDashboardApi.backgroundFileFor(ctx, key)
                        if (file == null || !file.exists()) {
                            call.respondText("""{"error":"Not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                            return@get
                        }
                        call.respondFile(file)
                    }

                    delete("/rom/background") {
                        if (!requireAdmin(call)) return@delete
                        val key = call.parameters["key"]?.trim().orEmpty()
                        if (key.isBlank()) {
                            call.respondText("""{"error":"Missing key"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@delete
                        }
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(org.json.JSONObject(SelfHostedDashboardApi.gamingClearBackground(ctx, key)).toString(), ContentType.Application.Json)
                    }

                    post("/upload") {
                        if (!requireAdmin(call)) return@post
                        val ctx = application.attributes[CONTEXT_KEY]
                        handleRomUpload(call, ctx)
                    }

                    get("/search") {
                        if (!requireAdmin(call)) return@get
                        val source = call.parameters["source"].orEmpty()
                        val query = call.parameters["q"].orEmpty()
                        val platform = call.parameters["platform"]?.takeIf { it.isNotBlank() }
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(
                            org.json.JSONObject(SelfHostedDashboardApi.gamingSearch(ctx, source, query, platform)).toString(),
                            ContentType.Application.Json
                        )
                    }

                    post("/resolve") {
                        if (!requireAdmin(call)) return@post
                        val body = org.json.JSONObject(call.receiveText())
                        val filename = body.optString("filename").trim()
                        val ctx = application.attributes[CONTEXT_KEY]
                        call.respondText(
                            org.json.JSONObject(SelfHostedDashboardApi.gamingResolve(ctx, filename)).toString(),
                            ContentType.Application.Json
                        )
                    }
                }

                get("/settings") {
                    if (!requireAdmin(call)) return@get
                    val ctx = application.attributes[CONTEXT_KEY]
                    call.respondText(
                        org.json.JSONObject(SelfHostedDashboardApi.settingsInfo(ctx)).toString(),
                        ContentType.Application.Json
                    )
                }

                get("/files") {
                    if (!requireAdmin(call)) return@get
                    val ctx = application.attributes[CONTEXT_KEY]
                    val path = call.parameters["path"] ?: ctx.filesDir.absolutePath
                    call.respondText(
                        org.json.JSONObject(SelfHostedDashboardApi.listFiles(ctx, path)).toString(),
                        ContentType.Application.Json
                    )
                }
            }
        }
    }

    // ─── Auth helpers ──────────────────────────────────────────────────────

    fun authenticate(call: ApplicationCall): UserRole? =
        resolveSession(call)?.role

    private fun resolveSession(call: ApplicationCall): SelfHostedSessionStore.Session? {
        val token = parseCookieToken(call) ?: return null
        sessionCache[token]?.let { cached ->
            if (cached.expiresAt > System.currentTimeMillis()) return cached
            sessionCache.remove(token)
        }
        val stored = SelfHostedSessionStore.resolve(context, token) ?: return null
        if (!currentSettings.users.any { it.username == stored.username && it.role == stored.role }) {
            SelfHostedSessionStore.revoke(context, token)
            return null
        }
        sessionCache[stored.token] = stored
        return stored
    }

    private suspend fun requireAdmin(call: ApplicationCall): Boolean {
        val role = authenticate(call)
        if (role == null) {
            call.respondText("""{"error":"Authentication required"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return false
        }
        if (role != UserRole.ADMIN) {
            call.respondText("""{"error":"Admin access required"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
            return false
        }
        return true
    }

    private fun parseCookieToken(call: ApplicationCall): String? {
        val cookieHeader = call.request.headers["Cookie"] ?: return null
        for (part in cookieHeader.split(';').map { it.trim() }) {
            val eq = part.indexOf('=')
            if (eq > 0 && part.substring(0, eq).trim().equals("swt_token", ignoreCase = true)) {
                return part.substring(eq + 1).trim().ifEmpty { null }
            }
        }
        return null
    }

    // ─── Request handlers ──────────────────────────────────────────────────

    private suspend fun handleLogin(call: ApplicationCall) {
        val body = try {
            org.json.JSONObject(call.receiveText())
        } catch (e: Exception) {
            call.respondText("""{"error":"Invalid request"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
            return
        }
        val username = body.optString("username")
        val password = body.optString("password")
        val role = currentSettings.authenticate(username, password)
        if (role == null) {
            call.respondText("""{"error":"Invalid credentials"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
            return
        }
        val session = SelfHostedSessionStore.create(context, username, role)
        sessionCache[session.token] = session
        call.response.header(
            "Set-Cookie",
            "swt_token=${session.token}; Path=/; HttpOnly; SameSite=Lax; Max-Age=${SelfHostedSessionStore.SESSION_TTL_SECONDS}"
        )
        call.respondText(
            org.json.JSONObject(mapOf("success" to true, "role" to role.name)).toString(),
            ContentType.Application.Json
        )
    }

    private suspend fun handleLogout(call: ApplicationCall) {
        val token = parseCookieToken(call)
        if (token != null) {
            sessionCache.remove(token)
            SelfHostedSessionStore.revoke(context, token)
        }
        call.response.header("Set-Cookie", "swt_token=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0")
        call.respondText("""{"success":true}""", ContentType.Application.Json)
    }

    private suspend fun handleRomUpload(call: ApplicationCall, context: Context) {
        try {
            val multipart = call.receiveMultipart()
            var platformId: String? = null
            var originalName: String? = null
            var fileBytes: ByteArray? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        when (part.name) {
                            "platformId" -> platformId = part.value.trim().ifBlank { null }
                            "originalName" -> originalName = part.value
                        }
                    }
                    is PartData.FileItem -> {
                        fileBytes = part.streamProvider().use { it.readBytes() }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (originalName.isNullOrBlank()) {
                call.respondText("""{"error":"originalName required"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return
            }
            if (fileBytes == null) {
                call.respondText("""{"error":"No file in request"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return
            }

            val tempFile = File(context.cacheDir, "gaming_upload_tmp").apply { mkdirs() }
                .let { File(it, originalName!!) }
            tempFile.outputStream().use { it.write(fileBytes!!) }

            val result = SelfHostedDashboardApi.gamingUploadRom(context, platformId, originalName!!, tempFile.inputStream())
            tempFile.delete()
            call.respondText(org.json.JSONObject(result).toString(), ContentType.Application.Json)
        } catch (e: Exception) {
            call.respondText("""{"error":"${e.message}"}""", ContentType.Application.Json, HttpStatusCode.InternalServerError)
        }
    }

    // ─── Body reading ──────────────────────────────────────────────────────

    private suspend fun readRawBody(call: ApplicationCall, maxBytes: Long): ByteArray? {
        val length = call.request.headers["Content-Length"]?.toLongOrNull() ?: return null
        if (length !in 1..maxBytes) return null
        val channel = call.receiveChannel()
        return channel.readRemaining().readBytes()
    }

    // ─── Static asset serving ──────────────────────────────────────────────

    private suspend fun serveAsset(context: Context, call: ApplicationCall, assetPath: String) {
        try {
            val inputStream = context.assets.open(assetPath)
            val bytes = inputStream.readBytes()
            inputStream.close()
            val contentType = guessContentType(assetPath)
            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            call.respondBytes(bytes, contentType)
        } catch (e: Exception) {
            Log.d(TAG, "Asset not found: $assetPath")
            call.respondText("""{"error":"Not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
        }
    }

    // ─── LAN address resolution ────────────────────────────────────────────

    private fun resolveLanAddress(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.activeNetwork?.let { network ->
                cm.getLinkProperties(network)?.linkAddresses
                    ?.asSequence()
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress
                    ?.let { return it }
            }
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { it.isUp && !it.isLoopback && !it.isVirtual }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
                ?: "localhost"
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve LAN address", e)
            "localhost"
        }
    }

    private fun guessContentType(filename: String): ContentType {
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "html", "htm" -> ContentType.Text.Html.withCharset(Charsets.UTF_8)
            "css" -> ContentType.Text.CSS.withCharset(Charsets.UTF_8)
            "js" -> ContentType.Application.JavaScript.withCharset(Charsets.UTF_8)
            "json" -> ContentType.Application.Json.withCharset(Charsets.UTF_8)
            "png" -> ContentType.Image.PNG
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "gif" -> ContentType.Image.GIF
            "svg" -> ContentType.Image.SVG
            "webp" -> ContentType.parse("image/webp")
            "ico" -> ContentType.parse("image/x-icon")
            "woff" -> ContentType.parse("font/woff")
            "woff2" -> ContentType.parse("font/woff2")
            "ttf" -> ContentType.parse("font/ttf")
            "zip" -> ContentType.Application.Zip
            else -> ContentType.Application.OctetStream
        }
    }
}
