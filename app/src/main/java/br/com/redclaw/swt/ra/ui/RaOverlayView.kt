package br.com.redclaw.swt.ra.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import br.com.redclaw.swt.R
import coil.load

/**
 * In-game achievement unlock toast. Shown by GameActivity when RaSessionManager
 * emits RaClientEvent (RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED).
 * Adapted from HylianBox RaOverlayView (GPLv3). Views puro, no Compose.
 */
class RaOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val badge: ImageView
    private val title: TextView
    private val desc: TextView
    private var hideRunnable: Runnable? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_ra_overlay, this, true)
        badge = findViewById(R.id.ra_overlay_badge)
        title = findViewById(R.id.ra_overlay_title)
        desc = findViewById(R.id.ra_overlay_desc)
        visibility = View.GONE
        alpha = 0f
        isClickable = false
        isFocusable = false
    }

    /**
     * Show an achievement unlock for [durationMs], then auto-hide with fade.
     * Safe to call from main thread only.
     */
    fun showUnlock(
        achievementTitle: String,
        achievementDesc: String?,
        badgeUrl: String?,
        durationMs: Long = 4000L,
    ) {
        hideRunnable?.let { removeCallbacks(it) }
        title.text = achievementTitle
        desc.text = achievementDesc ?: ""
        desc.visibility = if (achievementDesc.isNullOrBlank()) View.GONE else View.VISIBLE
        if (!badgeUrl.isNullOrBlank()) {
            badge.load(badgeUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_trophy)
                error(R.drawable.ic_trophy)
            }
        } else {
            badge.setImageResource(R.drawable.ic_trophy)
        }
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(220).start()
        val r = Runnable { hide() }
        hideRunnable = r
        postDelayed(r, durationMs)
    }

    /** Rich presence ticker (subtitle under HUD). */
    fun showPresence(presence: String) {
        if (presence.isBlank()) {
            if (visibility == View.VISIBLE) hide()
            return
        }
        title.text = presence
        desc.visibility = View.GONE
        badge.setImageResource(R.drawable.ic_trophy)
        showUnlock(presence, null, null, durationMs = 3000L)
    }

    private fun hide() {
        animate().alpha(0f).setDuration(220).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                visibility = View.GONE
                this@RaOverlayView.animate().setListener(null)
            }
        }).start()
    }
}
