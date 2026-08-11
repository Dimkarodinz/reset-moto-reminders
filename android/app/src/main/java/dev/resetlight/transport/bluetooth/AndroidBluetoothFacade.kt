package dev.resetlight.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class AndroidBluetoothFacade(context: Context) : BluetoothFacade {
    private val adapter: BluetoothAdapter =
        checkNotNull(context.getSystemService(BluetoothManager::class.java).adapter) { "Bluetooth is unavailable" }

    override fun bondedDevices(): Collection<BondedDevice> = adapter.bondedDevices.map { device ->
        BondedDevice(device.address, device.name)
    }

    override fun cancelDiscovery() {
        if (adapter.isDiscovering) adapter.cancelDiscovery()
    }

    override fun createRfcommSocket(address: String, serviceUuid: UUID): RfcommSocketConnection =
        AndroidRfcommSocket(adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(serviceUuid))
}

class AndroidRfcommSocket(
    private val socket: BluetoothSocket,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readBufferSize: Int = 1024,
) : RfcommSocketConnection {
    override suspend fun connect() = withContext(ioDispatcher) { socket.connect() }

    override suspend fun read(): ByteArray? = withContext(ioDispatcher) {
        val buffer = ByteArray(readBufferSize)
        val count = socket.inputStream.read(buffer)
        if (count < 0) null else buffer.copyOf(count)
    }

    override suspend fun write(bytes: ByteArray) = withContext(ioDispatcher) {
        socket.outputStream.write(bytes)
        socket.outputStream.flush()
    }

    override suspend fun close() = withContext(ioDispatcher) {
        try {
            socket.close()
        } catch (_: IOException) {
            // Closing is idempotent from the application perspective.
        }
    }
}
