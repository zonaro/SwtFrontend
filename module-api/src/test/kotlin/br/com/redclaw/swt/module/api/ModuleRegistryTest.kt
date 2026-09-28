package br.com.redclaw.swt.module.api

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ModuleRegistryTest {
    @Test
    fun rejectsDuplicateModuleIds() {
        val first = FakeModule(descriptor("zelda"))
        val second = FakeModule(descriptor("zelda"))

        assertThrows(IllegalArgumentException::class.java) {
            ModuleRegistry(listOf(first, second))
        }
    }

    @Test
    fun discoversModulesByIdAndCapability() {
        val catalog = FakeModule(descriptor("catalog", ModuleCapability.CATALOG))
        val emulator = FakeModule(descriptor("emulator", ModuleCapability.EMULATOR_CORE))
        val registry = ModuleRegistry(listOf(emulator, catalog))

        assertSame(catalog, registry.find("catalog"))
        assertEquals(listOf(emulator), registry.supporting(ModuleCapability.EMULATOR_CORE))
        assertEquals(listOf("catalog", "emulator"), registry.all().map { it.descriptor.id })
    }

    @Test
    fun catalogRejectsEntriesOwnedByAnotherModule() {
        val module = FakeModule(
            descriptor("zelda"),
            listOf(game(id = "other:oot")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runSuspend { ModuleRegistry(listOf(module)).catalog() }
        }
    }

    @Test
    fun catalogRejectsUnsupportedSystems() {
        val module = FakeModule(
            descriptor("zelda"),
            listOf(game(id = "zelda:oot", systemId = "snes")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            runSuspend { ModuleRegistry(listOf(module)).catalog() }
        }
    }

    @Test
    fun catalogIsDeterministicAndAllowsSameGameIdAcrossModules() {
        val second = FakeModule(descriptor("second"), listOf(game("second:shared", title = "Zelda B")))
        val first = FakeModule(descriptor("first"), listOf(game("first:shared", title = "Zelda A")))

        val result = runSuspend { ModuleRegistry(listOf(second, first)).catalog() }

        assertEquals(listOf("first", "second"), result.map { it.moduleId })
    }

    @Test
    fun globalGameIdRequiresNamespace() {
        assertThrows(IllegalArgumentException::class.java) {
            ModuleGame("oot", "Ocarina of Time", "n64")
        }
        assertEquals("zelda", ModuleGameId.parse("zelda:oot").moduleId)
        assertEquals("oot", ModuleGameId.parse("zelda:oot").gameId)
    }

    @Test
    fun endpointIdentifiesCompanionPackageAndRejectsFrontendPackage() {
        val endpoint = ModuleEndpoint(
            modulePackage = "br.com.redclaw.hylianbox",
            launchAction = "br.com.redclaw.hylianbox.action.LAUNCH_GAME",
        )

        assertEquals("br.com.redclaw.hylianbox", endpoint.modulePackage)
        assertThrows(IllegalArgumentException::class.java) {
            ModuleDescriptor(
                id = "frontend",
                version = "1",
                displayName = "Invalid",
                endpoint = ModuleEndpoint(
                    modulePackage = "br.com.redclaw.swt",
                    launchAction = "br.com.redclaw.swt.action.LAUNCH_GAME",
                ),
                capabilities = emptySet(),
                supportedSystems = emptySet(),
            )
        }
    }

    private fun descriptor(
        id: String,
        vararg capabilities: ModuleCapability,
    ) = ModuleDescriptor(
        id = id,
        version = "1.0.0",
        displayName = id,
        endpoint = ModuleEndpoint(
            modulePackage = "br.com.redclaw.$id",
            launchAction = "br.com.redclaw.$id.action.LAUNCH_GAME",
        ),
        capabilities = capabilities.toSet(),
        supportedSystems = setOf("n64"),
    )

    private fun game(
        id: String,
        systemId: String = "n64",
        title: String = id,
    ) = ModuleGame(id, title, systemId)

    private class FakeModule(
        override val descriptor: ModuleDescriptor,
        private val games: List<ModuleGame> = emptyList(),
    ) : SwtModule {
        override suspend fun catalog(): List<ModuleGame> = games

        override suspend fun launch(request: LaunchRequest): LaunchResult =
            LaunchResult(request.requestId, request.gameId, LaunchResult.Status.ACCEPTED)
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(value: Result<T>) {
                result = value
            }
        })
        return result!!.getOrThrow()
    }
}
