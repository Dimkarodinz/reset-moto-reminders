package dev.resetlight.adapter.elm

import dev.resetlight.profiles.AdapterProfileLoader
import dev.resetlight.transport.ReplayByteTransport
import dev.resetlight.transport.ReplayExchange
import dev.resetlight.transport.ReplayInbound
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdapterInitializerTest {
    @Test
    fun `replays map driven captured initialization across arbitrary fragments`() = runTest {
        val profile = AdapterProfileLoader().load(generatedProfile())
        val transport = ReplayByteTransport(
            listOf(
                exchange("ATWS", "AT", "WS\r\rELM327 v2.2\r", ">"),
                exchange("ATE0", "ATE0\rOK", "\r>"),
                exchange("ATL0", "OK\r>"),
                exchange("ATS0", "O", "K\r>"),
                exchange("STI", "STN1151 ", "v4.3.2\r>"),
                exchange("ATH1", "OK\r>"),
            ),
        )
        transport.connect()
        val stages = mutableListOf<InitializationStage>()
        val identity = AdapterInitializer(ElmCommandSession(transport)).initialize(profile) { stages += it }

        assertEquals("ELM327 v2.2", identity.elm)
        assertEquals("STN1151 v4.3.2", identity.stn)
        assertEquals(listOf(InitializationStage.IDENTIFYING, InitializationStage.INITIALIZING), stages)
        transport.assertConsumed()
    }

    @Test
    fun `identity mismatch stops before initialization commands`() = runTest {
        val profile = AdapterProfileLoader().load(generatedProfile())
        val transport = ReplayByteTransport(listOf(exchange("ATWS", "OTHER\r>")))
        transport.connect()

        assertThrows(AdapterIdentityMismatch::class.java) {
            kotlinx.coroutines.runBlocking {
                AdapterInitializer(ElmCommandSession(transport)).initialize(profile)
            }
        }
        transport.assertConsumed()
    }

    private fun exchange(command: String, vararg chunks: String) = ReplayExchange(
        ElmCodec.encode(command),
        chunks.map { ReplayInbound.Bytes(it.encodeToByteArray()) },
    )

    private fun generatedProfile(): ByteArray =
        File("build/generated/profileAssets/profiles/vlinker-mc-android.adaptermap.yaml").readBytes()
}
