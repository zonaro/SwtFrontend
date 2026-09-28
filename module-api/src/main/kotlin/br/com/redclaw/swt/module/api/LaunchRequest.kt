package br.com.redclaw.swt.module.api

/** Request sent by the frontend to a companion APK. URIs remain platform-neutral strings. */
data class LaunchRequest(
    val requestId: String,
    /** Global id in the form `moduleId:gameId`. */
    val gameId: String,
    val romUri: String,
    val settings: Map<String, String> = emptyMap(),
) {
    val moduleId: String
        get() = ModuleGameId.parse(gameId).moduleId

    init {
        require(requestId.isNotBlank()) { "Request id must not be blank" }
        ModuleGameId.parse(gameId)
        require(romUri.isNotBlank()) { "ROM URI must not be blank" }
        require(settings.keys.none(String::isBlank)) { "Setting keys must not be blank" }
    }
}
