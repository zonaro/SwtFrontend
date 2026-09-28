package br.com.redclaw.swt.module.api

/** Stable identity and advertised features of a frontend module. */
data class ModuleDescriptor(
    val id: String,
    val version: String,
    val displayName: String,
    val endpoint: ModuleEndpoint,
    val capabilities: Set<ModuleCapability>,
    val supportedSystems: Set<String>,
) {
    init {
        require(id.matches(ID_PATTERN)) { "Invalid module id: $id" }
        require(version.isNotBlank()) { "Module version must not be blank" }
        require(displayName.isNotBlank()) { "Module display name must not be blank" }
        require(endpoint.modulePackage != "br.com.redclaw.swt") {
            "A companion module must not use the frontend package"
        }
        require(supportedSystems.none(String::isBlank)) { "Supported system ids must not be blank" }
    }

    companion object {
        internal val ID_PATTERN = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
    }
}
