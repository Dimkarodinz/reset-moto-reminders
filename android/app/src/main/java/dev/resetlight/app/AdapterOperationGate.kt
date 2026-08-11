package dev.resetlight.app

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the whole-operation lease for one live adapter connection. The ELM
 * session already serializes individual commands; this gate prevents a second
 * multi-command feature from changing the CAN route between another feature's
 * prerequisite read and write.
 */
internal class AdapterOperationGate {
    private val held = AtomicBoolean(false)
    private val mutableInProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> = mutableInProgress.asStateFlow()

    fun tryAcquire(): Closeable? {
        if (!held.compareAndSet(false, true)) return null
        mutableInProgress.value = true
        val released = AtomicBoolean(false)
        return Closeable {
            if (released.compareAndSet(false, true)) {
                // Publish the idle UI state before making the gate acquirable.
                // Otherwise a new lease could set the flow true between these
                // two writes and then be overwritten by the old lease.
                mutableInProgress.value = false
                held.set(false)
            }
        }
    }
}
