package br.com.redclaw.swt.module.api

/** Platform-neutral contract implemented by every SwtFrontend module. */
interface SwtModule {
    val descriptor: ModuleDescriptor

    suspend fun catalog(): List<ModuleGame>

    /** Dispatches a launch through the transport represented by [ModuleDescriptor.endpoint]. */
    suspend fun launch(request: LaunchRequest): LaunchResult

    fun tools(): List<ModuleTool> = emptyList()
}
