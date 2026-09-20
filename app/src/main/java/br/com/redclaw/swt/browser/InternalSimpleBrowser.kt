/*
 * Copyright (C) 2025 RedClaw Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package br.com.redclaw.swt.browser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.redclaw.swt.theme.AccentManager
import br.com.redclaw.swt.ui.theme.SwtTheme
import br.com.redclaw.swt.ui.theme.rememberSwtAccent

/**
 * Internal browser Activity for image search & download.
 * Ported from XML to Compose — pure [ComponentActivity] with [setContent].
 *
 * Usage:
 * ```kotlin
 * startActivity(InternalSimpleBrowser.createIntent(context, title = "Buscar Capa"))
 * ```
 */
class InternalSimpleBrowser : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AccentManager.getThemeOverlay(this))
        super.onCreate(savedInstanceState)

        val searchQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY)

        setContent {
            val accent = rememberSwtAccent()
            SwtTheme(accent = accent) {
                BrowserScreen(
                    initialSearchQuery = searchQuery,
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onBackPressed() {
        // WebView back navigation handled inside BrowserScreen's AndroidView
        super.onBackPressed()
    }

    companion object {
        private const val EXTRA_SEARCH_QUERY = "search_query"

        fun createIntent(context: Context, title: String? = null, searchQuery: String? = null): Intent {
            return Intent(context, InternalSimpleBrowser::class.java).apply {
                searchQuery?.let { putExtra(EXTRA_SEARCH_QUERY, it) }
            }
        }

        fun createIntent(context: Context, searchQuery: String): Intent {
            return Intent(context, InternalSimpleBrowser::class.java).apply {
                putExtra(EXTRA_SEARCH_QUERY, searchQuery)
            }
        }
    }
}
