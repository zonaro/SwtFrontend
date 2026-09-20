/*
 * SwtFrontend - native Android launcher frontend for RetroArch/libretro cores.
 * Copyright (C) 2026 RedClaw — GPLv3, see COPYING.
 */

package br.com.redclaw.swt.views.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.redclaw.swt.R
import br.com.redclaw.swt.ui.theme.LocalSwtAccent

// ── Static data ──────────────────────────────────────────────────────

private data class Card(val id: String, @DrawableRes val icon: Int, val label: Int)
private data class Dock(@DrawableRes val icon: Int, val tint: Color, val label: Int)

private val CARDS = listOf(
    Card("all_games", R.drawable.ic_dock_all_games, R.string.library_card_all_games),
    Card("apps", R.drawable.ic_dock_apps, R.string.library_card_apps),
    Card("collections", R.drawable.ic_dock_collections, R.string.library_card_collections),
    Card("dashboard", R.drawable.ic_dock_quickmenu, R.string.library_card_dashboard),
)

private val DOCKS = listOf(
    Dock(R.drawable.ic_dock_games, Color.White, R.string.dock_jogos),
    Dock(R.drawable.ic_dock_apps, Color.White, R.string.dock_apps),
    Dock(R.drawable.ic_dock_achievements, Color(0xFFFFA000), R.string.dock_conquistas),
    Dock(R.drawable.ic_dock_profile, Color.White, R.string.dock_perfil),
    Dock(R.drawable.ic_dock_settings, Color.White, R.string.dock_config),
)

// ── Main screen ──────────────────────────────────────────────────────

@Composable
fun LibraryScreen(
    onGameGrid: () -> Unit,
    onApps: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalSwtAccent.current
    var focused by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.height(96.dp))
        FocusedLabel(focused, accent)
        HomeRow(accent, onGameGrid) { focused = it }
        Spacer(Modifier.height(20.dp))
        CardsGrid(onGameGrid, onApps) { focused = it }
        Spacer(Modifier.weight(1f))
        DockRow(onApps, onSettings) { focused = it }
    }
}

// ── Focused label ────────────────────────────────────────────────────

@Composable
private fun FocusedLabel(text: String?, accent: Color) = Text(
    text = text ?: stringResource(R.string.library_focused_placeholder),
    color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 8.dp),
)

// ── Home row ─────────────────────────────────────────────────────────

@Composable
private fun HomeRow(accent: Color, onAllGames: () -> Unit, onFocus: (String?) -> Unit) =
    LazyRow(
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "all_games") {
            CircleCard(stringResource(R.string.home_row_all_games), accent, onAllGames) { onFocus(null) }
        }
    }

@Composable
private fun CircleCard(label: String, accent: Color, onClick: () -> Unit, onFocus: () -> Unit) =
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size((220 * 0.56f).dp).clip(CircleShape).focusable()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable { onClick() },
    ) {
        Text(label, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }

// ── Library cards grid (2×2) ────────────────────────────────────────

@Composable
private fun CardsGrid(onGameGrid: () -> Unit, onApps: () -> Unit, onFocus: (String?) -> Unit) =
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 22.dp),
    ) {
        CARDS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { card -> CardItem(card, onFocus, Modifier.weight(1f)) { navigate(card.id, onGameGrid, onApps) } }
            }
        }
    }

@Composable
private fun CardItem(card: Card, onFocus: (String?) -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = stringResource(card.label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(100.dp).clip(RoundedCornerShape(5.dp)).focusable()
            .onFocusChanged { if (it.isFocused) onFocus(label) }
            .clickable { onClick() },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(card.icon), contentDescription = null, tint = LocalSwtAccent.current, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
    }
}

private fun navigate(id: String, onGameGrid: () -> Unit, onApps: () -> Unit) {
    when (id) {
        "all_games" -> onGameGrid()
        "apps" -> onApps()
    }
}

// ── Dock row ─────────────────────────────────────────────────────────

@Composable
private fun DockRow(onApps: () -> Unit, onSettings: () -> Unit, onFocus: (String?) -> Unit) {
    val actions = listOf<() -> Unit>({ }, onApps, { }, { }, onSettings)
    val dockLabels = DOCKS.map { stringResource(it.label) }
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        DOCKS.forEachIndexed { i, dock ->
            val focusLabel = dockLabels[i]
            DockBtn(dock, actions[i]) { onFocus(focusLabel) }
            if (i < DOCKS.lastIndex) Spacer(Modifier.width(7.dp))
        }
    }
}

@Composable
private fun DockBtn(dock: Dock, onClick: () -> Unit, onFocus: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(50.dp).clip(CircleShape).focusable()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable { onClick() },
    ) {
        Icon(painterResource(dock.icon), contentDescription = null, tint = dock.tint, modifier = Modifier.size(28.dp))
    }
}
