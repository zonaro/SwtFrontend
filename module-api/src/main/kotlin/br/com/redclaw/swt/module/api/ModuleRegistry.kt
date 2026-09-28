package br.com.redclaw.swt.module.api

/** Immutable registry used by the host to discover modules and their capabilities. */
class ModuleRegistry(modules: Iterable<SwtModule>) {
    private val modulesById: Map<String, SwtModule>

    init {
        val moduleList = modules.toList()
        val duplicateIds = moduleList
            .groupingBy { it.descriptor.id }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateIds.isEmpty()) { "Duplicate module ids: ${duplicateIds.sorted().joinToString()}" }
        modulesById = moduleList.associateBy { it.descriptor.id }
    }

    fun all(): List<SwtModule> = modulesById.values.sortedBy { it.descriptor.id }

    fun find(moduleId: String): SwtModule? = modulesById[moduleId]

    fun require(moduleId: String): SwtModule =
        find(moduleId) ?: throw NoSuchElementException("Unknown module: $moduleId")

    fun supporting(capability: ModuleCapability): List<SwtModule> =
        all().filter { capability in it.descriptor.capabilities }

    /** Returns a validated, deterministically ordered catalog from all registered modules. */
    suspend fun catalog(): List<ModuleGame> {
        val games = all().flatMap { module ->
            module.catalog().onEach { game ->
                require(game.moduleId == module.descriptor.id) {
                    "Game ${game.id} belongs to ${game.moduleId}, not ${module.descriptor.id}"
                }
                require(game.systemId in module.descriptor.supportedSystems) {
                    "Game ${game.id} uses unsupported system ${game.systemId}"
                }
            }
        }
        val duplicateKeys = games
            .groupingBy(ModuleGame::id)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateKeys.isEmpty()) {
            "Duplicate global game ids: ${duplicateKeys.sorted().joinToString()}"
        }
        return games.sortedWith(compareBy(ModuleGame::moduleId, ModuleGame::title, ModuleGame::id))
    }
}
