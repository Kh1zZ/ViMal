package dev.vimal.utl.core.data.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Audio-only normalization pipeline using Android's hardware-accelerated MediaCodec.
 *
 * Pipeline:
 *   AAC input → MediaCodec decoder → PCM FloatArray
 *       → EbuR128Calculator (measure LUFS)
 *       → apply linear gain to PCM
 *       → MediaCodec encoder → AAC output file
 *
 * Video stream is NOT touched here — VideoCopyHelper handles it separately.
 */
object AudioNormalizer {

    private const val TIMEOUT_US = 10_000L  // 10ms dequeue timeout

    /**
     * Normalize the audio track of [inputPath] and write normalized AAC to [outputPath].
     *
     * @param targetLufs  Target integrated loudness in LUFS (e.g. -14.0f for YouTube).
     * @param onProgress  Callback with 0f..1f progress.
     * @return The measured integrated LUFS of the original audio.
     */
    suspend fun normalize(
        inputPath: String,
        outputPath: String,
        targetLufs: Float,
        onProgress: (Float) -> Unit,
    ): Float = withContext(Dispatchers.Default) {

        // ── 1. Extract audio track format ──────────────────────────────────────
        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)
        val audioTrackIndex = findAudioTrack(extractor)
            ?: error("No audio track found in: $inputPath")
        extractor.selectTrack(audioTrackIndex)
        val inputFormat = extractor.getTrackFormat(audioTrackIndex)
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = inputFormat.getLong(MediaFormat.KEY_DURATION)

        // ── 2. Pass 1: Decode all PCM and measure LUFS ─────────────────────────
        onProgress(0.05f)
        val calculator = EbuR128Calculator(sampleRate, channelCount)
        val pcmChunks = mutableListOf<FloatArray>()

        decodeToPcm(
            extractor = extractor,
            inputFormat = inputFormat,
            durationUs = durationUs,
            onChunk = { pcm ->
                calculator.process(pcm)
                pcmChunks.add(pcm)
            },
            onProgress = { p -> onProgress(0.05f + p * 0.45f) },
        )

        val measuredLufs = calculator.integratedLoudness()
        val gainDb = targetLufs - measuredLufs
        val gainLinear = 10f.pow(gainDb / 20f)

        onProgress(0.5f)

        // ── 3. Pass 2: Encode PCM with gain applied ────────────────────────────
        val outputFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, inputFormat.getIntegerOrDefault(MediaFormat.KEY_BIT_RATE, 192_000))
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        encodePcmToAac(
            pcmChunks = pcmChunks,
            gainLinear = gainLinear,
            outputFormat = outputFormat,
            outputPath = outputPath,
            onProgress = { p -> onProgress(0.5f + p * 0.45f) },
        )

        extractor.release()
        onProgress(1.0f)

        measuredLufs
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return i
            }
        }
        return null
    }

    private fun decodeToPcm(
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        durationUs: Long,
        onChunk: (FloatArray) -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var presentationUs = 0L

        while (!outputDone) {
            // Feed input
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        presentationUs = extractor.sampleTime
                        decoder.queueInputBuffer(inIdx, 0, sampleSize, presentationUs, 0)
                        extractor.advance()
                    }
                }
            }

            // Drain output
            val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIdx >= 0) {
                val outBuf = decoder.getOutputBuffer(outIdx)!!
                if (info.size > 0) {
                    // Convert ByteBuffer (16-bit PCM) to FloatArray
                    val shorts = ShortArray(info.size / 2)
                    outBuf.rewind()
                    outBuf.asShortBuffer().get(shorts)
                    val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
                    onChunk(floats)
                    if (durationUs > 0) onProgress(info.presentationTimeUs.toFloat() / durationUs)
                }
                decoder.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        decoder.stop()
        decoder.release()
    }

    private fun encodePcmToAac(
        pcmChunks: List<FloatArray>,
        gainLinear: Float,
        outputFormat: MediaFormat,
        outputPath: String,
        onProgress: (Float) -> Unit,
    ) {
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val info = MediaCodec.BufferInfo()
        val outputFile = java.io.FileOutputStream(outputPath)
        var inputDone = false
        var chunkIdx = 0
        var chunkOffset = 0
        val total = pcmChunks.sumOf { it.size }
        var processed = 0

        while (true) {
            // Feed PCM frames
            if (!inputDone) {
                val inIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val buf = encoder.getInputBuffer(inIdx)!!
                    buf.clear()
                    val capacity = buf.remaining() / 2  // in shorts (16-bit)

                    if (chunkIdx >= pcmChunks.size) {
                        encoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        var written = 0
                        while (chunkIdx < pcmChunks.size && written < capacity) {
                            val chunk = pcmChunks[chunkIdx]
                            while (chunkOffset < chunk.size && written < capacity) {
                                val gained = (chunk[chunkOffset] * gainLinear).coerceIn(-1f, 1f)
                                buf.putShort((gained * 32767f).toInt().toShort())
                                chunkOffset++
                                written++
                                processed++
                            }
                            if (chunkOffset >= chunk.size) {
                                chunkIdx++
                                chunkOffset = 0
                            }
                        }
                        onProgress(processed.toFloat() / total)
                        encoder.queueInputBuffer(inIdx, 0, written * 2, 0, 0)
                    }
                }
            }

            // Drain encoded output
            val outIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIdx >= 0) {
                val outBuf = encoder.getOutputBuffer(outIdx)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                    // Write ADTS header + AAC frame
                    val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    writeAdtsHeader(outputFile, info.size, sampleRate, channels)
                    val bytes = ByteArray(info.size)
                    outBuf.rewind()
                    outBuf.get(bytes)
                    outputFile.write(bytes)
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }

        encoder.stop()
        encoder.release()
        outputFile.close()
    }

    /** Write 7-byte ADTS header (no CRC) for raw AAC frames. */
    private fun writeAdtsHeader(
        out: java.io.OutputStream,
        frameLength: Int,
        sampleRate: Int,
        channels: Int,
    ) {
        val freqIdx = sampleRateToIndex(sampleRate)
        val totalLength = frameLength + 7
        val header = ByteArray(7)
        header[0] = 0xFF.toByte()
        header[1] = 0xF1.toByte()  // MPEG-4, Layer 0, no CRC
        header[2] = ((0x01 shl 6) or (freqIdx shl 2) or (channels shr 2)).toByte()
        header[3] = ((channels and 0x3 shl 6) or (totalLength shr 11)).toByte()
        header[4] = ((totalLength and 0x7FF) shr 3).toByte()
        header[5] = (((totalLength and 0x7) shl 5) or 0x1F).toByte()
        header[6] = 0xFC.toByte()
        out.write(header)
    }

    private fun sampleRateToIndex(sr: Int): Int = when (sr) {
        96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3
        44100 -> 4; 32000 -> 5; 24000 -> 6; 22050 -> 7
        16000 -> 8; 12000 -> 9; 11025 -> 10; 8000 -> 11
        else -> 4  // fallback to 44100
    }

    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default
}
