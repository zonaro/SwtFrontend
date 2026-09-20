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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import br.com.redclaw.swt.R
import br.com.redclaw.swt.theme.AccentManager

/**
 * Bottom dock of the Switch home screen: a centered row of circular buttons
 * (Jogos, Apps, Conquistas, Perfil, Config). Each button shows its destination
 * icon tinted by a fixed color on a dock-circle background, and gains a round
 * accent-colored focus border when focused.
 *
 * Based on HylianBox SwitchDock pattern. Simplified: no SFX manager dependency
 * (that will be added when the audio system is wired in Phase 3+).
 */
object DockAdapter {

    /**
     * A single dock destination.
     * @param iconRes Monochrome drawable from RetroArch assets.
     * @param iconColorRes Fixed color for this dock icon (accent for focus border only).
     * @param labelRes String resource for accessibility label.
     * @param action Callback when the button is activated.
     */
    data class DockItem(
        @DrawableRes val iconRes: Int,
        @ColorRes val iconColorRes: Int,
        val labelRes: Int,
        val action: () -> Unit
    )

    /**
     * Builds the dock inside [container] with the given [items].
     * Each item becomes a circular FrameLayout button matching the Switch dock style.
     */
    fun buildDock(context: Context, container: LinearLayout, items: List<DockItem>) {
        container.removeAllViews()
        val size = context.resources.getDimensionPixelSize(R.dimen.switch_dock_button_size)
        val gap = context.resources.getDimensionPixelSize(R.dimen.switch_dock_gap)

        for (item in items) {
            val button = DockButton(context)
            button.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = gap / 2
                marginEnd = gap / 2
            }
            button.bind(item)
            container.addView(button)
        }
    }

    /**
     * A single circular dock button, matching the HylianBox SwitchDock.DockButton pattern.
     * Not a RecyclerView — these are just 5 fixed FrameLayouts inflated from layout_dock_button.xml.
     */
    private class DockButton(context: Context) : FrameLayout(context) {
        private val icon: ImageView
        private val border: View
        private val glow: View

        init {
            LayoutInflater.from(context).inflate(R.layout.layout_dock_button, this, true)
            icon = findViewById(R.id.dock_icon)
            border = findViewById(R.id.dock_border)
            glow = findViewById(R.id.dock_glow)
            border.background = AccentManager.createRoundFocusBorder(context)
            // Kill the default square elevation/ripple feedback; the round glow
            // overlay is the only focus/press indication.
            stateListAnimator = null
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
        }

        fun bind(item: DockItem) {
            val ctx = context
            RetroArchIconsHelper.tintToAccent(icon, item.iconRes)
            // Override with the fixed dock color instead of accent
            val drawable = ContextCompat.getDrawable(ctx, item.iconRes)?.mutate()
            if (drawable != null) {
                androidx.core.graphics.drawable.DrawableCompat.setTint(
                    drawable, ContextCompat.getColor(ctx, item.iconColorRes)
                )
                icon.setImageDrawable(drawable)
            }
            icon.contentDescription = ctx.getString(item.labelRes)
            tag = item
            setOnClickListener {
                item.action()
            }
        }

        override fun onFocusChanged(
            gainFocus: Boolean,
            direction: Int,
            previouslyFocusedRect: android.graphics.Rect?
        ) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            border.visibility = if (gainFocus) View.VISIBLE else View.GONE
            icon.alpha = if (gainFocus) 1.0f else 0.85f
            glow.visibility = if (gainFocus || isPressed) View.VISIBLE else View.INVISIBLE
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            if (glow.visibility == View.VISIBLE && !isPressed && !isFocused) {
                glow.visibility = View.INVISIBLE
            } else if (isPressed) {
                glow.visibility = View.VISIBLE
            }
        }
    }

    /** Internal helper mirroring [br.com.redclaw.swt.ui.RetroArchIcons.tintToAccent]. */
    private object RetroArchIconsHelper {
        fun tintToAccent(imageView: ImageView, @DrawableRes resId: Int) {
            val context = imageView.context
            val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return
            androidx.core.graphics.drawable.DrawableCompat.setTint(
                drawable, AccentManager.getAccentColor(context)
            )
            imageView.setImageDrawable(drawable)
        }
    }
}
