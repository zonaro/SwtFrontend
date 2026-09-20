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
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.swt.R
import br.com.redclaw.swt.theme.AccentManager
import br.com.redclaw.swt.ui.RetroArchIcons

/**
 * Adapter for the library cards grid below the home row: 4 cards
 * (Coleções, Todos os Jogos, Apps, Dashboard) shown in a 2×2 grid.
 *
 * Each card shows a monochrome icon tinted by the accent color and a
 * label. Cards are focusable and support D-pad navigation.
 */
class LibraryCardsAdapter(
    private val onCardClick: (LibraryCard) -> Unit
) : RecyclerView.Adapter<LibraryCardsAdapter.CardViewHolder>() {

    /**
     * Describes a single library card.
     * @param iconRes Monochrome drawable resource from RetroArch assets.
     * @param labelRes String resource for the card label.
     * @param id Unique identifier for the card.
     */
    data class LibraryCard(
        val id: String,
        @DrawableRes val iconRes: Int,
        val labelRes: Int
    )

    private val cards: List<LibraryCard> = listOf(
        LibraryCard("collections", R.drawable.ic_dock_collections, R.string.library_card_collections),
        LibraryCard("all_games", R.drawable.ic_dock_all_games, R.string.library_card_all_games),
        LibraryCard("apps", R.drawable.ic_dock_apps, R.string.library_card_apps),
        LibraryCard("dashboard", R.drawable.ic_dock_quickmenu, R.string.library_card_dashboard)
    )

    override fun getItemCount(): Int = cards.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position])
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.library_card_icon)
        private val label: TextView = itemView.findViewById(R.id.library_card_label)
        private val border: View = itemView.findViewById(R.id.library_card_border)

        init {
            border.background = AccentManager.createRoundFocusBorder(itemView.context)
            itemView.stateListAnimator = null
        }

        fun bind(card: LibraryCard) {
            val ctx = itemView.context
            label.text = ctx.getString(card.labelRes)
            RetroArchIcons.tintToAccent(icon, card.iconRes)
            itemView.setOnClickListener { onCardClick(card) }
            itemView.setOnFocusChangeListener { _, hasFocus ->
                border.visibility = if (hasFocus) View.VISIBLE else View.GONE
                // Brighten icon on focus
                icon.alpha = if (hasFocus) 1.0f else 0.85f
            }
            border.visibility = if (itemView.isFocused) View.VISIBLE else View.GONE
        }
    }
}
