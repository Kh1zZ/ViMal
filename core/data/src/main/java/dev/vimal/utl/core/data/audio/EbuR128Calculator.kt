package dev.vimal.utl.core.data.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure Kotlin implementation of EBU R128 integrated loudness measurement.
 * Implements ITU-R BS.1770-4:
 *  1. K-weighting pre-filter (two-stage: high-shelf + high-pass)
 *  2. Mean square measurement in 400ms blocks (75% overlap → 100ms steps)
 *  3. Absolute gate at -70 LUFS
 *  4. Relative gate at -10 LU below ungated mean
 *
 * Usage:
 *   val calc = EbuR128Calculator(sampleRate = 44100, channels = 2)
 *   // Feed raw interleaved PCM (short or float) incrementally:
 *   calc.process(pcmSamples)
 *   val lufs = calc.integratedLoudness() // LUFS value
 */
class EbuR128Calculator(
    private val sampleRate: Int,
    private val channels: Int,
) {
    // ── K-weighting filter coefficients (precomputed for common sample rates) ──
    // Stage 1: High-shelf (pre-filter)
    // Stage 2: High-pass
    // Coefficients generated via bilinear transform of analog prototypes per BS.1770-4.
    private val hs: BiquadFilter   // High-shelf
    private val hp: BiquadFilter   // High-pass

    // Block size for 400 ms (with 75% overlap)
    private val blockSizeSamples = (sampleRate * 0.4).toInt()
    private val stepSizeSamples = (sampleRate * 0.1).toInt()  // 100ms step

    // Accumulator for current 400ms block
    private val blockBuffer = FloatArray(blockSizeSamples * channels)
    private var blockWritePos = 0
    private var totalSamplesInBlock = 0

    // All 400ms block means (in linear power, sum across channels with channel weight)
    private val blockMeanSquares = mutableListOf<Double>()

    init {
        val (hsCoeffs, hpCoeffs) = computeKWeightingCoeffs(sampleRate.toDouble())
        hs = BiquadFilter(hsCoeffs, channels)
        hp = BiquadFilter(hpCoeffs, channels)
    }

    /**
     * Feed interleaved PCM samples (Float, range [-1.0, 1.0]).
     * Call repeatedly as audio frames arrive from MediaCodec.
     */
    fun process(samples: FloatArray) {
        // Apply K-weighting: two cascaded biquad stages
        val filtered = hs.process(samples)
        val kWeighted = hp.process(filtered)

        var i = 0
        while (i < kWeighted.size) {
            val remaining = kWeighted.size - i
            val spaceInBlock = (blockSizeSamples - totalSamplesInBlock) * channels
            val toCopy = minOf(remaining, spaceInBlock)

            kWeighted.copyInto(blockBuffer, blockWritePos, i, i + toCopy)
            blockWritePos += toCopy
            totalSamplesInBlock += toCopy / channels
            i += toCopy

            if (totalSamplesInBlock >= blockSizeSamples) {
                // Compute mean square for this 400ms block
                computeAndStoreBlock()

                // Overlap: slide buffer by 100ms (keep last 300ms)
                val samplesToKeep = (blockSizeSamples - stepSizeSamples) * channels
                blockBuffer.copyInto(blockBuffer, 0, stepSizeSamples * channels, blockSizeSamples * channels)
                blockWritePos = samplesToKeep
                totalSamplesInBlock = blockSizeSamples - stepSizeSamples
            }
        }
    }

    private fun computeAndStoreBlock() {
        var sumSquares = 0.0
        val samplesPerChannel = blockSizeSamples

        for (ch in 0 until channels) {
            val channelWeight = if (ch == 3 || ch == 4) 1.41 else 1.0  // LFE +1.5 dB (BS.1770)
            var chSum = 0.0
            var s = ch
            while (s < blockSizeSamples * channels) {
                val v = blockBuffer[s].toDouble()
                chSum += v * v
                s += channels
            }
            sumSquares += channelWeight * (chSum / samplesPerChannel)
        }
        blockMeanSquares.add(sumSquares)
    }

    /**
     * Returns integrated loudness in LUFS (negative value).
     * Call after feeding all samples.
     */
    fun integratedLoudness(): Float {
        // Flush the last partial block if it holds at least one step of real audio (100 ms).
        // Without this, audio that ends mid-block is silently discarded, producing an
        // inaccurate LUFS reading for short clips or clips whose tail is quiet.
        if (totalSamplesInBlock >= stepSizeSamples) {
            computeAndStoreBlock()
        }

        if (blockMeanSquares.isEmpty()) return -70f

        // Absolute gate: keep blocks above -70 LUFS
        val absThresholdPower = 10.0.pow((-70.0 - 0.691) / 10.0)
        val gated1 = blockMeanSquares.filter { it > absThresholdPower }
        if (gated1.isEmpty()) return -70f

        // Ungated mean for relative gate
        val ungatedMean = gated1.average()
        val relThresholdPower = ungatedMean * 10.0.pow(-10.0 / 10.0)  // -10 LU relative gate

        // Relative gate: keep blocks above relative threshold
        val gated2 = gated1.filter { it > relThresholdPower }
        if (gated2.isEmpty()) return -70f

        val integratedPower = gated2.average()
        return (-0.691 + 10.0 * log10(integratedPower)).toFloat()
    }

    /** Reset state for a new file. */
    fun reset() {
        blockMeanSquares.clear()
        blockBuffer.fill(0f)
        blockWritePos = 0
        totalSamplesInBlock = 0
        hs.reset()
        hp.reset()
    }

    // ── K-weighting biquad coefficient computation ─────────────────────────────

    private data class BiquadCoeffs(
        val b0: Double, val b1: Double, val b2: Double,
        val a1: Double, val a2: Double,
    )

    private fun computeKWeightingCoeffs(fs: Double): Pair<BiquadCoeffs, BiquadCoeffs> {
        // Stage 1: High-shelf filter (pre-filter)
        // Analog prototype: db = +4 dB shelf at ~1500 Hz
        val db = 3.99984385397  // gain in dB
        val f0 = 1681.974450955533
        val Q = 0.7071752369554196
        val K = kotlin.math.tan(Math.PI * f0 / fs)
        val Vh = 10.0.pow(db / 20.0)
        val Vb = Vh.pow(0.4996667741545416)
        val a0HS = 1.0 + K / Q + K * K
        val b0HS = (Vh + Vb * K / Q + K * K) / a0HS
        val b1HS = 2.0 * (K * K - Vh) / a0HS
        val b2HS = (Vh - Vb * K / Q + K * K) / a0HS
        val a1HS = 2.0 * (K * K - 1.0) / a0HS
        val a2HS = (1.0 - K / Q + K * K) / a0HS
        val hsCoeffs = BiquadCoeffs(b0HS, b1HS, b2HS, a1HS, a2HS)

        // Stage 2: High-pass filter
        val f0HP = 38.13547087602444
        val QHP = 0.5003270373238773
        val KHP = kotlin.math.tan(Math.PI * f0HP / fs)
        val a0HP = 1.0 + KHP / QHP + KHP * KHP
        val b0HP = 1.0 / a0HP
        val b1HP = -2.0 / a0HP
        val b2HP = 1.0 / a0HP
        val a1HP = 2.0 * (KHP * KHP - 1.0) / a0HP
        val a2HP = (1.0 - KHP / QHP + KHP * KHP) / a0HP
        val hpCoeffs = BiquadCoeffs(b0HP, b1HP, b2HP, a1HP, a2HP)

        return Pair(hsCoeffs, hpCoeffs)
    }

    // ── Direct-form II biquad filter ───────────────────────────────────────────
    private class BiquadFilter(private val c: BiquadCoeffs, private val channels: Int) {
        // Per-channel delay lines
        private val z1 = DoubleArray(channels)
        private val z2 = DoubleArray(channels)

        fun process(input: FloatArray): FloatArray {
            val out = FloatArray(input.size)
            for (n in input.indices) {
                val ch = n % channels
                val x = input[n].toDouble()
                val w = x - c.a1 * z1[ch] - c.a2 * z2[ch]
                val y = c.b0 * w + c.b1 * z1[ch] + c.b2 * z2[ch]
                z2[ch] = z1[ch]
                z1[ch] = w
                out[n] = y.toFloat()
            }
            return out
        }

        fun reset() {
            z1.fill(0.0)
            z2.fill(0.0)
        }
    }
}
