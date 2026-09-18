package dev.vimal.utl.core.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor

/**
 * High-performance audio analysis pipeline using Android native MediaCodec.
 * Decodes audio via MediaCodec and measures integrated LUFS via EbuR128Calculator.
 */
object AudioNormalizer {

    private const val TIMEOUT_US = 1_000L  // 1ms non-blocking polling timeout

    data class DecodedAudio(
        val pcmChunks: List<FloatArray>,
        val sampleRate: Int,
        val channelCount: Int,
        val bitRate: Int,
        val durationUs: Long,
        val measuredLufs: Float,
    )

    /**
     * Pass 1: Decode audio track from FileDescriptor to PCM and measure integrated LUFS.
     */
    suspend fun analyze(
        fd: FileDescriptor,
        onProgress: (Float) -> Unit,
    ): DecodedAudio = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        extractor.setDataSource(fd)

        val audioTrackIndex = findAudioTrack(extractor)
            ?: error("No audio track found in media file")

        extractor.selectTrack(audioTrackIndex)
        val inputFormat = extractor.getTrackFormat(audioTrackIndex)
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
            inputFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L
        val bitRate = if (inputFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
            inputFormat.getInteger(MediaFormat.KEY_BIT_RATE)
        } else 192_000

        val calculator = EbuR128Calculator(sampleRate, channelCount)
        val pcmChunks = mutableListOf<FloatArray>()

        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var lastProgressTime = 0L

        while (!outputDone) {
            if (!inputDone) {
                val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val sampleSize = extractor.readSampleData(buf, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        val pts = extractor.sampleTime
                        decoder.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIdx >= 0) {
                val outBuf = decoder.getOutputBuffer(outIdx)!!
                if (info.size > 0) {
                    val shorts = ShortArray(info.size / 2)
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    outBuf.asShortBuffer().get(shorts)

                    val floats = FloatArray(shorts.size) { shorts[it] / 32768f }
                    calculator.process(floats)
                    pcmChunks.add(floats)

                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 60 || (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)) {
                        lastProgressTime = now
                        if (durationUs > 0) {
                            onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                        }
                    }
                }
                decoder.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        decoder.stop()
        decoder.release()
        extractor.release()

        val measuredLufs = calculator.integratedLoudness()
        DecodedAudio(
            pcmChunks = pcmChunks,
            sampleRate = sampleRate,
            channelCount = channelCount,
            bitRate = bitRate,
            durationUs = durationUs,
            measuredLufs = measuredLufs,
        )
    }

    fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                return i
            }
        }
        return null
    }
}
