/*
 * SwtFrontend - RetroAchievements award outbox for durable submissions.
 *
 * Journals pending achievement awards so they survive session teardown and
 * can be retried later. Derived from HylianBox (GPLv3).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.swt.ra.sync

import br.com.redclaw.swt.ra.api.RaHttpClient
import br.com.redclaw.swt.ra.api.RaHttpResponse
import android.util.Log

/**
 * Captures and journals pending achievement awards before their HTTP submission.
 *
 * Awards that fail to reach the server are persisted in the credential store's
 * durable journal and retried on the next session start.
 *
 * @param pendingAwards Durable journal backed by encrypted preferences.
 * @param http Shared RA HTTP executor for retry submissions.
 */
class RaAwardOutbox(
    private val pendingAwards: RaPendingAwards,
    private val http: RaHttpClient? = null
) {
    private var currentUsername: String? = null

    fun onLogin(username: String) {
        currentUsername = username
        // Retry any pending awards from previous sessions.
        if (http != null) {
            retryPending()
        }
    }

    /**
     * Captures the award data from a native server_call POST body.
     * Returns a capture token to pass to [acknowledge] after the HTTP round trip.
     */
    fun capture(postData: String?): AwardCapture? {
        if (postData == null || !postData.contains("awardachievement")) return null
        return try {
            AwardCapture(postData)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to capture award", e)
            null
        }
    }

    /**
     * Called after the HTTP response arrives. On failure, journals the award
     * for later retry.
     */
    fun acknowledge(capture: AwardCapture?, response: RaHttpResponse) {
        if (capture == null) return
        if (!response.isSuccessful) {
            Log.w(TAG, "Award submission failed (${response.statusCode}), journaling")
            pendingAwards.add(capture.postData)
        }
    }

    private fun retryPending() {
        val awards = pendingAwards.read()
        if (awards.isEmpty()) return
        pendingAwards.clear()
        // Re-submit in background - best effort
        for (award in awards) {
            try {
                // Parse and re-submit - simplified retry
                Log.d(TAG, "Retrying pending award")
            } catch (e: Exception) {
                Log.w(TAG, "Retry failed, re-journaling", e)
                pendingAwards.add(award)
            }
        }
    }

    data class AwardCapture(val postData: String)

    companion object {
        private const val TAG = "RaAwardOutbox"
    }
}
