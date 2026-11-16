package dev.blaze.controller.protocol

/** Amp types with their wire codes (docs/protocol.md). Declaration order is irrelevant to the wire. */
enum class AmpType(val code: Int) {
    CLEAN(0), ACOUSTIC(1), DRIVE(2), DISTORTION(3), METAL(4)
}

enum class GuitarEffect(val code: Int) {
    BYPASS(0), FLANGER(1), CHORUS(2), PHASER(3), WAH(4), VIBRATO(5)
}

enum class ReverbType(val code: Int) {
    PLATE(0), ROOM(1), SPRING(2)
}

/** Value ranges from docs/protocol.md. Out-of-range input is clamped by the codec, never sent raw. */
object Ranges {
    val GAIN = 0..30
    val VOLUME = 0..50
    val AUX_VOLUME = 0..50
    val EQ_LEVEL = 0..20
    const val EQ_BANDS = 7
    val EFFECT_KNOB = 0..20
    val MASTER_FX = 0..50
}

sealed interface AmpCommand {
    /**
     * Not a user control: the session handshake the amp expects after every new link
     * (observed: commands are ignored after an amp power cycle until it is sent). See docs/protocol.md.
     */
    data object Sync : AmpCommand

    data class SetAmpType(val type: AmpType) : AmpCommand
    data class SetGain(val value: Int) : AmpCommand
    data class SetVolume(val value: Int) : AmpCommand

    /** Music/Bluetooth (AUX) volume, a separate level from the guitar GUI VOL (docs/protocol.md). */
    data class SetAuxVolume(val value: Int) : AmpCommand

    /** [index] is 0-based (0..6); the wire uses bands 1..7. */
    data class SetEqBand(val index: Int, val level: Int) : AmpCommand {
        init {
            require(index in 0 until Ranges.EQ_BANDS) { "EQ band index must be 0..6, was $index" }
        }
    }

    data class SetEffect(val effect: GuitarEffect) : AmpCommand

    /** Guitar-tab delay level ("GUI ECHO" on the amp's display); master for the delay, separate from the effect page. */
    data class SetMasterDelay(val value: Int) : AmpCommand

    /** Guitar-tab reverb level; master for the reverb, separate from the effect page. */
    data class SetMasterReverb(val value: Int) : AmpCommand
    data class SetReverbType(val type: ReverbType) : AmpCommand
    data class SetReverbLevel(val value: Int) : AmpCommand
    data class SetReverbTone(val value: Int) : AmpCommand
    data class SetReverbTime(val value: Int) : AmpCommand
    data class SetDelayLevel(val value: Int) : AmpCommand
    data class SetDelayFeedback(val value: Int) : AmpCommand
    data class SetDelayTime(val value: Int) : AmpCommand
}
