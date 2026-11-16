package dev.blaze.controller.protocol

import dev.blaze.controller.protocol.AmpCommand.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are frames captured from the real amp on 2026-11-15 (see docs/protocol.md). */
class ProtocolCodecDecodeTest {

    private fun frame(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** A live notification: FE 0F group item sub value 00.. FF (16 bytes). */
    private fun live(group: Int, item: Int, sub: Int, value: Int): ByteArray =
        ByteArray(16).also {
            it[0] = 0xFE.toByte(); it[1] = 0x0F
            it[2] = group.toByte(); it[3] = item.toByte(); it[4] = sub.toByte(); it[5] = value.toByte()
            it[15] = 0xFF.toByte()
        }

    private val bb = "FE0FBB0C0004190D0A0A0A0A0A0A0AFF"
    private val bc = "FE0FBC00141C1412180B1123140000FF"
    private val c0 = "FE0FC0000A080A0B0E100C640D000DFF"

    @Test
    fun decodesTheBBDump() {
        val cmds = ProtocolCodec.decode(frame(bb))
        val expected = listOf(SetVolume(12), SetAmpType(AmpType.METAL), SetGain(13)) +
            List(7) { SetEqBand(it, 10) }
        assertEquals(expected.size, cmds.size)
        assertTrue(cmds.containsAll(expected))
    }

    @Test
    fun decodesTheBCDump() {
        val cmds = ProtocolCodec.decode(frame(bc))
        val expected = listOf(SetEffect(GuitarEffect.BYPASS), SetMasterDelay(0), SetMasterReverb(0))
        assertEquals(expected.size, cmds.size)
        assertTrue(cmds.containsAll(expected))
    }

    @Test
    fun decodesTheC0Dump() {
        val cmds = ProtocolCodec.decode(frame(c0))
        val expected = listOf(
            SetReverbType(ReverbType.PLATE), SetReverbLevel(10), SetReverbTone(8), SetReverbTime(10),
            SetDelayLevel(11), SetDelayFeedback(14), SetDelayTime(16),
        )
        assertEquals(expected.size, cmds.size)
        assertTrue(cmds.containsAll(expected))
    }

    @Test
    fun decodesLiveKnobFrames() {
        assertEquals(listOf(SetVolume(12)), ProtocolCodec.decode(live(0x02, 0x01, 0x00, 12)))
        assertEquals(listOf(SetGain(14)), ProtocolCodec.decode(live(0x02, 0x03, 0x02, 14)))
        assertEquals(listOf(SetAmpType(AmpType.METAL)), ProtocolCodec.decode(live(0x02, 0x03, 0x00, 4)))
        assertEquals(listOf(SetMasterDelay(50)), ProtocolCodec.decode(live(0x02, 0x02, 0x00, 50)))
        assertEquals(listOf(SetMasterReverb(3)), ProtocolCodec.decode(live(0x02, 0x02, 0x01, 3)))
        assertEquals(listOf(SetEffect(GuitarEffect.CHORUS)), ProtocolCodec.decode(live(0x02, 0x05, 0x00, 2)))
        assertEquals(listOf(SetEqBand(2, 15)), ProtocolCodec.decode(live(0x02, 0x04, 0x03, 15)))
    }

    @Test
    fun ignoresHeartbeatOtherAuxItemsAndTheOtherDumps() {
        val ignored = listOf(
            frame("FE0F41000002000000000000000000FF"),            // heartbeat
            live(0x04, 0x01, 0x01, 0x05),                          // other aux items are still ignored
            live(0x04, 0x02, 0x00, 0x05),
            frame("FE0FBD1A143206140C0C0C0C0D0D0DFF"),
            frame("FE0FBE00000A0A0A0A0A0A0A000D0DFF"),
        )
        for (f in ignored) assertEquals(emptyList<AmpCommand>(), ProtocolCodec.decode(f))
    }

    @Test
    fun malformedFramesYieldNothing() {
        val good = frame(bb)
        val bad = listOf(
            good.copyOfRange(0, 15),                                 // too short
            good + byteArrayOf(0),                                   // too long
            ByteArray(0),                                            // empty
            good.also { }.copyOf().also { it[1] = 0x0D },            // a write-direction header
            good.copyOf().also { it[15] = 0x00 },                    // missing trailer
        )
        for (f in bad) assertEquals(emptyList<AmpCommand>(), ProtocolCodec.decode(f))
    }

    @Test
    fun unknownEnumCodeIsSkippedButTheRestApplies() {
        val cmds = ProtocolCodec.decode(frame("FE0FBB0C0009190D0A0A0A0A0A0A0AFF"))
        assertTrue(cmds.none { it is SetAmpType })
        assertTrue(cmds.contains(SetVolume(12)))
        assertTrue(cmds.contains(SetGain(13)))
    }

    @Test
    fun decodeNeverProducesSync() {
        val all = listOf(bb, bc, c0).map { frame(it) } + listOf(
            live(0x02, 0x01, 0x00, 1), live(0x02, 0x03, 0x00, 1), live(0x02, 0x04, 0x01, 1),
        )
        for (f in all) assertTrue(ProtocolCodec.decode(f).none { it == AmpCommand.Sync })
    }

    @Test
    fun decodesTheLiveAuxVolume() {
        // the master pot sends GUI VOL (02 01 00) and the aux volume (04 01 00) together
        assertEquals(listOf(SetAuxVolume(25)), ProtocolCodec.decode(live(0x04, 0x01, 0x00, 25)))
        assertEquals(listOf(SetAuxVolume(0)), ProtocolCodec.decode(live(0x04, 0x01, 0x00, 0)))
    }

    @Test
    fun decodesTheAuxVolumeFromTheBFDump() {
        // captured on the real amp: after the app set the aux volume to 16 (GUI VOL was 15 in the BB dump of that sync)
        assertEquals(listOf(SetAuxVolume(16)), ProtocolCodec.decode(frame("FE0FBF100001000A0A0A0A0A0A0A00FF")))
        assertEquals(listOf(SetAuxVolume(12)), ProtocolCodec.decode(frame("FE0FBF0C0001000A0A0A0A0A0A0A19FF")))
    }
}
