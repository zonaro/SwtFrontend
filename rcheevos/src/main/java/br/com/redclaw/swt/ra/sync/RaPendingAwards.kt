/*
 * SwtFrontend - RetroAchievements durable award journal.
 *
 * Stores pending achievement awards in encrypted preferences so they survive
 * session teardown. Derived from HylianBox (GPLv3).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.swt.ra.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.security.GeneralSecurityException

/**
 * Encrypted durable award journal. Survives logout for account isolation.
 *
 * @param context Application context for opening encrypted preferences.
 */
class RaPendingAwards(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            throw RuntimeException("Unable to initialize RA award journal", e)
        }
    }

    /** Returns all pending award POST bodies. */
    fun read(): List<String> {
        val json = prefs.getString(KEY_AWARDS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Appends an award POST body to the journal. Synchronous commit. */
    fun add(postData: String) {
        val current = read().toMutableList()
        current.add(postData)
        val arr = JSONArray()
        current.forEach { arr.put(it) }
        check(prefs.edit().putString(KEY_AWARDS, arr.toString()).commit()) {
            "Unable to persist pending RetroAchievements award"
        }
    }

    /** Clears the journal (after successful retry). */
    fun clear() {
        prefs.edit().remove(KEY_AWARDS).apply()
    }

    companion object {
        private const val PREFS_FILE = "ra_secure_prefs"
        private const val KEY_AWARDS = "ra_pending_awards_v1"
    }
}
