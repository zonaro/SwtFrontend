package br.com.redclaw.swt.module.api

/**
 * Stable Android package and action exposed by a separately installed companion APK.
 *
 * The API deliberately represents both values as strings so catalog and registry code remains
 * usable in plain Kotlin. The Android host is responsible for resolving the action explicitly
 * against [modulePackage] and for rejecting packages whose signing identity is not trusted.
 */
data class ModuleEndpoint(
    val modulePackage: String,
    val launchAction: String,
    val protocolVersion: Int = 1,
) {
    init {
        require(modulePackage.matches(PACKAGE_PATTERN)) { "Invalid module package: $modulePackage" }
        require(launchAction.matches(ACTION_PATTERN)) { "Invalid launch action: $launchAction" }
        require(protocolVersion > 0) { "Protocol version must be positive" }
    }

    companion object {
        private val PACKAGE_PATTERN = Regex("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+")
        private val ACTION_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    }
}
