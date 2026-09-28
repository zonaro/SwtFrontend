package br.com.redclaw.hylianbox.module

import br.com.redclaw.hylianbox.BuildConfig
import br.com.redclaw.swt.module.sdk.ModuleDescriptor
import br.com.redclaw.swt.module.sdk.ModuleService

/** Discovery endpoint used by SwtFrontend while HylianBox remains a companion APK. */
class HylianBoxModuleService : ModuleService() {
    override val moduleDescriptor = ModuleDescriptor(
        id = "br.com.redclaw.hylianbox",
        displayName = "HylianBox",
        versionName = BuildConfig.VERSION_NAME,
        capabilities = setOf(
            "catalog",
            "emulator_core",
            "ra_runtime",
            "tool.auto_ocarina",
            "tool.item_tracker",
            "touch_overlay.standard",
            "touch_overlay.pro",
        ),
    )
}
