/*
 * SwtFrontend - native Android launcher frontend for RetroArch/libretro cores.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.swt.views

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.redclaw.swt.R
import br.com.redclaw.swt.ui.theme.LocalSwtAccent

/**
 * Represents a single installed app with its metadata.
 * Immutable — the icon [Drawable] is converted to [ImageBitmap] at render time.
 */
data class AppItem(
    val packageName: String,
    val label: String,
    val category: String,
    val icon: Drawable?,
    val launchIntent: Intent?,
)

// ═══════════════════════════════════════════════════════════════════════
// Data helpers (same logic as original AppsActivity)
// ═══════════════════════════════════════════════════════════════════════

/** Queries [PackageManager] for all launchable apps and maps to [AppItem]. */
fun loadInstalledApps(context: android.content.Context): List<AppItem> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(
            mainIntent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
    }

    return resolveInfos
        .filter { it.activityInfo != null }
        .map { ri ->
            val ai = ri.activityInfo.applicationInfo
            val label = ri.loadLabel(pm).toString()
            val icon = try { ri.loadIcon(pm) } catch (_: Exception) { null }
            val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                categoryLabel(context, ai.category)
            } else {
                context.getString(R.string.app_category_other)
            }
            AppItem(
                packageName = ri.activityInfo.packageName,
                label = label,
                category = category,
                icon = icon,
                launchIntent = pm.getLaunchIntentForPackage(ri.activityInfo.packageName),
            )
        }
        .sortedBy { it.label.lowercase() }
}

/**
 * Maps [ApplicationInfo.category] int to a localized label.
 * Ported from Kwiq KwiqCore.categoryLabel().
 */
private fun categoryLabel(context: android.content.Context, category: Int): String =
    when (category) {
        ApplicationInfo.CATEGORY_AUDIO -> context.getString(R.string.app_category_audio)
        ApplicationInfo.CATEGORY_GAME -> context.getString(R.string.app_category_games)
        ApplicationInfo.CATEGORY_IMAGE -> context.getString(R.string.app_category_image)
        ApplicationInfo.CATEGORY_MAPS -> context.getString(R.string.app_category_maps)
        ApplicationInfo.CATEGORY_NEWS -> context.getString(R.string.app_category_news)
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> context.getString(R.string.app_category_productivity)
        ApplicationInfo.CATEGORY_SOCIAL -> context.getString(R.string.app_category_social)
        ApplicationInfo.CATEGORY_VIDEO -> context.getString(R.string.app_category_video)
        ApplicationInfo.CATEGORY_UNDEFINED -> context.getString(R.string.app_category_other)
        else -> context.getString(R.string.app_category_other)
    }

/** Returns the display order for app categories. */
private fun categoryOrder(context: android.content.Context): List<String> = listOf(
    context.getString(R.string.app_category_games),
    context.getString(R.string.app_category_social),
    context.getString(R.string.app_category_video),
    context.getString(R.string.app_category_audio),
    context.getString(R.string.app_category_image),
    context.getString(R.string.app_category_productivity),
    context.getString(R.string.app_category_news),
    context.getString(R.string.app_category_maps),
    context.getString(R.string.app_category_tools),
    context.getString(R.string.app_category_other),
    context.getString(R.string.app_category_unknown),
)

// ═══════════════════════════════════════════════════════════════════════
// Screen composable
// ═══════════════════════════════════════════════════════════════════════

/**
 * Displays installed Android apps grouped by category, Switch-style.
 *
 * Search is filtered via [derivedStateOf] (simple debounce: only recomputes
 * when the query or the app list actually change).
 * Categories are rendered with [LazyColumn] + `stickyHeader`.
 */
@Composable
fun AppsScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val accentColor = LocalSwtAccent.current

    var allApps by remember { mutableStateOf(emptyList<AppItem>()) }
    var searchQuery by remember { mutableStateOf("") }

    // ── Load & refresh on resume ──────────────────────────────────────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allApps = loadInstalledApps(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Initial load
        allApps = loadInstalledApps(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Filtered list via derivedStateOf (simple debounce) ────────────
    val filteredApps by remember {
        derivedStateOf {
            val query = searchQuery.lowercase().trim()
            if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter {
                    it.label.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query) ||
                        it.category.lowercase().contains(query)
                }
            }
        }
    }

    // ── Group by category in display order ────────────────────────────
    val groupedItems by remember {
        derivedStateOf {
            val grouped = filteredApps.groupBy { it.category }
            val order = categoryOrder(context)
            val items = mutableListOf<Pair<String, List<AppItem>>>()

            for (cat in order) {
                val catApps = grouped[cat]
                if (!catApps.isNullOrEmpty()) {
                    items.add(cat to catApps)
                }
            }
            // Catch any categories not in the predefined order.
            for ((cat, catApps) in grouped) {
                if (cat !in order && catApps.isNotEmpty()) {
                    items.add(cat to catApps)
                }
            }
            items
        }
    }

    // ── UI ────────────────────────────────────────────────────────────
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Header ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u2190", // left arrow
                style = MaterialTheme.typography.headlineMedium,
                color = accentColor,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(end = 12.dp),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // ── Search field ──────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = {
                Text(
                    text = "Buscar apps\u2026",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = accentColor,
            ),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── App list ──────────────────────────────────────────────────
        if (groupedItems.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.apps_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                groupedItems.forEach { (category, apps) ->
                    stickyHeader {
                        CategoryHeader(
                            category = category,
                            accentColor = accentColor,
                        )
                    }
                    items(
                        items = apps,
                        key = { it.packageName },
                    ) { app ->
                        AppRow(
                            app = app,
                            accentColor = accentColor,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Item composables
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun CategoryHeader(
    category: String,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = category.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = accentColor,
        )
    }
}

@Composable
private fun AppRow(
    app: AppItem,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current

    // Convert Drawable → ImageBitmap, cached per package name.
    val iconBitmap: ImageBitmap? = remember(app.packageName, app.icon) {
        app.icon?.toBitmap()?.asImageBitmap()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                app.launchIntent?.let { intent ->
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // App cannot be launched (no activity).
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = app.label,
                modifier = Modifier.size(40.dp),
            )
        } else {
            Image(
                painter = painterResource(android.R.drawable.sym_def_app_icon),
                contentDescription = app.label,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // App info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
