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

package br.com.redclaw.swt.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.swt.R
import br.com.redclaw.swt.theme.AccentManager
import java.io.File

/**
 * Adapter for the Switch-style home row: a horizontal list of landscape game
 * cards showing recently played entries, plus a trailing circular "Todos os
 * Jogos" card at the end.
 *
 * Entry data is currently mockable via [GameItem]. Real integration with
 * `retrograde-app-shared` [GameSystem]/[Game] types will happen in Phase 3;
 * the adapter is already designed to accept any [GameItem] list.
 */
class HomeGamesAdapter(
    private val onGameClick: (GameItem) -> Unit,
    private val onGameLongClick: (GameItem) -> Unit,
    private val onAllGamesClick: () -> Unit,
    private val onFocused: (GameItem?) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_GAME = 0
    private val VIEW_ALL_GAMES = 1

    private var items: List<GameItem> = emptyList()

    /** Placeholder data class for game entries. Replace with real types in Phase 3. */
    data class GameItem(
        val id: String,
        val title: String,
        val coverPath: String? = null,
        /** Optional system/author tag shown on the card. */
        val subtitle: String? = null
    )

    /** Replace the game list and refresh. */
    fun submitList(newItems: List<GameItem>) {
        items = newItems
        notifyDataSetChanged()
        if (items.isNotEmpty()) {
            onFocused(items.first())
        } else {
            onFocused(null)
        }
    }

    override fun getItemCount(): Int = items.size + 1 // +1 for "All Games" card

    override fun getItemViewType(position: Int): Int =
        if (position < items.size) VIEW_GAME else VIEW_ALL_GAMES

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val size = parent.resources.getDimensionPixelSize(R.dimen.switch_card_size_home)
        val gap = parent.resources.getDimensionPixelSize(R.dimen.switch_home_card_gap)
        // Height follows the landscape cover aspect ratio
        val cardH = (size * 0.56f).toInt() // ~16:9 landscape ratio

        if (viewType == VIEW_GAME) {
            val view = inflater.inflate(R.layout.item_home_game, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(size, cardH).apply {
                marginStart = gap / 2
                marginEnd = gap / 2
            }
            return GameViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_home_game, parent, false)
            // Circular "All Games" card: square, same height as game cards
            view.layoutParams = RecyclerView.LayoutParams(cardH, cardH).apply {
                marginStart = gap / 2
                marginEnd = gap / 2
            }
            return AllGamesViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GameViewHolder -> {
                val item = items[position]
                holder.bind(item)
                holder.itemView.setOnClickListener { onGameClick(item) }
                holder.itemView.setOnLongClickListener {
                    onGameLongClick(item)
                    true
                }
                holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onFocused(item)
                }
            }
            is AllGamesViewHolder -> {
                holder.bind()
                holder.itemView.setOnClickListener { onAllGamesClick() }
                holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) onFocused(null) // null signals "All Games" is focused
                }
            }
        }
    }

    /** ViewHolder for a single game card. */
    inner class GameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.home_game_cover)
        private val icon: ImageView = itemView.findViewById(R.id.home_game_icon)
        private val title: TextView = itemView.findViewById(R.id.home_game_title)
        private val border: View = itemView.findViewById(R.id.home_game_border)

        init {
            // Focus border from AccentManager
            border.background = AccentManager.createRoundFocusBorder(itemView.context)
            itemView.stateListAnimator = null // remove square elevation
        }

        fun bind(item: GameItem) {
            title.text = item.title
            if (item.coverPath != null) {
                val file = File(item.coverPath)
                if (file.exists()) {
                    cover.setImageBitmap(null) // will be loaded by Coil in Activity
                    cover.setImageURI(android.net.Uri.fromFile(file))
                    cover.visibility = View.VISIBLE
                    icon.visibility = View.GONE
                } else {
                    cover.visibility = View.GONE
                    icon.visibility = View.VISIBLE
                }
            } else {
                cover.visibility = View.GONE
                icon.visibility = View.VISIBLE
            }
            itemView.setOnFocusChangeListener { _, hasFocus ->
                border.visibility = if (hasFocus) View.VISIBLE else View.GONE
            }
            border.visibility = if (itemView.isFocused) View.VISIBLE else View.GONE
        }
    }

    /** ViewHolder for the trailing circular "Todos os Jogos" card. */
    inner class AllGamesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.home_game_cover)
        private val icon: ImageView = itemView.findViewById(R.id.home_game_icon)
        private val title: TextView = itemView.findViewById(R.id.home_game_title)
        private val border: View = itemView.findViewById(R.id.home_game_border)

        init {
            cover.visibility = View.GONE
            border.background = AccentManager.createRoundFocusBorder(itemView.context)
            itemView.stateListAnimator = null
        }

        fun bind() {
            val ctx = itemView.context
            title.text = ctx.getString(R.string.home_row_all_games)
            // Show the app icon tinted with accent as the "All Games" glyph
            icon.visibility = View.VISIBLE
            icon.setColorFilter(AccentManager.getAccentColor(ctx))
            // Round background for the "All Games" circle
            cover.visibility = View.GONE
            itemView.setOnFocusChangeListener { _, hasFocus ->
                border.visibility = if (hasFocus) View.VISIBLE else View.GONE
            }
            border.visibility = if (itemView.isFocused) View.VISIBLE else View.GONE
        }
    }

    companion object {
        /** Sentinel tag for the circular "Todos os Jogos" card. */
        val ALL_GAMES_TAG = Any()
    }
}
