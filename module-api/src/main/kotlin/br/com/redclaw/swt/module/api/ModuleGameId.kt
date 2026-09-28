package br.com.redclaw.swt.module.api

/** Namespaced game identity shared by the frontend and companion APKs. */
data class ModuleGameId(
    val moduleId: String,
    val gameId: String,
) {
    init {
        require(moduleId.matches(ModuleDescriptor.ID_PATTERN)) { "Invalid module id: $moduleId" }
        require(gameId.matches(GAME_ID_PATTERN)) { "Invalid module-local game id: $gameId" }
    }

    override fun toString(): String = "$moduleId:$gameId"

    companion object {
        private val GAME_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

        fun parse(value: String): ModuleGameId {
            val separator = value.indexOf(':')
            require(separator > 0 && separator == value.lastIndexOf(':') && separator < value.lastIndex) {
                "Game id must use the moduleId:gameId format"
            }
            return ModuleGameId(value.substring(0, separator), value.substring(separator + 1))
        }
    }
}
