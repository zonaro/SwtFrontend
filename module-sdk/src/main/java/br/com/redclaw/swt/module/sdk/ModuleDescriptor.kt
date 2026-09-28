package br.com.redclaw.swt.module.sdk

import android.os.Bundle

/** Process-safe summary returned by a module before any feature-specific API is used. */
data class ModuleDescriptor(
    val id: String,
    val displayName: String,
    val versionName: String,
    val apiVersion: Int = ModuleContract.API_VERSION,
    val capabilities: Set<String> = emptySet(),
) {
    init {
        require(id.matches(ID_PATTERN)) { "Invalid module id: $id" }
        require(displayName.isNotBlank()) { "Module display name must not be blank" }
        require(versionName.isNotBlank()) { "Module version must not be blank" }
        require(apiVersion > 0) { "Module API version must be positive" }
        require(capabilities.none(String::isBlank)) { "Module capabilities must not be blank" }
    }

    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_ID, id)
        putString(KEY_DISPLAY_NAME, displayName)
        putString(KEY_VERSION_NAME, versionName)
        putInt(KEY_API_VERSION, apiVersion)
        putStringArrayList(KEY_CAPABILITIES, ArrayList(capabilities.sorted()))
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")

        private const val KEY_ID = "id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_VERSION_NAME = "version_name"
        private const val KEY_API_VERSION = "api_version"
        private const val KEY_CAPABILITIES = "capabilities"

        fun fromBundle(bundle: Bundle): ModuleDescriptor? {
            val id = bundle.getString(KEY_ID) ?: return null
            val displayName = bundle.getString(KEY_DISPLAY_NAME) ?: return null
            val versionName = bundle.getString(KEY_VERSION_NAME) ?: return null
            return runCatching {
                ModuleDescriptor(
                    id = id,
                    displayName = displayName,
                    versionName = versionName,
                    apiVersion = bundle.getInt(KEY_API_VERSION, ModuleContract.API_VERSION),
                    capabilities = bundle.getStringArrayList(KEY_CAPABILITIES)?.toSet().orEmpty(),
                )
            }.getOrNull()
        }
    }
}
