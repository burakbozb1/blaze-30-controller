package dev.blaze.controller.tuner

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TunerControllerTest {

    private fun tone(hz: Double, amp: Double = 0.3): ShortArray =
        ShortArray(4096) { (sin(2 * PI * hz * it / 44_100.0) * amp * 32767).toInt().toShort() }

    private val silence = ShortArray(4096)

    private fun noise(seed: Long): ShortArray {
        val rnd = Random(seed)
        return ShortArray(4096) { ((rnd.nextDouble() * 2 - 1) * 0.3 * 32767).toInt().toShort() }
    }

    /** Frames are pushed by the test one at a time; counts how often the source is opened and released. */
    private class FakeSource : AudioSource {
        val channel = Channel<ShortArray>(Channel.UNLIMITED)
        var failOnOpen = false
        var starts = 0
        var releases = 0
        override fun frames(): Flow<ShortArray> = flow {
            starts++
            try {
                if (failOnOpen) throw IllegalStateException("microphone busy")
                for (frame in channel) emit(frame)
            } finally {
                releases++
            }
        }
    }

    private fun TestScope.start(source: FakeSource, controller: TunerController) =
        backgroundScope.launch { controller.run() }.also { runCurrent() }

    private fun TestScope.push(source: FakeSource, vararg frames: ShortArray) {
        for (f in frames) { source.channel.trySend(f); runCurrent() }
    }

    private fun TunerController.reading(): NoteReading? = (ui.value as? TunerUi.Reading)?.note

    @Test
    fun steadyLowEShowsE2OnString6() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        start(source, c)
        push(source, *Array(8) { tone(82.41) })
        val r = c.reading()!!
        assertEquals("E", r.name); assertEquals(2, r.octave); assertEquals(6, r.string)
        assertTrue(abs(r.cents) < 2)
    }

    @Test
    fun silenceAndRoomNoiseNeverShowANote() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        start(source, c)
        repeat(20) { push(source, silence); assertEquals(TunerUi.NoSignal, c.ui.value) }
        repeat(20) { push(source, noise(it.toLong())); assertEquals(TunerUi.NoSignal, c.ui.value) }
    }

    @Test
    fun holdsTheLastNoteBrieflyThenGoesToNoSignal() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        start(source, c)
        push(source, *Array(5) { tone(110.0) })
        assertEquals("A", c.reading()!!.name)

        repeat(4) { push(source, silence) }
        assertTrue("still holding after 4 silent frames", c.ui.value is TunerUi.Reading)
        push(source, silence)
        assertEquals(TunerUi.NoSignal, c.ui.value)
    }

    @Test
    fun aSingleOctaveGlitchFrameDoesNotFlipTheDisplay() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        start(source, c)
        push(source, *Array(4) { tone(82.41) })
        push(source, tone(164.82)) // one frame that the detector reads an octave up
        assertEquals("E", c.reading()!!.name); assertEquals(2, c.reading()!!.octave)
        push(source, tone(82.41), tone(82.41))
        assertEquals(2, c.reading()!!.octave)
    }

    @Test
    fun sourceFailureShowsAnErrorAndRunReturnsNormally() = runTest {
        val source = FakeSource().apply { failOnOpen = true }
        val c = TunerController(source)
        val job = start(source, c)
        assertEquals(TunerUi.Error("Mikrofon kullanılamıyor"), c.ui.value)
        assertTrue(job.isCompleted); assertFalse(job.isCancelled)
    }

    @Test
    fun runningAgainAfterAnErrorRecovers() = runTest {
        val source = FakeSource().apply { failOnOpen = true }
        val c = TunerController(source)
        start(source, c)
        assertTrue(c.ui.value is TunerUi.Error)

        source.failOnOpen = false
        start(source, c)
        assertEquals(TunerUi.NoSignal, c.ui.value)
        push(source, *Array(3) { tone(110.0) })
        assertEquals("A", c.reading()!!.name)
    }

    @Test
    fun cancellingRunReleasesTheMicrophone() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        val job = start(source, c)
        push(source, tone(110.0))
        assertEquals(1, source.starts); assertEquals(0, source.releases)

        job.cancel(); runCurrent()

        assertEquals(1, source.releases)
    }

    @Test
    fun theFirstFramesAfterSilenceNeverShowANoteThatWasNotPlayed() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        start(source, c)
        push(source, silence, silence)

        // the pick attack: the detector first reads an octave too high, then settles on E2
        val steps = listOf(tone(164.82), tone(82.41), tone(82.41), tone(82.41), tone(82.41))
        for (frame in steps) {
            push(source, frame)
            val shown = c.reading()
            assertTrue("showed ${shown?.name}${shown?.octave}", shown == null || (shown.name == "E" && shown.octave == 2))
        }
        assertEquals("E", c.reading()!!.name)
    }

    @Test
    fun twoDetectionsAreNotEnoughToShowANoteButThreeAre() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        start(source, c)
        push(source, tone(82.41), tone(82.41))
        assertEquals(TunerUi.NoSignal, c.ui.value)
        push(source, tone(82.41))
        assertEquals("E", c.reading()!!.name)
    }

    @Test
    fun aLoneStrayDetectionInSilenceNeverShowsANote() = runTest {
        val source = FakeSource(); val c = TunerController(source)
        start(source, c)
        push(source, silence, silence, silence)
        push(source, tone(110.0)) // a knock or a voice that slipped through the gate
        assertEquals(TunerUi.NoSignal, c.ui.value)
        repeat(10) { push(source, silence); assertEquals(TunerUi.NoSignal, c.ui.value) }
    }
}
