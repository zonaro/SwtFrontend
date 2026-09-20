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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.redclaw.swt.R
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Decorative ghost placeholder matching the Switch HOME menu trailing row. */
@Composable
fun GhostCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .alpha(0.12f)
            .clip(RoundedCornerShape(dimensionResource(R.dimen.switch_card_corner_radius)))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Game card with async cover art, title and system label. Uses Coil [AsyncImage]. */
@Composable
fun GameCard(
    entry: GameEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val cornerRadius = dimensionResource(R.dimen.switch_card_corner_radius)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val coverSource = entry.localCoverUri ?: entry.coverUrl
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverSource)
                .crossfade(true)
                .build(),
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.shape_game_card),
            error = painterResource(R.drawable.shape_game_card),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.system,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
