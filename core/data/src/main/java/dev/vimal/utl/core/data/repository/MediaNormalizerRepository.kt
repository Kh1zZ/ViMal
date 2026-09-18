package dev.vimal.utl.core.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import dev.vimal.utl.core.data.audio.AudioNormalizer
import dev.vimal.utl.core.data.output.OutputFileManager
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.NormalizeResult
import dev.vimal.utl.core.domain.model.VideoInfo
import dev.vimal.utl.core.domain.repository.NormalizerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileDescriptor
import kotlin.math.pow

/**
 * Concrete implementation of [NormalizerRepository].
 * Orchestrates: Fast metadata retrieval → Pass 1 Audio Analysis → Pass 2 Stream Copy Video & Calibrated AAC Mux.
 */
class MediaNormalizerRepository : NormalizerRepository {

    private var lastResult: NormalizeResult = NormalizeResult.Failure(IllegalStateException("No normalization run yet"))

    override suspend fun getVideoInfo(context: Context, source: MediaSource): VideoInfo = withContext(Dispatchers.IO) {
        val uri = (source as MediaSource.LocalUri).uri
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var width = 0
        var height = 0
        var mimeType = "video/mp4"

        try {
            val pfd = try {
                context.contentResolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) { null }

            if (pfd != null) {
                pfd.use { parcelFd ->
                    retriever.setDataSource(parcelFd.fileDescriptor)
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                    durationMs = durStr?.toLongOrNull() ?: 0L
                    val w = wStr?.toIntOrNull() ?: 0
                    val h = hStr?.toIntOrNull() ?: 0
                    val rotation = rotStr?.toIntOrNull() ?: 0

                    if (rotation == 90 || rotation == 270) {
                        width = h
                        height = w
                    } else {
                        width = w
                        height = h
                    }
                    if (!mimeStr.isNullOrBlank()) mimeType = mimeStr
                }
            } else {
                retriever.setDataSource(context, uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rotStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                durationMs = durStr?.toLongOrNull() ?: 0L
                val w = wStr?.toIntOrNull() ?: 0
                val h = hStr?.toIntOrNull() ?: 0
                val rotation = rotStr?.toIntOrNull() ?: 0

                if (rotation == 90 || rotation == 270) {
                    width = h
                    height = w
                } else {
                    width = w
                    height = h
                }
                if (!mimeStr.isNullOrBlank()) mimeType = mimeStr
            }
        } catch (_: Exception) {
            // Retriever fallback
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Get file size and display name from ContentResolver
        val fileSizeBytes = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) { 0L }

        val fileName = try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment ?: "video.mp4"
        } catch (_: Exception) { uri.lastPathSegment ?: "video.mp4" }

        VideoInfo(
            uri = uri,
            fileName = fileName,
            durationMs = durationMs,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            mimeType = mimeType,
            videoCodec = null,
            audioCodec = null,
            measuredLufs = null,
        )
    }

    override fun normalize(
        context: Context,
        source: MediaSource,
        preset: LoudnessPreset,
        customTargetLufs: Float?,
    ): Flow<NormalizeProgress> = channelFlow {
        val targetLufs = if (preset == LoudnessPreset.CUSTOM) customTargetLufs!! else preset.targetLufs
        val uri = (source as MediaSource.LocalUri).uri

        val tempMuxedFile = OutputFileManager.createTempFile(context, "_muxed.mp4")

        try {
            send(NormalizeProgress.Analyzing)

            // Open source file descriptor
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open file descriptor for URI: $uri")

            val audioData = pfd.use { parcelFd ->
                AudioNormalizer.analyze(
                    fd = parcelFd.fileDescriptor,
                    onProgress = { p ->
                        trySend(NormalizeProgress.Processing(p * 0.45f, NormalizeProgress.Stage.MEASURING_LUFS))
                    }
                )
            }

            val measuredLufs = audioData.measuredLufs
            val gainDb = targetLufs - measuredLufs
            val gainLinear = 10f.pow(gainDb / 20f)

            send(NormalizeProgress.Processing(0.5f, NormalizeProgress.Stage.ENCODING_AUDIO))

            // Open second descriptor for muxing video stream and encoding audio
            val muxPfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open file descriptor for muxing: $uri")

            muxPfd.use { parcelFd ->
                muxNormalizedVideo(
                    videoFd = parcelFd.fileDescriptor,
                    audio = audioData,
                    gainLinear = gainLinear,
                    tempOutputFile = tempMuxedFile,
                    onProgress = { p ->
                        trySend(NormalizeProgress.Processing(0.5f + p * 0.45f, NormalizeProgress.Stage.ENCODING_AUDIO))
                    }
                )
            }

            send(NormalizeProgress.Muxing)

            // Publish to MediaStore Movies/ViMal
            val fileName = try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else "video.mp4" } ?: "video.mp4"
            } catch (_: Exception) { "video.mp4" }

            val outputUri = OutputFileManager.publishToMediaStore(context, fileName, tempMuxedFile)
            val durationMs = audioData.durationUs / 1000L

            lastResult = NormalizeResult.Success(
                outputUri = outputUri,
                outputPath = outputUri.toString(),
                measuredLufs = measuredLufs,
                targetLufs = targetLufs,
                appliedGainDb = gainDb,
                durationMs = durationMs,
            )

            send(NormalizeProgress.Completed)

        } catch (e: Exception) {
            lastResult = NormalizeResult.Failure(e)
            throw e
        } finally {
            OutputFileManager.cleanupTempFiles(tempMuxedFile)
        }
    }

    override suspend fun awaitResult(): NormalizeResult = lastResult

    // ─────────────────────────────────────────────────────────────────────────

    private fun muxNormalizedVideo(
        videoFd: FileDescriptor,
        audio: AudioNormalizer.DecodedAudio,
        gainLinear: Float,
        tempOutputFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // 1. Add video track (verbatim copy from source)
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFd)
        val videoTrackIdx = findVideoTrack(videoExtractor)
        videoExtractor.selectTrack(videoTrackIdx)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIdx)
        val muxerVideoTrack = muxer.addTrack(videoFormat)

        // 2. Setup AAC Encoder
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            audio.sampleRate,
            audio.channelCount,
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, audio.bitRate.coerceAtLeast(128_000))
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        var muxerAudioTrack = -1
        var muxerStarted = false

        val encoderInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var chunkIdx = 0
        var chunkOffset = 0
        val totalSamples = audio.pcmChunks.sumOf { it.size }
        var processedSamples = 0
        var lastProgressTime = 0L

        fun drainEncoder() {
            while (true) {
                val outIdx = encoder.dequeueOutputBuffer(encoderInfo, 1000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        muxerAudioTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outIdx >= 0) {
                    val outBuf = encoder.getOutputBuffer(outIdx)!!
                    if (encoderInfo.size > 0 && muxerStarted) {
                        if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            muxer.writeSampleData(muxerAudioTrack, outBuf, encoderInfo)
                        }
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else {
                    break
                }
            }
        }

        // 3. Feed PCM to encoder and drain output
        while (!inputDone) {
            val inIdx = encoder.dequeueInputBuffer(1000L)
            if (inIdx >= 0) {
                val buf = encoder.getInputBuffer(inIdx)!!
                buf.clear()
                val capacity = buf.remaining() / 2  // in 16-bit shorts

                if (chunkIdx >= audio.pcmChunks.size) {
                    val sampleTimeUs = if (audio.sampleRate > 0) {
                        (processedSamples.toLong() / audio.channelCount) * 1_000_000L / audio.sampleRate
                    } else 0L
                    encoder.queueInputBuffer(inIdx, 0, 0, sampleTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    val sampleTimeUs = if (audio.sampleRate > 0) {
                        (processedSamples.toLong() / audio.channelCount) * 1_000_000L / audio.sampleRate
                    } else 0L
                    var written = 0
                    while (chunkIdx < audio.pcmChunks.size && written < capacity) {
                        val chunk = audio.pcmChunks[chunkIdx]
                        while (chunkOffset < chunk.size && written < capacity) {
                            val gained = (chunk[chunkOffset] * gainLinear).coerceIn(-1f, 1f)
                            buf.putShort((gained * 32767f).toInt().toShort())
                            chunkOffset++
                            written++
                            processedSamples++
                        }
                        if (chunkOffset >= chunk.size) {
                            chunkIdx++
                            chunkOffset = 0
                        }
                    }
                    encoder.queueInputBuffer(inIdx, 0, written * 2, sampleTimeUs, 0)

                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime > 60) {
                        lastProgressTime = now
                        if (totalSamples > 0) {
                            onProgress(processedSamples.toFloat() / totalSamples)
                        }
                    }
                }
            }
            drainEncoder()
        }

        // Drain remaining encoder output until EOS
        while (true) {
            val outIdx = encoder.dequeueOutputBuffer(encoderInfo, 5000L)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    muxerAudioTrack = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
            } else if (outIdx >= 0) {
                val outBuf = encoder.getOutputBuffer(outIdx)!!
                if (encoderInfo.size > 0 && muxerStarted) {
                    if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        muxer.writeSampleData(muxerAudioTrack, outBuf, encoderInfo)
                    }
                }
                encoder.releaseOutputBuffer(outIdx, false)
                if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            } else {
                break
            }
        }

        // Ensure muxer is started
        if (!muxerStarted) {
            muxerAudioTrack = muxer.addTrack(encoder.outputFormat)
            muxer.start()
            muxerStarted = true
        }

        // 4. Copy video samples into muxer
        val videoBuf = java.nio.ByteBuffer.allocate(1024 * 1024)
        val videoInfo = MediaCodec.BufferInfo()
        while (true) {
            videoBuf.clear()
            val sz = videoExtractor.readSampleData(videoBuf, 0)
            if (sz < 0) break
            videoInfo.offset = 0
            videoInfo.size = sz
            videoInfo.presentationTimeUs = videoExtractor.sampleTime
            videoInfo.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(muxerVideoTrack, videoBuf, videoInfo)
            videoExtractor.advance()
        }

        // Clean up
        encoder.stop()
        encoder.release()
        videoExtractor.release()
        try {
            muxer.stop()
        } catch (_: Exception) {}
        muxer.release()
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) return i
        }
        error("No video track found")
    }
}
