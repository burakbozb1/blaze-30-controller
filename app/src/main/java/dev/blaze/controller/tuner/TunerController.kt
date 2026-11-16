package dev.blaze.controller.tuner

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Source of microphone frames (4096 16-bit samples each). Throws [IllegalStateException] if the microphone cannot be opened. */
interface AudioSource {
    fun frames(): Flow<ShortArray>
}

sealed interface TunerUi {
    data object NoSignal : TunerUi
    data class Reading(val note: NoteReading) : TunerUi
    data class Error(val message: String) : TunerUi
}

/**
 * Turns microphone frames into what the tuner screen shows. A glitch frame cannot flip the display (median of the
 * last [medianSize] detections, and nothing is shown before [minDetections] have been collected), the last note is
 * held for [holdFrames] silent frames, and nothing is shown for silence or noise. Collecting is tied to [run]; cancelling it releases the microphone.
 */
class TunerController(
    private val source: AudioSource,
    private val detector: PitchDetector = PitchDetector(),
    private val holdFrames: Int = 5,
    private val medianSize: Int = 5,
    private val minDetections: Int = 3,
) {
    private val _ui = MutableStateFlow<TunerUi>(TunerUi.NoSignal)
    val ui: StateFlow<TunerUi> = _ui.asStateFlow()

    /** Listens until cancelled. Never throws except [CancellationException]; failures end in [TunerUi.Error]. */
    suspend fun run() {
        _ui.value = TunerUi.NoSignal
        val recent = ArrayDeque<Double>()
        var missing = 0
        try {
            source.frames().collect { frame ->
                val hz = detector.detect(frame)
                if (hz == null) {
                    if (++missing >= holdFrames) {
                        recent.clear()
                        _ui.value = TunerUi.NoSignal
                    }
                } else {
                    missing = 0
                    recent.addLast(hz)
                    if (recent.size > medianSize) recent.removeFirst()
                    // A pluck attack or a stray sound gives one or two wrong frames: show nothing until a few agree.
                    if (recent.size >= minDetections) {
                        NoteMath.fromFrequency(median(recent))?.let { _ui.value = TunerUi.Reading(it) }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.value = TunerUi.Error("Mikrofon kullanılamıyor")
        }
    }

    /** A value that was actually detected (the lower middle for an even count), never an average of two. */
    private fun median(values: Collection<Double>): Double = values.sorted()[(values.size - 1) / 2]
}
