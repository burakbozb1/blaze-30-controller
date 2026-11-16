package dev.blaze.controller.protocol

/**
 * Encodes [AmpCommand]s into the amp's 10-byte frames: `FE 0D G I S V 00 00 00 FF`
 * (see docs/protocol.md). The factory-reset frame is intentionally not representable.
 */
object ProtocolCodec {

    fun encode(cmd: AmpCommand): ByteArray = when (cmd) {
        AmpCommand.Sync -> byteArrayOf(
            0xFE.toByte(), 0x0D, 0xAB.toByte(), 0x41, 0x34, 0x42, 0x00, 0x00, 0x00, 0xFF.toByte(),
        )
        is AmpCommand.SetAmpType -> frame(0x02, 0x03, 0x00, cmd.type.code)
        is AmpCommand.SetGain -> frame(0x02, 0x03, 0x02, cmd.value.coerceIn(Ranges.GAIN))
        is AmpCommand.SetVolume -> frame(0x02, 0x01, 0x00, cmd.value.coerceIn(Ranges.VOLUME))
        is AmpCommand.SetAuxVolume -> frame(0x04, 0x01, 0x00, cmd.value.coerceIn(Ranges.AUX_VOLUME))
        is AmpCommand.SetEqBand -> frame(0x02, 0x04, cmd.index + 1, cmd.level.coerceIn(Ranges.EQ_LEVEL))
        is AmpCommand.SetEffect -> frame(0x02, 0x05, 0x00, cmd.effect.code)
        is AmpCommand.SetMasterDelay -> frame(0x02, 0x02, 0x00, cmd.value.coerceIn(Ranges.MASTER_FX))
        is AmpCommand.SetMasterReverb -> frame(0x02, 0x02, 0x01, cmd.value.coerceIn(Ranges.MASTER_FX))
        is AmpCommand.SetReverbType -> frame(0x05, 0x01, 0x00, cmd.type.code)
        is AmpCommand.SetReverbLevel -> knob(0x02, cmd.value)
        is AmpCommand.SetReverbTone -> knob(0x03, cmd.value)
        is AmpCommand.SetReverbTime -> knob(0x04, cmd.value)
        is AmpCommand.SetDelayLevel -> knob(0x05, cmd.value)
        is AmpCommand.SetDelayFeedback -> knob(0x06, cmd.value)
        is AmpCommand.SetDelayTime -> knob(0x07, cmd.value)
    }

    // ---- amp -> phone ----------------------------------------------------------------------------------

    private const val FRAME_LEN = 16

    /**
     * Turns one 16-byte notification (`FE 0F ... FF`) into the commands that set [dev.blaze.controller.amp.AmpState]
     * to what the amp reports. Never throws; anything not understood (heartbeat, unused dumps and aux items,
     * malformed frames) yields an empty list. Field maps are in docs/protocol.md.
     */
    fun decode(frame: ByteArray): List<AmpCommand> {
        if (frame.size != FRAME_LEN) return emptyList()
        val b = IntArray(FRAME_LEN) { frame[it].toInt() and 0xFF }
        if (b[0] != 0xFE || b[1] != 0x0F || b[15] != 0xFF) return emptyList()
        return when (b[2]) {
            0xBB -> buildList {
                add(AmpCommand.SetVolume(b[3]))
                AmpType.entries.firstOrNull { it.code == b[5] }?.let { add(AmpCommand.SetAmpType(it)) }
                add(AmpCommand.SetGain(b[7]))
                for (band in 0 until Ranges.EQ_BANDS) add(AmpCommand.SetEqBand(band, b[8 + band]))
            }
            0xBC -> buildList {
                GuitarEffect.entries.firstOrNull { it.code == b[3] }?.let { add(AmpCommand.SetEffect(it)) }
                add(AmpCommand.SetMasterDelay(b[13]))
                add(AmpCommand.SetMasterReverb(b[14]))
            }
            0xC0 -> buildList {
                ReverbType.entries.firstOrNull { it.code == b[3] }?.let { add(AmpCommand.SetReverbType(it)) }
                add(AmpCommand.SetReverbLevel(b[4]))
                add(AmpCommand.SetReverbTone(b[5]))
                add(AmpCommand.SetReverbTime(b[6]))
                add(AmpCommand.SetDelayLevel(b[7]))
                add(AmpCommand.SetDelayFeedback(b[8]))
                add(AmpCommand.SetDelayTime(b[9]))
            }
            // BF dump: byte 3 is the aux (music) volume (verified on the amp: it followed the app's aux writes, GUI VOL did not)
            0xBF -> listOf(AmpCommand.SetAuxVolume(b[3]))
            0x02 -> decodeLiveGuitar(item = b[3], sub = b[4], value = b[5])
            // live aux (music) volume: FE 0F 04 01 00 VV ... (the master pot sends it together with GUI VOL)
            0x04 -> if (b[3] == 0x01 && b[4] == 0x00) listOf(AmpCommand.SetAuxVolume(b[5])) else emptyList()
            else -> emptyList()
        }
    }

    private fun decodeLiveGuitar(item: Int, sub: Int, value: Int): List<AmpCommand> = when {
        item == 0x01 && sub == 0x00 -> listOf(AmpCommand.SetVolume(value))
        item == 0x02 && sub == 0x00 -> listOf(AmpCommand.SetMasterDelay(value))
        item == 0x02 && sub == 0x01 -> listOf(AmpCommand.SetMasterReverb(value))
        item == 0x03 && sub == 0x00 ->
            AmpType.entries.firstOrNull { it.code == value }?.let { listOf(AmpCommand.SetAmpType(it)) } ?: emptyList()
        item == 0x03 && sub == 0x02 -> listOf(AmpCommand.SetGain(value))
        item == 0x04 && sub in 1..Ranges.EQ_BANDS -> listOf(AmpCommand.SetEqBand(sub - 1, value))
        item == 0x05 && sub == 0x00 ->
            GuitarEffect.entries.firstOrNull { it.code == value }?.let { listOf(AmpCommand.SetEffect(it)) } ?: emptyList()
        else -> emptyList()
    }

    private fun knob(item: Int, value: Int) = frame(0x05, item, 0x00, value.coerceIn(Ranges.EFFECT_KNOB))

    private fun frame(group: Int, item: Int, sub: Int, value: Int): ByteArray = byteArrayOf(
        0xFE.toByte(), 0x0D, group.toByte(), item.toByte(), sub.toByte(), value.toByte(),
        0x00, 0x00, 0x00, 0xFF.toByte(),
    )
}
