package dev.blaze.controller.ui

import dev.blaze.controller.ble.FoundDevice
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.GuitarEffect
import dev.blaze.controller.protocol.ReverbType

/** Everything the screens can ask the amp to do. Implemented by [HomeViewModel]; faked in UI tests. */
interface AmpActions {
    fun onTone(type: AmpType)
    fun setGain(v: Int)
    fun setVolume(v: Int)
    fun setAuxVolume(v: Int)
    fun setEffect(effect: GuitarEffect)
    fun setEq(index: Int, level: Int)
    fun resetEq()
    fun setMasterDelay(v: Int)
    fun setDelayLevel(v: Int)
    fun setDelayFeedback(v: Int)
    fun setDelayTime(v: Int)
    fun toggleDelay()
    fun setMasterReverb(v: Int)
    fun setReverbType(type: ReverbType)
    fun setReverbLevel(v: Int)
    fun setReverbTone(v: Int)
    fun setReverbTime(v: Int)
    fun toggleReverb()
    fun retry()
    fun onDevicePicked(device: FoundDevice)
}

/** Things only the activity can do (permissions and system settings). */
data class SystemActions(
    val onGrantPermission: () -> Unit,
    val onOpenBluetoothSettings: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onRequestMic: () -> Unit,
    val onOpenUrl: (String) -> Unit = {},
)
