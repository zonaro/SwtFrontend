package br.com.redclaw.swt

import android.app.Application
import br.com.redclaw.swt.theme.ThemeManager

/**
 * Swt Frontend - Launcher Android for RetroArch/libretro cores.
 *
 * Service locator central (pattern HylianBox): provides app-wide dependencies
 * manually (no Dagger/Hilt). ThemeManager is applied at startup to set the
 * persisted DayNight mode before any Activity inflates views.
 */
class SwtApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        ThemeManager.applyAtStartup(this)
    }

    companion object {
        lateinit var instance: SwtApp
            private set
    }
}