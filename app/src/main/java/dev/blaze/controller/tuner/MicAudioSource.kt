package dev.blaze.controller.tuner

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Phone microphone as 4096-sample mono 16-bit frames at 44.1 kHz. The microphone is opened when collection starts
 * and always released when it ends (cancellation, error or completion). The caller must hold RECORD_AUDIO.
 */
class MicAudioSource(
    private val sampleRate: Int = 44_100,
    private val frameSize: Int = 4096,
) : AudioSource {

    @SuppressLint("MissingPermission")
    override fun frames(): Flow<ShortArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuffer > 0) { "microphone parameters not supported" }
        val bufferBytes = max(minBuffer, frameSize * 2) * 4
        // UNPROCESSED avoids the phone's automatic gain and noise processing, which distorts pitch tracking.
        val record = open(MediaRecorder.AudioSource.UNPROCESSED, bufferBytes)
            ?: open(MediaRecorder.AudioSource.MIC, bufferBytes)
            ?: throw IllegalStateException("microphone could not be opened")
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "microphone is in use" }
            val buffer = ShortArray(frameSize)
            var filled = 0
            while (currentCoroutineContext().isActive) {
                val n = record.read(buffer, filled, frameSize - filled)
                check(n >= 0) { "microphone read failed: $n" }
                filled += n
                if (filled == frameSize) {
                    emit(buffer.copyOf())
                    filled = 0
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    private fun open(source: Int, bufferBytes: Int): AudioRecord? = try {
        AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
            ?: null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: UnsupportedOperationException) {
        null
    }
}
