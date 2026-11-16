package dev.blaze.controller.amp

import dev.blaze.controller.ble.AmpTransport
import dev.blaze.controller.ble.ConnectionState
import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpCommand
import dev.blaze.controller.protocol.ProtocolCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-facing amp controller: queues commands so they reach the amp in tap order, mirrors what the app sends and
 * what the amp reports into [state], and keeps the link alive. After a dropped link it reconnects and re-syncs from
 * the amp; it never pushes app values (or defaults) to the amp on its own.
 */
class AmpRepository(
    private val transport: AmpTransport,
    private val scope: CoroutineScope,
    private val reconnectDelayMs: Long = 1_000L,
    private val syncSettleMs: Long = 150L,
    private val maxReconnectAttempts: Int = 8,
    private val maxReconnectDelayMs: Long = 10_000L,
) {
    private val _state = MutableStateFlow(AmpState.DEFAULT)
    val state: StateFlow<AmpState> = _state.asStateFlow()
    val connection: StateFlow<ConnectionState> = transport.connection

    /** True once automatic reconnecting has been abandoned; the UI then offers a manual retry. */
    private val _gaveUp = MutableStateFlow(false)
    val gaveUp: StateFlow<Boolean> = _gaveUp.asStateFlow()

    private val queue = Channel<AmpCommand>(Channel.UNLIMITED)

    private var device: FoundDevice? = null
    private var wantConnected = false
    private var reconnectJob: Job? = null

    init {
        scope.launch {
            for (cmd in queue) {
                try {
                    deliver(cmd)
                } catch (e: Exception) {
                    // A write can end in a timeout or an aborted acknowledgement (both CancellationExceptions that
                    // are NOT this coroutine being cancelled). Drop that command, keep serving the queue; rethrow
                    // only if the scope itself was cancelled.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
        scope.launch {
            transport.connection.collect { onConnection(it) }
        }
        scope.launch {
            // Read-only mirror of what the amp reports; never written back.
            transport.incoming.collect { frame ->
                for (cmd in ProtocolCodec.decode(frame)) {
                    _state.value = _state.value.apply(cmd)
                }
            }
        }
    }

    fun connect(device: FoundDevice) {
        this.device = device
        wantConnected = true
        _gaveUp.value = false
        scope.launch { transport.connect(device) }
    }

    fun reconnect() {
        val d = device ?: return
        connect(d)
    }

    fun disconnect() {
        wantConnected = false
        reconnectJob?.cancel()
        transport.disconnect()
    }

    /** Asks the amp for a fresh full state dump (it answers the sync frame with one). No-op when not connected. */
    fun refresh() {
        if (transport.connection.value == ConnectionState.Connected) queue.trySend(AmpCommand.Sync)
    }

    fun send(cmd: AmpCommand) {
        queue.trySend(cmd)
    }

    private suspend fun deliver(cmd: AmpCommand) {
        if (transport.connection.value != ConnectionState.Connected) return
        try {
            transport.write(ProtocolCodec.encode(cmd))
        } catch (e: IllegalStateException) {
            return // link dropped between check and write; the connection flow handles recovery
        }
        if (cmd == AmpCommand.Sync) {
            delay(syncSettleMs) // give the amp a moment to open its session before the next frame
            return
        }
        _state.value = _state.value.apply(cmd)
    }

    private fun onConnection(s: ConnectionState) {
        when (s) {
            ConnectionState.Connected -> {
                reconnectJob?.cancel()
                _gaveUp.value = false
                // A new link always starts with the sync; the amp answers with its full state and that is the truth.
                // Nothing the app sent earlier is replayed: it could overwrite knob changes made on the amp meanwhile.
                queue.trySend(AmpCommand.Sync)
            }
            ConnectionState.Disconnected, is ConnectionState.Failed -> startReconnectLoop()
            ConnectionState.Connecting -> Unit
        }
    }

    /** Retries with growing pauses (base, x2, ... capped); after [maxReconnectAttempts] it stops and sets [gaveUp]. */
    private fun startReconnectLoop() {
        val d = device ?: return
        if (!wantConnected || _gaveUp.value || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var attempts = 0
            var pause = reconnectDelayMs
            while (wantConnected && transport.connection.value != ConnectionState.Connected) {
                if (attempts >= maxReconnectAttempts) {
                    _gaveUp.value = true
                    break
                }
                delay(pause)
                if (!wantConnected) break
                transport.connect(d)
                attempts++
                pause = minOf(pause * 2, maxReconnectDelayMs)
            }
        }
    }
}
