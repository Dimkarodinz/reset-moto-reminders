package dev.resetlight.transport.bluetooth

import dev.resetlight.transport.ByteTransport
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.net.SocketTimeoutException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface RfcommSocketConnection {
    suspend fun connect()
    suspend fun read(): ByteArray?
    suspend fun write(bytes: ByteArray)
    suspend fun close()
}

interface BluetoothFacade {
    fun bondedDevices(): Collection<BondedDevice>
    fun cancelDiscovery()
    fun createRfcommSocket(address: String, serviceUuid: UUID): RfcommSocketConnection
}

/**
 * Raised when a connection is attempted against an adapter that is no longer
 * bonded. Opening an RFCOMM socket to an unbonded device only surfaces a
 * generic [IOException], so the transport checks the bond list first and fails
 * with this typed error, letting the UI direct the user to Android's pairing
 * screen instead of showing a bare "connection failed".
 */
class DevicePairingRequiredException(address: String) :
    java.io.IOException("Adapter $address is not paired")

class RfcommByteTransport(
    private val facade: BluetoothFacade,
    private val address: String,
    private val serviceUuid: UUID,
    private val connectTimeoutMillis: Long = 12_000,
) : ByteTransport {
    private var socket: RfcommSocketConnection? = null
    private val closed = AtomicBoolean(false)

    override suspend fun connect(): Unit = coroutineScope {
        if (!BondedDeviceSelector.isBonded(facade.bondedDevices(), address)) {
            throw DevicePairingRequiredException(address)
        }
        facade.cancelDiscovery()
        val created = facade.createRfcommSocket(address, serviceUuid)
        socket = created
        val timeoutFailure = AtomicReference<SocketTimeoutException?>(null)
        val socketClosed = AtomicBoolean(false)
        suspend fun closeCreatedOnce() {
            if (socketClosed.compareAndSet(false, true)) created.close()
        }
        val timeout = launch {
            delay(connectTimeoutMillis)
            timeoutFailure.set(SocketTimeoutException("RFCOMM connection timed out"))
            closeCreatedOnce()
        }
        try {
            created.connect()
            timeoutFailure.get()?.let { throw it }
        } catch (failure: Throwable) {
            closeCreatedOnce()
            closed.set(true)
            throw timeoutFailure.get() ?: failure
        } finally {
            timeout.cancel()
        }
        Unit
    }

    override suspend fun write(bytes: ByteArray) = activeSocket().write(bytes)
    override suspend fun read(): ByteArray? = activeSocket().read()

    override suspend fun close() {
        if (closed.compareAndSet(false, true)) socket?.close()
    }

    private fun activeSocket(): RfcommSocketConnection = checkNotNull(socket) { "RFCOMM transport is not connected" }
}
