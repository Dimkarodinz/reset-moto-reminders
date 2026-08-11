package dev.resetlight.transport.bluetooth

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RfcommByteTransportTest {
    private val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    @Test
    fun `cancels discovery before creating mapped uuid socket`() = runTest {
        val calls = mutableListOf<String>()
        val facade = FakeFacade(calls)
        RfcommByteTransport(facade, "synthetic-address", uuid).connect()
        assertEquals(listOf("cancelDiscovery", "create:synthetic-address:$uuid", "connect"), calls)
    }

    @Test
    fun `unbonded address fails as pairing required before opening a socket`() = runTest {
        val calls = mutableListOf<String>()
        val facade = FakeFacade(calls, bonded = false)
        assertThrows(DevicePairingRequiredException::class.java) {
            runBlocking { RfcommByteTransport(facade, "synthetic-address", uuid).connect() }
        }
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `connect failure closes socket exactly once`() = runTest {
        val calls = mutableListOf<String>()
        val facade = FakeFacade(calls, connectFailure = IOException("no link"))
        val transport = RfcommByteTransport(facade, "synthetic-address", uuid)
        try { transport.connect() } catch (_: IOException) { }
        transport.close()
        assertEquals(1, calls.count { it == "close" })
    }

    @Test
    fun `blocked connect times out by closing socket exactly once`() = runTest {
        val calls = mutableListOf<String>()
        val unblocked = CompletableDeferred<Unit>()
        val facade = object : BluetoothFacade {
            override fun bondedDevices(): Collection<BondedDevice> =
                listOf(BondedDevice("synthetic-address", "vLinker MC-Android"))
            override fun cancelDiscovery() { calls += "cancelDiscovery" }
            override fun createRfcommSocket(address: String, serviceUuid: UUID) = object : RfcommSocketConnection {
                override suspend fun connect() {
                    unblocked.await()
                    throw IOException("closed")
                }
                override suspend fun read(): ByteArray? = null
                override suspend fun write(bytes: ByteArray) = Unit
                override suspend fun close() {
                    calls += "close"
                    unblocked.complete(Unit)
                }
            }
        }

        try {
            RfcommByteTransport(facade, "synthetic-address", uuid, connectTimeoutMillis = 10).connect()
        } catch (_: SocketTimeoutException) {
            // Expected typed timeout.
        }
        assertEquals(1, calls.count { it == "close" })
    }

    private class FakeFacade(
        private val calls: MutableList<String>,
        private val connectFailure: IOException? = null,
        private val bonded: Boolean = true,
    ) : BluetoothFacade {
        override fun bondedDevices(): Collection<BondedDevice> =
            if (bonded) listOf(BondedDevice("synthetic-address", "vLinker MC-Android")) else emptyList()
        override fun cancelDiscovery() { calls += "cancelDiscovery" }
        override fun createRfcommSocket(address: String, serviceUuid: UUID): RfcommSocketConnection {
            calls += "create:$address:$serviceUuid"
            return object : RfcommSocketConnection {
                override suspend fun connect() { calls += "connect"; connectFailure?.let { throw it } }
                override suspend fun read(): ByteArray? = null
                override suspend fun write(bytes: ByteArray) = Unit
                override suspend fun close() { calls += "close" }
            }
        }
    }
}
