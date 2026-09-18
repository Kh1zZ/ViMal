package dev.vimal.utl.core.data.repository

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dev.vimal.utl.core.data.audio.AudioNormalizer
import dev.vimal.utl.core.data.output.OutputFileManager
import dev.vimal.utl.core.data.video.VideoCopyHelper
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.NormalizeResult
import dev.vimal.utl.core.domain.model.VideoInfo
import dev.vimal.utl.core.domain.repository.NormalizerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Concrete implementation of [NormalizerRepository].
 * Orchestrates: demux → audio normalize → video copy → mux into final MP4.
 */
class MediaNormalizerRepository : NormalizerRepository {

    private var lastResult: NormalizeResult = NormalizeResult.Failure(IllegalStateException("No normalization run yet"))

    override suspend fun getVideoInfo(context: Context, source: MediaSource): VideoInfo {
        val uri = (source as MediaSource.LocalUri).uri
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var durationUs = 0L
        var width = 0
        var height = 0
        var mimeType = "video/mp4"
        var videoCodec: String? = null
        var audioCodec: String? = null

        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            when {
                mime.startsWith("video/") -> {
                    videoCodec = mime
                    width = if (fmt.containsKey(MediaFormat.KEY_WIDTH)) fmt.getInteger(MediaFormat.KEY_WIDTH) else 0
                    height = if (fmt.containsKey(MediaFormat.KEY_HEIGHT)) fmt.getInteger(MediaFormat.KEY_HEIGHT) else 0
                    if (fmt.containsKey(MediaFormat.KEY_DURATION)) durationUs = fmt.getLong(MediaFormat.KEY_DURATION)
                }
                mime.startsWith("audio/") -> audioCodec = mime
            }
        }
        extractor.release()

        // Get file size and display name
        val fileSizeBytes = context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: 0L

        val fileName = context.contentResolver.query(
            uri, arrayOf(android.provider.MediaStore.Video.Media.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else "video.mp4"
        } ?: uri.lastPathSegment ?: "video.mp4"

        mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

        return VideoInfo(
            uri = uri,
            fileName = fileName,
            durationMs = durationUs / 1000,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            mimeType = mimeType,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
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

        // Resolve file path from URI
        val inputPath = OutputFileManager.getPathFromUri(context, uri)
            ?: error("Cannot resolve file path from URI: $uri")

        val tempAudioFile = OutputFileManager.createTempFile(context, "_audio.aac")
        val tempMuxedFile = OutputFileManager.createTempFile(context, "_muxed.mp4")

        try {
            send(NormalizeProgress.Analyzing)

            // ── Step 1: Normalize audio ──────────────────────────────────────
            var measuredLufs = -70f
            val audioJob = launch {
                measuredLufs = AudioNormalizer.normalize(
                    inputPath = inputPath,
                    outputPath = tempAudioFile.absolutePath,
                    targetLufs = targetLufs,
                    onProgress = { p ->
                        trySend(NormalizeProgress.Processing(p * 0.8f, NormalizeProgress.Stage.ENCODING_AUDIO))
                    },
                )
            }
            audioJob.join()

            // ── Step 2: Mux video + normalized audio ─────────────────────────
            send(NormalizeProgress.Muxing)
            muxVideoAndAudio(
                context = context,
                inputPath = inputPath,
                normalizedAudioPath = tempAudioFile.absolutePath,
                outputPath = tempMuxedFile.absolutePath,
            )

            // ── Step 3: Publish to MediaStore ────────────────────────────────
            val fileName = context.contentResolver.query(
                uri, arrayOf(android.provider.MediaStore.Video.Media.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else "video.mp4" } ?: "video.mp4"

            val outputUri = OutputFileManager.publishToMediaStore(context, fileName, tempMuxedFile)
            val durationMs = getVideoDurationMs(inputPath)
            val appliedGain = targetLufs - measuredLufs

            lastResult = NormalizeResult.Success(
                outputUri = outputUri,
                outputPath = outputUri.toString(),
                measuredLufs = measuredLufs,
                targetLufs = targetLufs,
                appliedGainDb = appliedGain,
                durationMs = durationMs,
            )

        } catch (e: Exception) {
            lastResult = NormalizeResult.Failure(e)
            throw e
        } finally {
            OutputFileManager.cleanupTempFiles(tempAudioFile, tempMuxedFile)
        }
    }

    override suspend fun awaitResult(): NormalizeResult = lastResult

    // ─────────────────────────────────────────────────────────────────────────

    private fun muxVideoAndAudio(
        context: Context,
        inputPath: String,
        normalizedAudioPath: String,
        outputPath: String,
    ) {
        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        // Add video track (direct format copy from source)
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(inputPath)
        val videoTrackIdx = findVideoTrack(videoExtractor)
        val muxerVideoTrack = videoExtractor.getTrackFormat(videoTrackIdx).let { muxer.addTrack(it) }

        // Add audio track
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(normalizedAudioPath)
        // ADTS AAC format
        val audioFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            audioExtractor.getTrackFormat(0).getInteger(MediaFormat.KEY_SAMPLE_RATE),
            audioExtractor.getTrackFormat(0).getInteger(MediaFormat.KEY_CHANNEL_COUNT),
        )
        val muxerAudioTrack = muxer.addTrack(audioFormat)

        muxer.start()

        // Copy video
        videoExtractor.selectTrack(videoTrackIdx)
        val buf = java.nio.ByteBuffer.allocate(1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buf.clear()
            val sz = videoExtractor.readSampleData(buf, 0)
            if (sz < 0) break
            info.offset = 0; info.size = sz
            info.presentationTimeUs = videoExtractor.sampleTime
            info.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(muxerVideoTrack, buf, info)
            videoExtractor.advance()
        }

        // Copy normalized audio (raw ADTS frames, skip header)
        audioExtractor.selectTrack(0)
        while (true) {
            buf.clear()
            val sz = audioExtractor.readSampleData(buf, 0)
            if (sz < 0) break
            info.offset = 0; info.size = sz
            info.presentationTimeUs = audioExtractor.sampleTime
            info.flags = audioExtractor.sampleFlags
            muxer.writeSampleData(muxerAudioTrack, buf, info)
            audioExtractor.advance()
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        audioExtractor.release()
    }

    private fun findVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) return i
        }
        error("No video track found")
    }

    private fun getVideoDurationMs(path: String): Long {
        val ext = MediaExtractor()
        ext.setDataSource(path)
        val dur = if (ext.trackCount > 0) {
            ext.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION) / 1000L
        } else 0L
        ext.release()
        return dur
    }
}
