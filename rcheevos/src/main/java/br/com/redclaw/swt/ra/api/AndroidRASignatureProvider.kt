/*
 * SwtFrontend - RetroAchievements MD5 signature provider.
 *
 * Computes the MD5(username + token) signature required by the
 * awardachievement dorequest.php endpoint.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.swt.ra.api

import java.security.MessageDigest

/**
 * Computes MD5 signatures for RetroAchievements API requests.
 *
 * The awardachievement endpoint requires an MD5 signature computed as:
 * `MD5(username + token)` where username and token are the raw strings
 * (not URL-encoded).
 */
object AndroidRASignatureProvider {

    /**
     * Computes the MD5 hex digest of [input].
     *
     * @return Lowercase 32-character hex string.
     */
    fun computeMd5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Computes the RetroAchievements award signature.
     *
     * @param username The authenticated username.
     * @param token The session token.
     * @return The MD5(username + token) hex string.
     */
    fun computeAwardSignature(username: String, token: String): String =
        computeMd5(username + token)
}
