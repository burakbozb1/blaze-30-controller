package dev.blaze.controller.amp

import dev.blaze.controller.protocol.AmpCommand
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.GuitarEffect
import dev.blaze.controller.protocol.Ranges
import dev.blaze.controller.protocol.ReverbType

/**
 * Single source of truth for what the app believes the amp is set to.
 * Future presets are serialized AmpStates. Values mirror what the codec sends (clamped).
 */
data class AmpState(
    val ampType: AmpType,
    val gain: Int,
    val volume: Int,
    val auxVolume: Int,
    val eq: List<Int>,
    val effect: GuitarEffect,
    val masterDelay: Int,
    val masterReverb: Int,
    val reverbType: ReverbType,
    val reverbLevel: Int,
    val reverbTone: Int,
    val reverbTime: Int,
    val delayLevel: Int,
    val delayFeedback: Int,
    val delayTime: Int,
) {
    companion object {
        /** Neutral starting point before the amp's real state is known (EQ 10 = flat per vendor default). */
        val DEFAULT = AmpState(
            ampType = AmpType.CLEAN,
            gain = 15,
            volume = 10,
            auxVolume = 10,
            eq = List(Ranges.EQ_BANDS) { 10 },
            effect = GuitarEffect.BYPASS,
            masterDelay = 0,
            masterReverb = 0,
            reverbType = ReverbType.PLATE,
            reverbLevel = 0,
            reverbTone = 10,
            reverbTime = 10,
            delayLevel = 0,
            delayFeedback = 0,
            delayTime = 10,
        )
    }
}

fun AmpState.apply(cmd: AmpCommand): AmpState = when (cmd) {
    AmpCommand.Sync -> this
    is AmpCommand.SetAmpType -> copy(ampType = cmd.type)
    is AmpCommand.SetGain -> copy(gain = cmd.value.coerceIn(Ranges.GAIN))
    is AmpCommand.SetVolume -> copy(volume = cmd.value.coerceIn(Ranges.VOLUME))
    is AmpCommand.SetAuxVolume -> copy(auxVolume = cmd.value.coerceIn(Ranges.AUX_VOLUME))
    is AmpCommand.SetEqBand -> copy(
        eq = eq.toMutableList().also { it[cmd.index] = cmd.level.coerceIn(Ranges.EQ_LEVEL) },
    )
    is AmpCommand.SetEffect -> copy(effect = cmd.effect)
    is AmpCommand.SetMasterDelay -> copy(masterDelay = cmd.value.coerceIn(Ranges.MASTER_FX))
    is AmpCommand.SetMasterReverb -> copy(masterReverb = cmd.value.coerceIn(Ranges.MASTER_FX))
    is AmpCommand.SetReverbType -> copy(reverbType = cmd.type)
    is AmpCommand.SetReverbLevel -> copy(reverbLevel = cmd.value.coerceIn(Ranges.EFFECT_KNOB))
    is AmpCommand.SetReverbTone -> copy(reverbTone = cmd.value.coerceIn(Ranges.EFFECT_KNOB))
    is AmpCommand.SetReverbTime -> copy(reverbTime = cmd.value.coerceIn(Ranges.EFFECT_KNOB))
    is AmpCommand.SetDelayLevel -> copy(delayLevel = cmd.value.coerceIn(Ranges.EFFECT_KNOB))
    is AmpCommand.SetDelayFeedback -> copy(delayFeedback = cmd.value.coerceIn(Ranges.EFFECT_KNOB))
    is AmpCommand.SetDelayTime -> copy(delayTime = cmd.value.coerceIn(Ranges.EFFECT_KNOB))
}
