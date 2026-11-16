package dev.blaze.controller.ui

import androidx.lifecycle.ViewModel
import dev.blaze.controller.amp.AmpRepository
import dev.blaze.controller.amp.AmpState
import dev.blaze.controller.ble.AmpTransport
import dev.blaze.controller.ble.ConnectionState
import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpCommand
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.GuitarEffect
import dev.blaze.controller.protocol.Ranges
import dev.blaze.controller.protocol.ReverbType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class HomeStatus { Starting, NeedsPermission, BluetoothOff, Scanning, NotFound, GaveUp, PickDevice, Connecting, Connected, Reconnecting, LinkLost }

data class HomeUiState(
    val status: HomeStatus,
    val devices: List<FoundDevice>,
    val amp: AmpState,
    /** True when Android will no longer show the permission dialog; the user has to use system settings. */
    val permissionNeedsSettings: Boolean = false,
) {
    val connected: Boolean get() = status == HomeStatus.Connected

    /** "On" = master level above zero (the amp has no separate on/off flag). */
    val delayOn: Boolean get() = amp.masterDelay > 0
    val reverbOn: Boolean get() = amp.masterReverb > 0
}

class HomeViewModel(
    private val repo: AmpRepository,
    private val transport: AmpTransport,
    private val scope: CoroutineScope,
    private val ownsScope: Boolean = false,
    private val scanWindowMs: Long = 4_000L,
    private val rescanDelayMs: Long = 3_000L,
    private val maxScanWindows: Int = 8,
) : ViewModel(), AmpActions {

    private sealed interface Phase {
        data object Idle : Phase
        data object NeedsPermission : Phase
        data object BluetoothOff : Phase
        data object Scanning : Phase
        data object NotFound : Phase
        data object GaveUp : Phase
        data class Pick(val devices: List<FoundDevice>) : Phase
        data object Linked : Phase
    }

    private val phase = MutableStateFlow<Phase>(Phase.Idle)
    private var permissionGranted: Boolean? = null
    private val needsSettings = MutableStateFlow(false)
    private var bluetoothEnabled: Boolean? = null
    private var scanJob: Job? = null
    private val delayPower = PowerMemory()
    private val reverbPower = PowerMemory()

    val ui: StateFlow<HomeUiState> = combine(phase, repo.state, repo.connection, repo.gaveUp, needsSettings) { p, amp, conn, gaveUp, settings ->
        val status = when (p) {
            Phase.Idle -> HomeStatus.Starting
            Phase.NeedsPermission -> HomeStatus.NeedsPermission
            Phase.BluetoothOff -> HomeStatus.BluetoothOff
            Phase.Scanning -> HomeStatus.Scanning
            Phase.NotFound -> HomeStatus.NotFound
            Phase.GaveUp -> HomeStatus.GaveUp
            is Phase.Pick -> HomeStatus.PickDevice
            Phase.Linked -> when (conn) {
                ConnectionState.Connected -> HomeStatus.Connected
                ConnectionState.Connecting -> HomeStatus.Connecting
                ConnectionState.Disconnected, is ConnectionState.Failed ->
                    if (gaveUp) HomeStatus.LinkLost else HomeStatus.Reconnecting
            }
        }
        HomeUiState(status, (p as? Phase.Pick)?.devices ?: emptyList(), amp, permissionNeedsSettings = settings && status == HomeStatus.NeedsPermission)
    }.stateIn(scope, SharingStarted.Eagerly, HomeUiState(HomeStatus.Starting, emptyList(), AmpState.DEFAULT))

    init {
        // Every level the amp state shows (also those set on the amp) feeds the power memories.
        scope.launch {
            repo.state.collect {
                delayPower.observe(it.masterDelay)
                reverbPower.observe(it.masterReverb)
            }
        }
    }

    fun onPermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
        permissionGranted = granted
        needsSettings.value = !granted && permanentlyDenied
        evaluate()
    }

    fun onBluetoothEnabled(enabled: Boolean) {
        bluetoothEnabled = enabled
        evaluate()
    }

    private fun evaluate() {
        when {
            permissionGranted == false -> stopAll(Phase.NeedsPermission)
            bluetoothEnabled == false -> stopAll(Phase.BluetoothOff)
            permissionGranted == true && bluetoothEnabled == true -> {
                val p = phase.value
                if (p == Phase.Idle || p == Phase.NeedsPermission || p == Phase.BluetoothOff) startScan()
            }
        }
    }

    /** Stop scanning and the repository's reconnect loop; the next scan starts when the blocker clears. */
    private fun stopAll(next: Phase) {
        scanJob?.cancel()
        repo.disconnect()
        phase.value = next
    }

    /** Manual retry after the app gave up: drop any stale link state, then look for the amp afresh. */
    override fun retry() {
        repo.disconnect()
        startScan()
    }

    /**
     * Scans in windows until the amp shows up. A single miss is normal (the amp may still be holding a stale
     * link and not advertising), so after the first empty window we show "not found" but keep looking, for a
     * bounded number of windows; then we stop and leave it to a manual retry.
     */
    private fun startScan() {
        scanJob?.cancel()
        phase.value = Phase.Scanning
        scanJob = scope.launch {
            var windows = 0
            while (true) {
                val found = scanOnce()
                windows++
                when (found.size) {
                    0 -> {
                        if (windows >= maxScanWindows) {
                            phase.value = Phase.GaveUp
                            return@launch
                        }
                        phase.value = Phase.NotFound
                        delay(rescanDelayMs)
                    }
                    1 -> { connectTo(found.first()); return@launch }
                    else -> { phase.value = Phase.Pick(found.toList()); return@launch }
                }
            }
        }
    }

    private suspend fun scanOnce(): Set<FoundDevice> {
        val found = LinkedHashSet<FoundDevice>()
        try {
            withTimeoutOrNull(scanWindowMs) { transport.scan().collect { found.add(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            found.clear() // scan unavailable (e.g. adapter went off) -> treated as not found, retried
        }
        return found
    }

    override fun onDevicePicked(device: FoundDevice) = connectTo(device)

    private fun connectTo(device: FoundDevice) {
        phase.value = Phase.Linked
        repo.connect(device)
    }

    /** The app came back to the foreground: re-read the amp so the screen matches it again. */
    fun onForeground() = repo.refresh()

    fun send(cmd: AmpCommand) = repo.send(cmd)
    override fun onTone(type: AmpType) = send(AmpCommand.SetAmpType(type))
    override fun setGain(value: Int) = send(AmpCommand.SetGain(value))
    override fun setVolume(value: Int) = send(AmpCommand.SetVolume(value))
    override fun setAuxVolume(value: Int) = send(AmpCommand.SetAuxVolume(value))
    override fun setEq(index: Int, level: Int) = send(AmpCommand.SetEqBand(index, level))
    override fun setMasterDelay(v: Int) = send(AmpCommand.SetMasterDelay(v))
    override fun setMasterReverb(v: Int) = send(AmpCommand.SetMasterReverb(v))
    override fun setEffect(effect: GuitarEffect) = send(AmpCommand.SetEffect(effect))
    override fun setReverbType(type: ReverbType) = send(AmpCommand.SetReverbType(type))
    override fun setReverbLevel(v: Int) = send(AmpCommand.SetReverbLevel(v))
    override fun setReverbTone(v: Int) = send(AmpCommand.SetReverbTone(v))
    override fun setReverbTime(v: Int) = send(AmpCommand.SetReverbTime(v))
    override fun setDelayLevel(v: Int) = send(AmpCommand.SetDelayLevel(v))
    override fun setDelayFeedback(v: Int) = send(AmpCommand.SetDelayFeedback(v))
    override fun setDelayTime(v: Int) = send(AmpCommand.SetDelayTime(v))

    /** Power buttons: off = master level 0 (previous level remembered), on = restore it. */
    override fun toggleDelay() = setMasterDelay(delayPower.levelAfterToggle(repo.state.value.masterDelay))
    override fun toggleReverb() = setMasterReverb(reverbPower.levelAfterToggle(repo.state.value.masterReverb))

    /** EQ reset: all seven bands back to the centre (10). */
    override fun resetEq() {
        for (band in 0 until Ranges.EQ_BANDS) send(AmpCommand.SetEqBand(band, 10))
    }

    override fun onCleared() {
        repo.disconnect()
        if (ownsScope) scope.cancel()
    }
}
