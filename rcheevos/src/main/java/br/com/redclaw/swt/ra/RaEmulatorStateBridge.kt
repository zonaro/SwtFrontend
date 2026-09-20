/*
 * SwtFrontend - RetroAchievements emulator state bridge.
 *
 * Core-locked state and policy hooks shared by menus, hotkeys and direct
 * native callers. Derived from HylianBox (GPLv3).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.swt.ra

import android.os.Handler
import android.os.Looper
import android.util.Log
import br.com.redclaw.swt.ra.data.RaSaveState
import br.com.redclaw.swt.ra.data.RaSaveStateCodec
import br.com.redclaw.swt.ra.jni.RcheevosJni
import org.json.JSONObject

/**
 * Callback interface for policy notifications (hardcore blocks, etc.).
 * The app module implements this to show Toasts with localized strings.
 */
interface RaPolicyCallback {
    /** Called when a save state load is blocked by hardcore mode. */
    fun onHardcoreStateBlocked()
    /** Called when a pause is blocked by hardcore mode. */
    fun onHardcorePauseDisabled()
}

/**
 * Core-locked state and policy hooks shared by menus, hotkeys and direct
 * native callers. Does NOT depend on LibretroDroid or app resources.
 *
 * The caller (app module) wires save/load/reset callbacks to the emulator
 * and invokes [onFrame] after each emulated frame.
 */
class RaEmulatorStateBridge(
    private val policyCallback: RaPolicyCallback? = null
) {
    private val main = Handler(Looper.getMainLooper())

    /** Encodes achievement progress alongside the core save state. */
    fun onSave(coreState: ByteArray): ByteArray = RaSaveStateCodec.encode(
        RaSaveState(coreState, RcheevosJni.nativeSerializeProgress(), currentHash())
    )

    /** Decodes a save state; blocks in hardcore mode via [policyCallback]. */
    fun onDecode(savedState: ByteArray): ByteArray? {
        if (RcheevosJni.nativeIsHardcore()) {
            policyCallback?.onHardcoreStateBlocked()
            return null
        }
        return runCatching {
            val state = RaSaveStateCodec.decode(savedState)
            val hash = currentHash()
            require(state.hash.isBlank() || hash.isBlank() || state.hash == hash)
            state.core
        }.getOrNull()
    }

    /** Restores achievement progress from a decoded save state. */
    fun onLoaded(savedState: ByteArray) {
        val state = runCatching { RaSaveStateCodec.decode(savedState) }.getOrNull()
        val hash = currentHash()
        val progress = state?.progress?.takeIf { hash.isNotBlank() && state.hash == hash }
        RcheevosJni.nativeDeserializeProgress(progress)
    }

    /** Clears achievement progress after a core reset. */
    fun onReset() = RcheevosJni.nativeResetProgress()

    /** Returns true when cheats are allowed (not in hardcore mode). */
    fun allowCheat(): Boolean = !RcheevosJni.nativeIsHardcore()

    /** Enforces hardcore pause cooldown; disables hardcore if violated. */
    fun onPause() {
        if (!RcheevosJni.nativeCanPause()) {
            RcheevosJni.nativeSetHardcoreEnabled(false)
            policyCallback?.onHardcorePauseDisabled()
        }
    }

    private fun currentHash(): String = runCatching {
        JSONObject(RcheevosJni.nativeGetGameInfoJson()).optString("hash")
    }.getOrDefault("")

    companion object {
        private const val TAG = "RaEmulatorStateBridge"
    }
}
