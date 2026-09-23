package dev.vimal.utl.core.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Session
import dev.vimal.utl.core.data.audio.AudioNormalizer
import dev.vimal.utl.core.data.output.OutputFileManager
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.NormalizeResult
import dev.vimal.utl.core.domain.model.VideoInfo
import dev.vimal.utl.core.domain.model.VideoResolutionPreset
import dev.vimal.utl.core.domain.repository.NormalizerRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Concrete implementation of [NormalizerRepository].
 *
 * Unified Pipeline:
 *   Pass 1  → Decode audio track → measure integrated LUFS (EBU R128 / ITU-R BS.1770-4)
 *   Pass 2  → Transcode video & audio simultaneously via FFmpegKit:
 *             - Video: Scale to target resolution (720p HD or 540p Compact) if larger,
 *               preserving exact aspect ratio and original FPS (including 60 FPS!).
 *               Encode with libx264 (CRF 23, WhatsApp sweet-spot bitrate cap, yuv420p).
 *             - Audio: Apply calibrated EBU R128 gain (-af volume=XdB) + encode AAC 128k.
 *             - Container: MP4 with +faststart for instant status playback.
 *
 * Audio and video are never split into separate files.
 */
class MediaNormalizerRepository : NormalizerRepository {

    // ── Metadata ──────────────────────────────────────────────────────────────

    override suspend fun getVideoInfo(context: Context, source: MediaSource): VideoInfo =
        withContext(Dispatchers.IO) {
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
                        val durStr  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val wStr    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val hStr    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        val rotStr  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                        durationMs = durStr?.toLongOrNull() ?: 0L
                        val w = wStr?.toIntOrNull() ?: 0
                        val h = hStr?.toIntOrNull() ?: 0
                        val rotation = rotStr?.toIntOrNull() ?: 0

                        if (rotation == 90 || rotation == 270) { width = h; height = w }
                        else { width = w; height = h }
                        if (!mimeStr.isNullOrBlank()) mimeType = mimeStr
                    }
                } else {
                    retriever.setDataSource(context, uri)
                    val durStr  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val wStr    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    val hStr    = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    val rotStr  = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                    durationMs = durStr?.toLongOrNull() ?: 0L
                    val w = wStr?.toIntOrNull() ?: 0
                    val h = hStr?.toIntOrNull() ?: 0
                    val rotation = rotStr?.toIntOrNull() ?: 0

                    if (rotation == 90 || rotation == 270) { width = h; height = w }
                    else { width = w; height = h }
                    if (!mimeStr.isNullOrBlank()) mimeType = mimeStr
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }

            val fileSizeBytes = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            } catch (_: Exception) { 0L }

            val fileName = try {
                context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: uri.lastPathSegment ?: "video.mp4"
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

    // ── Normalization & Video Compression ─────────────────────────────────────

    override fun normalize(
        context: Context,
        source: MediaSource,
        preset: LoudnessPreset,
        customTargetLufs: Float?,
        resolutionPreset: VideoResolutionPreset,
    ): Flow<NormalizeProgress> = channelFlow {
        val targetLufs = if (preset == LoudnessPreset.CUSTOM) customTargetLufs!! else preset.targetLufs
        val uri = (source as MediaSource.LocalUri).uri
        val tempOutputFile = OutputFileManager.createTempFile(context, "_compressed.mp4")

        try {
            val videoInfo = getVideoInfo(context, source)

            // ── Pass 1: Decode audio → measure integrated LUFS ────────────────
            send(NormalizeProgress.Analyzing)

            val pfd1 = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open file descriptor for URI: $uri")

            val audioData = pfd1.use { parcelFd ->
                AudioNormalizer.analyze(
                    fd = parcelFd.fileDescriptor,
                    onProgress = { p ->
                        trySend(NormalizeProgress.Processing(p * 0.1f, NormalizeProgress.Stage.MEASURING_LUFS))
                    },
                )
            }

            val measuredLufs = audioData.measuredLufs
            val gainDb = (targetLufs - measuredLufs).coerceIn(-40f, 40f)

            send(NormalizeProgress.Processing(0.1f, NormalizeProgress.Stage.COMPRESSING_VIDEO))

            // ── Pass 2: Unified Video Compression + Audio Normalization ───────
            val inputPath = FFmpegKitConfig.getSafParameterForRead(context, uri)
            val outputPath = tempOutputFile.absolutePath

            // Calculate scaling parameters preserving aspect ratio (always even dimensions)
            val (targetW, targetH) = videoInfo.getTargetResolution(resolutionPreset)
            val scaleFilter = if (videoInfo.isCompressionNeeded(resolutionPreset) && targetW > 0 && targetH > 0) {
                "scale=${targetW}:${targetH}"
            } else {
                "scale=trunc(iw/2)*2:trunc(ih/2)*2"
            }

            val maxBitrate = "${resolutionPreset.targetBitrateKbps}k"
            val bufSize = "${resolutionPreset.targetBitrateKbps * 2}k"
            val gainFormatted = String.format(Locale.US, "%.2f", gainDb)

            // FFmpeg command:
            // - scale video to target resolution maintaining aspect ratio (or keep even dimensions)
            // - libx264 with CRF 23 and VBV cap for WhatsApp sweet-spot
            // - keep original FPS (including 60 FPS!)
            // - apply gain to audio and re-encode to 128k AAC
            // - moov atom at beginning (+faststart)
            val ffmpegCommand = buildString {
                append("-y ")
                append("-i \"$inputPath\" ")
                append("-vf \"$scaleFilter\" ")
                append("-c:v libx264 -preset medium -crf 23 -maxrate $maxBitrate -bufsize $bufSize -pix_fmt yuv420p ")
                append("-af \"volume=${gainFormatted}dB\" -c:a aac -b:a 128k ")
                append("-movflags +faststart ")
                append("\"$outputPath\"")
            }

            val deferred = CompletableDeferred<Session>()
            val session = FFmpegKit.executeAsync(
                ffmpegCommand,
                { completedSession ->
                    deferred.complete(completedSession)
                },
                { /* log */ },
                { stats ->
                    val durationMs = videoInfo.durationMs
                    if (durationMs > 0) {
                        val progressFraction = (stats.time.toFloat() / durationMs).coerceIn(0f, 1f)
                        trySend(
                            NormalizeProgress.Processing(
                                0.1f + progressFraction * 0.85f,
                                NormalizeProgress.Stage.COMPRESSING_VIDEO,
                            )
                        )
                    }
                }
            )

            try {
                val completedSession = deferred.await()
                if (!ReturnCode.isSuccess(completedSession.returnCode)) {
                    val errorLog = completedSession.allLogsAsString
                    error("Video compression failed (code ${completedSession.returnCode}): $errorLog")
                }
            } catch (e: Exception) {
                FFmpegKit.cancel(session.sessionId)
                throw e
            }

            send(NormalizeProgress.Muxing)

            // ── Publish to MediaStore Movies/ViMal ────────────────────────────
            val fileName = videoInfo.fileName
            val outputUri = OutputFileManager.publishToMediaStore(context, fileName, tempOutputFile)
            val durationMs = if (videoInfo.durationMs > 0) videoInfo.durationMs else (audioData.durationUs / 1000L)

            val result = NormalizeResult.Success(
                outputUri = outputUri,
                outputPath = outputUri.toString(),
                measuredLufs = measuredLufs,
                targetLufs = targetLufs,
                appliedGainDb = gainDb,
                durationMs = durationMs,
            )

            send(NormalizeProgress.Completed(result))

        } catch (e: Exception) {
            throw e
        } finally {
            OutputFileManager.cleanupTempFiles(tempOutputFile)
        }
    }
}
