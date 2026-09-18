package dev.vimal.utl.core.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EbuR128CalculatorTest {

    @Test
    fun testEmptyInputReturnsMinus70LUFS() {
        val calc = EbuR128Calculator(sampleRate = 48000, channels = 2)
        assertEquals(-70f, calc.integratedLoudness(), 0.001f)
    }

    @Test
    fun testSilenceReturnsMinus70LUFS() {
        val calc = EbuR128Calculator(sampleRate = 48000, channels = 2)
        // 1 second of stereo silence (96000 samples)
        val silence = FloatArray(96000) { 0f }
        calc.process(silence)
        assertEquals(-70f, calc.integratedLoudness(), 0.001f)
    }

    @Test
    fun testSineWaveProducesMeasurableLoudness() {
        val sampleRate = 48000
        val channels = 2
        val calc = EbuR128Calculator(sampleRate = sampleRate, channels = channels)

        // Generate 2 seconds of 1 kHz stereo sine wave at 0.5 amplitude (-6 dBFS)
        val durationSec = 2.0
        val totalFrames = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalFrames * channels)

        val freq = 1000.0
        for (i in 0 until totalFrames) {
            val v = (0.5 * sin(2.0 * PI * freq * i / sampleRate)).toFloat()
            samples[i * 2] = v      // Left
            samples[i * 2 + 1] = v  // Right
        }

        calc.process(samples)
        val lufs = calc.integratedLoudness()

        // BS.1770 K-weighting at 1kHz has small gain, at 0.5 amplitude (-6 dBFS)
        // Expected integrated loudness is around -6 to -8 LUFS
        assertTrue("Expected LUFS between -15 and 0, got $lufs", lufs in -15.0f..0.0f)
    }

    @Test
    fun testResetRestoresInitialState() {
        val sampleRate = 48000
        val calc = EbuR128Calculator(sampleRate = sampleRate, channels = 1)
        val sine = FloatArray(sampleRate) { (0.5 * sin(2.0 * PI * 1000.0 * it / sampleRate)).toFloat() }

        calc.process(sine)
        val lufsBefore = calc.integratedLoudness()
        assertTrue(lufsBefore > -70f)

        calc.reset()
        assertEquals(-70f, calc.integratedLoudness(), 0.001f)
    }
}
