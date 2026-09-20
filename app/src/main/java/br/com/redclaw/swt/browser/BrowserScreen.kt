package br.com.redclaw.swt.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import br.com.redclaw.swt.R

internal data class SearchSite(
    val name: String,
    val url: String,
) {
    fun searchUrl(query: String): String {
        val encoded = Uri.encode(query)
        return when (name) {
            "Google Images" -> "https://www.google.com/search?tbm=isch&q=$encoded"
            "The Games DB" -> "https://thegamesdb.net/search.php?name=$encoded"
            "SteamGridDB" -> "https://www.steamgriddb.com/search/grids?term=$encoded"
            "IGDB" -> "https://www.igdb.com/search?q=$encoded"
            else -> url
        }
    }
}

private val SITES = listOf(
    SearchSite("IGDB", "https://www.igdb.com"),
    SearchSite("The Games DB", "https://thegamesdb.net"),
    SearchSite("SteamGridDB", "https://www.steamgriddb.com"),
    SearchSite("Google Images", "https://www.google.com/imghp"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserScreen(
    initialSearchQuery: String? = null,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    val searchQuery = remember { mutableStateOf(initialSearchQuery.orEmpty()) }

    fun navigateTo(rawUrl: String) {
        val url = validateUrl(rawUrl)
        if (url.isBlank()) {
            android.widget.Toast.makeText(
                context, R.string.browser_choose_provider, android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        currentUrl = url
        isLoading = true
        webViewRef?.loadUrl(url)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        OutlinedTextField(
            value = currentUrl,
            onValueChange = { currentUrl = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(48.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.browser_url_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SITES.forEach { site ->
                FilterChip(
                    selected = false,
                    onClick = {
                        val q = searchQuery.value
                        navigateTo(if (q.isNotBlank()) site.searchUrl(q) else site.url)
                    },
                    label = { Text(site.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    createBrowserWebView(
                        context = ctx,
                        onUrlChanged = { url -> currentUrl = url },
                        onProgressChanged = { progress -> isLoading = progress < 100 },
                        onImageLongPress = { url -> confirmAndDownload(ctx, url, scope) },
                    ).also { webViewRef = it }
                },
                update = { webView -> webViewRef = webView },
                modifier = Modifier.fillMaxSize(),
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
