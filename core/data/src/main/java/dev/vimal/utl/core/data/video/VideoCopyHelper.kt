package dev.vimal.utl.core.data.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * CopyStrategy implementation: bit-for-bit video stream copy.
 *
 * Reads raw encoded video samples from [inputPath] via MediaExtractor
 * and writes them verbatim to [outputMuxer] without any decode/re-encode.
 * Video quality is 100% identical to source.
 *
 * This satisfies the hard constraint from PRD §9:
 * "Video tidak boleh ter-decode/re-encode di MVP (stream-copy only)"
 */
object VideoCopyHelper {

    private const val BUFFER_SIZE = 1024 * 1024  // 1 MB read buffer
    private const val TIMEOUT_US = 10_000L

    /**
     * Copy the video track from [inputPath] into [outputMuxer].
     *
     * @param inputPath     Source video file path.
     * @param outputMuxer   Already-started MediaMuxer to write into.
     * @param onProgress    0f..1f progress callback.
     * @return Index of the added video track in [outputMuxer].
     */
    suspend fun copyVideoTrack(
        inputPath: String,
        outputMuxer: MediaMuxer,
        onProgress: (Float) -> Unit,
    ): Int = withContext(Dispatchers.IO) {

        val extractor = MediaExtractor()
        extractor.setDataSource(inputPath)

        val videoTrackIndex = findVideoTrack(extractor)
            ?: error("No video track found in: $inputPath")

        extractor.selectTrack(videoTrackIndex)
        val format = extractor.getTrackFormat(videoTrackIndex)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val muxerTrackIndex = outputMuxer.addTrack(format)

        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val bufferInfo = android.media.MediaCodec.BufferInfo()

        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            outputMuxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)

            if (durationUs > 0) {
                onProgress(extractor.sampleTime.toFloat() / durationUs)
            }
            extractor.advance()
        }

        extractor.release()
        onProgress(1f)
        muxerTrackIndex
    }

    /**
     * Extract just the video track format (for muxer setup before starting).
     */
    fun getVideoTrackFormat(inputPath: String): MediaFormat? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(inputPath)
            val idx = findVideoTrack(extractor) ?: return null
            extractor.getTrackFormat(idx)
        } finally {
            extractor.release()
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i
        }
        return null
    }
}
