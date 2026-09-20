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
package br.com.redclaw.swt.scraping

/**
 * Shared data models for game metadata scraping (IGDB, TheGamesDB, SteamGridDB).
 *
 * Ported from Kwiq's KwiqCore.kt — adapted for SwtFrontend without Compose/Hilt.
 */

data class GameMetadata(
    val igdbId: Long? = null,
    val theGamesDbId: Long? = null,
    val name: String? = null,
    val summary: String? = null,
    val storyline: String? = null,
    val releaseDateMillis: Long? = null,
    val genres: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val rating: Double? = null,
    val aggregatedRating: Double? = null,
    val screenshots: List<String> = emptyList(),
    val coverUrl: String? = null,
    val localCoverUri: String? = null,
    val localBackgroundUri: String? = null,
    val websites: List<String> = emptyList(),
    val companies: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val playerPerspectives: List<String> = emptyList(),
    val themes: List<String> = emptyList()
)

data class IgdbSearchResult(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val releaseYear: Int?,
    val platforms: List<String>
)

/** A provider-neutral search result used by the manual metadata importer. */
data class GameDatabaseSearchResult(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val releaseYear: Int?,
    val platforms: List<String>
)

enum class CoverSearchSource {
    GoogleImages,
    TheGamesDb,
    SteamGridDb
}
