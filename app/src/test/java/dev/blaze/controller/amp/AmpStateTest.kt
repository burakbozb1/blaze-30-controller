package dev.blaze.controller.amp

import dev.blaze.controller.protocol.AmpCommand.*
import dev.blaze.controller.protocol.AmpType
import dev.blaze.controller.protocol.GuitarEffect
import dev.blaze.controller.protocol.ReverbType
import org.junit.Assert.assertEquals
import org.junit.Test

class AmpStateTest {

    @Test
    fun setAmpTypeUpdatesOnlyAmpType() {
        val s = AmpState.DEFAULT.apply(SetAmpType(AmpType.METAL))
        assertEquals(AmpType.METAL, s.ampType)
        assertEquals(AmpState.DEFAULT.copy(ampType = AmpType.METAL), s)
    }

    @Test
    fun setEqBandUpdatesThatBandOnly() {
        val s = AmpState.DEFAULT.apply(SetEqBand(3, 5))
        assertEquals(5, s.eq[3])
        assertEquals(AmpState.DEFAULT.eq.filterIndexed { i, _ -> i != 3 }, s.eq.filterIndexed { i, _ -> i != 3 })
        assertEquals(7, s.eq.size)
    }

    @Test
    fun sequenceOfCommandsYieldsLastValue() {
        val s = listOf(
            SetAmpType(AmpType.METAL), SetAmpType(AmpType.CLEAN), SetAmpType(AmpType.DRIVE),
            SetEffect(GuitarEffect.WAH), SetEffect(GuitarEffect.BYPASS),
            SetReverbType(ReverbType.SPRING),
        ).fold(AmpState.DEFAULT) { acc, c -> acc.apply(c) }
        assertEquals(AmpType.DRIVE, s.ampType)
        assertEquals(GuitarEffect.BYPASS, s.effect)
        assertEquals(ReverbType.SPRING, s.reverbType)
    }

    @Test
    fun stateMirrorsWhatTheCodecWillSendSoValuesAreClamped() {
        assertEquals(30, AmpState.DEFAULT.apply(SetGain(999)).gain)
        assertEquals(0, AmpState.DEFAULT.apply(SetVolume(-1)).volume)
        assertEquals(20, AmpState.DEFAULT.apply(SetEqBand(0, 99)).eq[0])
        assertEquals(20, AmpState.DEFAULT.apply(SetDelayTime(99)).delayTime)
    }

    @Test
    fun everyCommandKindUpdatesItsField() {
        val s = AmpState.DEFAULT
            .apply(SetGain(7)).apply(SetVolume(8))
            .apply(SetReverbLevel(1)).apply(SetReverbTone(2)).apply(SetReverbTime(3))
            .apply(SetDelayLevel(4)).apply(SetDelayFeedback(5)).apply(SetDelayTime(6))
        assertEquals(listOf(7, 8, 1, 2, 3, 4, 5, 6),
            listOf(s.gain, s.volume, s.reverbLevel, s.reverbTone, s.reverbTime, s.delayLevel, s.delayFeedback, s.delayTime))
    }

    @Test
    fun masterDelayAndReverbAreTrackedSeparatelyFromTheEffectPageControls() {
        val s = AmpState.DEFAULT.apply(SetMasterDelay(12)).apply(SetMasterReverb(34)).apply(SetDelayLevel(5))
        assertEquals(12, s.masterDelay)
        assertEquals(34, s.masterReverb)
        assertEquals(5, s.delayLevel)
        assertEquals(50, AmpState.DEFAULT.apply(SetMasterDelay(999)).masterDelay)
    }

    @Test
    fun auxVolumeIsTrackedSeparatelyFromGuiVolumeAndClamped() {
        val s = AmpState.DEFAULT.apply(SetVolume(12)).apply(SetAuxVolume(30))
        assertEquals(12, s.volume)
        assertEquals(30, s.auxVolume)
        assertEquals(10, AmpState.DEFAULT.auxVolume)
        assertEquals(50, AmpState.DEFAULT.apply(SetAuxVolume(999)).auxVolume)
        assertEquals(0, AmpState.DEFAULT.apply(SetAuxVolume(-1)).auxVolume)
    }
}
