package br.com.redclaw.swt.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStoreTest {
    @Test
    fun sourceId_keepsGlobalModuleGameIdentity() {
        assertEquals(
            "br.com.redclaw.hylianbox:oot_master_quest",
            CaptureStore.safeSourceId("br.com.redclaw.hylianbox:oot_master_quest"),
        )
    }

    @Test
    fun sourceId_removesPathAndControlCharacters() {
        val safe = CaptureStore.safeSourceId("../../module/game\nname")
        assertEquals("..-..-module-game-name", safe)
        assertTrue('/' !in safe)
    }

    @Test
    fun sourceId_hasStableUnknownFallback() {
        assertEquals("unknown", CaptureStore.safeSourceId("   "))
        assertEquals("unknown", CaptureStore.safeSourceId(null))
    }
}
