package br.com.redclaw.swt.module.api

/** Game or patch entry contributed to the frontend catalog by a module. */
data class ModuleGame(
    /** Global id in the form `moduleId:gameId`. */
    val id: String,
    val title: String,
    val systemId: String,
    val romIdentity: RomIdentity? = null,
    val coverUri: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    val moduleId: String
        get() = ModuleGameId.parse(id).moduleId

    val localId: String
        get() = ModuleGameId.parse(id).gameId

    init {
        ModuleGameId.parse(id)
        require(title.isNotBlank()) { "Game title must not be blank" }
        require(systemId.isNotBlank()) { "Game system id must not be blank" }
        require(metadata.keys.none(String::isBlank)) { "Metadata keys must not be blank" }
    }
}
