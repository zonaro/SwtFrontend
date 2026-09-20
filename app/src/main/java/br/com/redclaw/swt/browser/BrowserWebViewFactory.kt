package br.com.redclaw.swt.browser

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import br.com.redclaw.swt.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
internal val URL_SCHEME_REGEX = Regex("^https?://", RegexOption.IGNORE_CASE)

internal fun validateUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return ""
    return if (trimmed.contains(URL_SCHEME_REGEX)) trimmed else "https://$trimmed"
}

internal fun isImageExtension(url: String): Boolean {
    val ext = url.substringBefore('?').substringAfterLast(".", "")
        .lowercase(Locale.ROOT)
    return ext in IMAGE_EXTENSIONS
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createBrowserWebView(
    context: android.content.Context,
    onUrlChanged: (String) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onImageLongPress: (String) -> Unit,
): WebView {
    return WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { onUrlChanged(it) }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                val cert = error?.certificate
                val subject = cert?.issuedTo?.cName.orEmpty()
                val issuer = cert?.issuedBy?.cName.orEmpty()
                if (issuer.contains("Swt") || subject.contains("Swt") ||
                    issuer.contains("RedClaw") || subject.contains("RedClaw")
                ) {
                    handler?.proceed()
                } else {
                    handler?.cancel()
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgressChanged(newProgress)
            }
        }

        setOnLongClickListener {
            val hit = hitTestResult
            val imageUrl = hit?.extra?.takeIf {
                hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                    hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            }
            if (!imageUrl.isNullOrBlank() && imageUrl.startsWith("http")) {
                onImageLongPress(imageUrl)
                true
            } else false
        }

        setDownloadListener { url, _, _, _, _ ->
            if (isImageExtension(url)) {
                onImageLongPress(url)
            }
        }
    }
}

internal fun confirmAndDownload(
    context: android.content.Context,
    imageUrl: String,
    scope: CoroutineScope,
) {
    android.app.AlertDialog.Builder(context)
        .setMessage(R.string.browser_confirm_download)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            scope.launch {
                val success = withContext(Dispatchers.IO) { downloadImageToCovers(context, imageUrl) }
                val msg = if (success) R.string.browser_image_saved else R.string.browser_image_failed
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

private fun downloadImageToCovers(context: android.content.Context, imageUrl: String): Boolean {
    return try {
        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()
        val ext = imageUrl.substringBefore('?')
            .substringAfterLast('.', "jpg")
            .lowercase(Locale.US)
            .takeIf { it in IMAGE_EXTENSIONS } ?: "jpg"
        val filename = "cover_${System.currentTimeMillis()}.$ext"
        val outFile = File(coversDir, filename)
        val connection = URL(imageUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "SwtFrontend/1.0")
        try {
            connection.inputStream.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        true
    } catch (_: Exception) {
        false
    }
}
