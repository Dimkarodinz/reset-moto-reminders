package dev.resetlight.adapter.elm

import dev.resetlight.transport.ByteTransport
import java.io.IOException
import java.util.ArrayDeque
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

enum class CommandIntent { READ, WRITE }

sealed class CommandFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Timeout(val command: String, cause: Throwable) : CommandFailure("Timed out waiting for ELM prompt: $command", cause)
    class Disconnected(val command: String) : CommandFailure("Transport disconnected: $command")
    class Io(val command: String, cause: Throwable) : CommandFailure("Transport I/O failed: $command", cause)
    class AmbiguousWrite(val command: String, cause: Throwable? = null) :
        CommandFailure("Write result is ambiguous; do not retry: $command", cause)
}

class ElmCommandSession(
    private val transport: ByteTransport,
    private val commandTimeoutMillis: Long = 3_000,
    private val observer: (ElmTrafficEvent) -> Unit = {},
) {
    private val commandMutex = Mutex()
    private val assembler = PromptAssembler()
    private val readyFrames = ArrayDeque<ElmFrame>()

    suspend fun execute(command: String, intent: CommandIntent = CommandIntent.READ): ElmFrame =
        commandMutex.withLock {
            var sent = false
            try {
                val outbound = ElmCodec.encode(command)
                observer(ElmTrafficEvent(ElmDirection.OUTBOUND, outbound.copyOf(), command, complete = true))
                transport.write(outbound)
                sent = true
                val frame = withTimeout(commandTimeoutMillis) { nextFrame() }
                val normalized = frame.withoutEcho(command)
                observer(
                    ElmTrafficEvent(
                        ElmDirection.INBOUND,
                        frame.raw.copyOf(),
                        normalized.normalizedText,
                        complete = true,
                    ),
                )
                normalized
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                if (intent == CommandIntent.WRITE && sent) throw CommandFailure.AmbiguousWrite(command, timeout)
                throw CommandFailure.Timeout(command, timeout)
            } catch (failure: IOException) {
                if (intent == CommandIntent.WRITE && sent) throw CommandFailure.AmbiguousWrite(command, failure)
                throw CommandFailure.Io(command, failure)
            } catch (failure: CommandFailure.Disconnected) {
                if (intent == CommandIntent.WRITE && sent) throw CommandFailure.AmbiguousWrite(command, failure)
                throw failure
            } catch (failure: CommandFailure) {
                throw failure
            }
        }

    private suspend fun nextFrame(): ElmFrame {
        readyFrames.pollFirst()?.let { return it }
        while (true) {
            val chunk = transport.read() ?: throw CommandFailure.Disconnected("active command")
            val frames = assembler.append(chunk)
            if (frames.isNotEmpty()) {
                frames.drop(1).forEach(readyFrames::addLast)
                return frames.first()
            }
        }
    }

    private fun ElmFrame.withoutEcho(command: String): ElmFrame {
        val lines = normalizedText.lines().toMutableList()
        if (lines.firstOrNull()?.equals(command.trim(), ignoreCase = true) == true) lines.removeAt(0)
        return copy(normalizedText = lines.joinToString("\n"))
    }
}

enum class ElmDirection { OUTBOUND, INBOUND }

data class ElmTrafficEvent(
    val direction: ElmDirection,
    val raw: ByteArray,
    val normalizedText: String,
    val complete: Boolean,
)
