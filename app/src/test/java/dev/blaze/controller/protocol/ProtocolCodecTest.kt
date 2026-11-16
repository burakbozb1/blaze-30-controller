package dev.blaze.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCodecTest {

    private fun hex(cmd: AmpCommand): String =
        ProtocolCodec.encode(cmd).joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    @Test
    fun ampTypeFramesMatchVerifiedProtocol() {
        assertEquals("FE0D02030000000000FF", hex(AmpCommand.SetAmpType(AmpType.CLEAN)))
        assertEquals("FE0D02030001000000FF", hex(AmpCommand.SetAmpType(AmpType.ACOUSTIC)))
        assertEquals("FE0D02030002000000FF", hex(AmpCommand.SetAmpType(AmpType.DRIVE)))
        assertEquals("FE0D02030003000000FF", hex(AmpCommand.SetAmpType(AmpType.DISTORTION)))
        assertEquals("FE0D02030004000000FF", hex(AmpCommand.SetAmpType(AmpType.METAL)))
    }

    @Test
    fun syncFrameMatchesTheVendorHandshake() {
        assertEquals("FE0DAB413442000000FF", hex(AmpCommand.Sync))
    }

    @Test
    fun gainAndVolumeFrames() {
        assertEquals("FE0D0203020F000000FF", hex(AmpCommand.SetGain(15)))
        assertEquals("FE0D0201000A000000FF", hex(AmpCommand.SetVolume(10)))
    }

    @Test
    fun outOfRangeValuesAreClampedNotSentRaw() {
        assertEquals("FE0D0203021E000000FF", hex(AmpCommand.SetGain(999)))   // max 30
        assertEquals("FE0D02030200000000FF", hex(AmpCommand.SetGain(-5)))    // min 0
        assertEquals("FE0D02010032000000FF", hex(AmpCommand.SetVolume(999))) // max 50
        assertEquals("FE0D02040114000000FF", hex(AmpCommand.SetEqBand(0, 99))) // max 20
    }

    @Test
    fun eqBandIndexIsZeroBasedAndMapsToWireBandOneToSeven() {
        assertEquals("FE0D0204010A000000FF", hex(AmpCommand.SetEqBand(0, 10)))
        assertEquals("FE0D0204070A000000FF", hex(AmpCommand.SetEqBand(6, 10)))
    }

    @Test
    fun eqBandIndexOutsideZeroToSixIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { AmpCommand.SetEqBand(7, 0) }
        assertThrows(IllegalArgumentException::class.java) { AmpCommand.SetEqBand(-1, 0) }
    }

    @Test
    fun effectFrames() {
        assertEquals("FE0D02050000000000FF", hex(AmpCommand.SetEffect(GuitarEffect.BYPASS)))
        assertEquals("FE0D02050002000000FF", hex(AmpCommand.SetEffect(GuitarEffect.CHORUS)))
        assertEquals("FE0D02050005000000FF", hex(AmpCommand.SetEffect(GuitarEffect.VIBRATO)))
    }

    @Test
    fun reverbAndDelayFrames() {
        assertEquals("FE0D05010002000000FF", hex(AmpCommand.SetReverbType(ReverbType.SPRING)))
        assertEquals("FE0D0502000A000000FF", hex(AmpCommand.SetReverbLevel(10)))
        assertEquals("FE0D0503000A000000FF", hex(AmpCommand.SetReverbTone(10)))
        assertEquals("FE0D0504000A000000FF", hex(AmpCommand.SetReverbTime(10)))
        assertEquals("FE0D0505000A000000FF", hex(AmpCommand.SetDelayLevel(10)))
        assertEquals("FE0D0506000A000000FF", hex(AmpCommand.SetDelayFeedback(10)))
        assertEquals("FE0D0507000A000000FF", hex(AmpCommand.SetDelayTime(10)))
    }

    @Test
    fun masterDelayAndReverbFramesAreTheGuitarTabEchoAndReverb() {
        assertEquals("FE0D0202000A000000FF", hex(AmpCommand.SetMasterDelay(10)))   // amp OLED: "GUI ECHO"
        assertEquals("FE0D0202010A000000FF", hex(AmpCommand.SetMasterReverb(10)))
        assertEquals("FE0D02020032000000FF", hex(AmpCommand.SetMasterDelay(999)))  // clamped to 50
        assertEquals("FE0D02020100000000FF", hex(AmpCommand.SetMasterReverb(-3)))  // clamped to 0
    }

    @Test
    fun everyFrameIsTenBytesWithHeaderAndTrailer() {
        val all = listOf(
            AmpCommand.SetAmpType(AmpType.METAL), AmpCommand.SetGain(3), AmpCommand.SetVolume(3),
            AmpCommand.SetEqBand(3, 3), AmpCommand.SetEffect(GuitarEffect.WAH),
            AmpCommand.SetReverbType(ReverbType.ROOM), AmpCommand.SetReverbLevel(3),
            AmpCommand.SetReverbTone(3), AmpCommand.SetReverbTime(3), AmpCommand.SetDelayLevel(3),
            AmpCommand.SetDelayFeedback(3), AmpCommand.SetDelayTime(3),
        )
        for (c in all) {
            val f = ProtocolCodec.encode(c)
            assertEquals(10, f.size)
            assertEquals(0xFE, f[0].toInt() and 0xFF)
            assertEquals(0x0D, f[1].toInt() and 0xFF)
            assertEquals(0xFF, f[9].toInt() and 0xFF)
        }
    }

    @Test
    fun noEncodableCommandIsTheFactoryReset() {
        // The vendor "restore factory mode" frame must be unreachable from the command set.
        val factoryReset = "FE0D01010000000000FF"
        val sample = listOf(
            AmpCommand.SetAmpType(AmpType.CLEAN), AmpCommand.SetVolume(0), AmpCommand.SetGain(0),
            AmpCommand.SetEqBand(0, 0), AmpCommand.SetEffect(GuitarEffect.BYPASS),
        )
        assertTrue(sample.none { hex(it) == factoryReset })
    }

    @Test
    fun auxVolumeFramesAreClampedAndNeverTheFactoryReset() {
        assertEquals("FE0D0401000A000000FF", hex(AmpCommand.SetAuxVolume(10)))
        assertEquals("FE0D04010032000000FF", hex(AmpCommand.SetAuxVolume(999)))  // max 50
        assertEquals("FE0D04010000000000FF", hex(AmpCommand.SetAuxVolume(-3)))   // min 0
        for (v in -5..60) assertTrue(hex(AmpCommand.SetAuxVolume(v)) != "FE0D01010000000000FF")
    }
}
