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

package br.com.redclaw.swt.views.gamegrid

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.redclaw.swt.R
data class GameEntry(
    val id: String,
    val title: String,
    val system: String,
    val coverUrl: String? = null,
    val localCoverUri: String? = null,
    val lastPlayedMillis: Long = 0L,
    val addedMillis: Long = 0L,
)
enum class SortMode(val prefValue: String) {
    ALPHA("alpha"),
    LAST_PLAYED("last_played"),
    RECENT("recent");

    companion object {
        fun fromPref(value: String?): SortMode =
            entries.find { it.prefValue == value } ?: ALPHA
    }
}
@Composable
fun SortDialog(
    currentMode: SortMode,
    onModeSelected: (SortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grid_sort_button)) },
        text = {
            Column {
                SortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode); onDismiss() },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                SortMode.ALPHA -> stringResource(R.string.grid_sort_alpha)
                                SortMode.LAST_PLAYED -> stringResource(R.string.grid_sort_last_played)
                                SortMode.RECENT -> stringResource(R.string.grid_sort_recent)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSort: () -> Unit,
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Pesquisar...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
        actions = {
            IconButton(onClick = onSort) {
                Icon(Icons.Default.Sort, contentDescription = "Ordenar")
            }
        },
    )
}
private fun loadGameEntries(): List<GameEntry> = emptyList()
@Composable
fun GameGridScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("swt_prefs", Context.MODE_PRIVATE)
    }

    var allEntries by remember { mutableStateOf(loadGameEntries()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember {
        mutableStateOf(
            SortMode.fromPref(
                prefs.getString("pref_grid_sort", SortMode.ALPHA.prefValue)
            )
        )
    }
    var showSortDialog by remember { mutableStateOf(false) }
    val filtered = remember(allEntries, searchQuery) {
        if (searchQuery.isBlank()) allEntries
        else allEntries.filter {
            it.title.lowercase().contains(searchQuery.lowercase().trim()) ||
                it.system.lowercase().contains(searchQuery.lowercase().trim())
        }
    }
    val sorted = remember(filtered, sortMode) {
        when (sortMode) {
            SortMode.ALPHA -> filtered.sortedBy { it.title.lowercase() }
            SortMode.LAST_PLAYED -> filtered.sortedByDescending { it.lastPlayedMillis }
            SortMode.RECENT -> filtered.sortedByDescending { it.addedMillis }
        }
    }

    val hasEntries = allEntries.isNotEmpty()
    val hasResults = sorted.isNotEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        GridTopBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onBack = onBack,
            onSort = { showSortDialog = true },
        )

        when {
            !hasEntries -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.grid_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            !hasResults -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.grid_no_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cardSizeDp = dimensionResource(R.dimen.switch_card_size_grid)
                val gapDp = dimensionResource(R.dimen.switch_grid_card_gap)
                val paddingDp = dimensionResource(R.dimen.switch_grid_padding)
                val usable = maxWidth - paddingDp * 2
                val spanCount = maxOf(
                    2,
                    ((usable + gapDp) / (cardSizeDp + gapDp)).toInt(),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(spanCount),
                    contentPadding = PaddingValues(paddingDp),
                    horizontalArrangement = Arrangement.spacedBy(gapDp),
                    verticalArrangement = Arrangement.spacedBy(gapDp),
                ) {
                    items(sorted, key = { it.id }) { entry ->
                        GameCard(
                            entry = entry,
                            onClick = { /* TODO: launch game via LibraryMenuHostDelegate */ },
                        )
                    }
                    items(spanCount) { GhostCard() }
                }
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            currentMode = sortMode,
            onModeSelected = { chosen ->
                sortMode = chosen
                prefs.edit()
                    .putString("pref_grid_sort", chosen.prefValue)
                    .apply()
                allEntries = loadGameEntries()
            },
            onDismiss = { showSortDialog = false },
        )
    }
}
