package dev.resetlight.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class ConnectionStateMachineTest {
    @Test
    fun `cannot jump from disconnected to ready`() {
        assertFailsWith<IllegalStateException> {
            ConnectionStateMachine.transition(ConnectionState.Disconnected, ConnectionState.AdapterReady("ELM", "STN", "map"))
        }
    }

    @Test
    fun `normal adapter flow reaches ready`() {
        var state: ConnectionState = ConnectionState.Disconnected
        listOf(
            ConnectionState.Connecting,
            ConnectionState.Identifying,
            ConnectionState.Initializing,
            ConnectionState.AdapterReady("ELM327 v2.2", "STN1151 v4.3.2", "vlinker-mc-android"),
        ).forEach { state = ConnectionStateMachine.transition(state, it) }
        assertEquals("vlinker-mc-android", (state as ConnectionState.AdapterReady).mapId)
    }
}
