package dev.vimal.utl.core.domain.model

/**
 * Target resolution preset for WhatsApp Story video compression.
 * [shortDimension] is the target size of the shorter edge (maintaining aspect ratio).
 * [targetBitrateKbps] is the optimized video bitrate for WhatsApp Story without heavy recompression.
 */
enum class VideoResolutionPreset(
    val label: String,
    val shortDimension: Int,
    val targetBitrateKbps: Int,
) {
    P720("720p HD", 720, 2000),
    P540("540p Compact", 540, 1200);
}

/** Metadata extracted from the input video file. */
data class VideoInfo(
    val uri: android.net.Uri,
    val fileName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val mimeType: String,
    val videoCodec: String?,
    val audioCodec: String?,
    val measuredLufs: Float?,  // null until analysis is run
) {
    /**
     * Calculates the target resolution (width, height) preserving aspect ratio.
     * Guarantees both dimensions are even numbers (required by H.264).
     * Does not upscale if original video is already smaller than [preset].
     */
    fun getTargetResolution(preset: VideoResolutionPreset): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val targetShort = preset.shortDimension

        val (rawW, rawH) = if (width <= height) {
            // Portrait or square: width is shorter edge
            if (width > targetShort) {
                val scaledH = (targetShort.toDouble() * height / width).toInt()
                targetShort to scaledH
            } else {
                width to height
            }
        } else {
            // Landscape: height is shorter edge
            if (height > targetShort) {
                val scaledW = (targetShort.toDouble() * width / height).toInt()
                scaledW to targetShort
            } else {
                width to height
            }
        }

        val finalW = if (rawW % 2 != 0) rawW + 1 else rawW
        val finalH = if (rawH % 2 != 0) rawH + 1 else rawH
        return finalW to finalH
    }

    /** Returns true if downscaling is needed for the given [preset]. */
    fun isCompressionNeeded(preset: VideoResolutionPreset): Boolean {
        if (width <= 0 || height <= 0) return false
        val shortEdge = minOf(width, height)
        return shortEdge > preset.shortDimension
    }

    /**
     * Estimates output file size in bytes based on [preset] video bitrate + 128 kbps audio.
     */
    fun getEstimatedSizeBytes(preset: VideoResolutionPreset): Long {
        if (durationMs <= 0L) return fileSizeBytes
        val totalBitrateBps = (preset.targetBitrateKbps + 128) * 1000L / 8L
        val estimated = (durationMs / 1000.0 * totalBitrateBps).toLong()
        return if (fileSizeBytes > 0 && estimated > fileSizeBytes && !isCompressionNeeded(preset)) {
            fileSizeBytes
        } else {
            estimated
        }
    }
}

/** The result of a completed normalization job. */
sealed class NormalizeResult {
    data class Success(
        val outputUri: android.net.Uri,
        val outputPath: String,
        val measuredLufs: Float,
        val targetLufs: Float,
        val appliedGainDb: Float,
        val durationMs: Long,
    ) : NormalizeResult()

    data class Failure(val error: Throwable) : NormalizeResult()
}

/** Step-by-step progress emitted during normalization. */
sealed class NormalizeProgress {
    data object Analyzing : NormalizeProgress()
    data class Processing(val percent: Float, val stage: Stage) : NormalizeProgress()
    data object Muxing : NormalizeProgress()
    data class Completed(val result: NormalizeResult.Success) : NormalizeProgress()

    enum class Stage { DEMUXING, MEASURING_LUFS, ENCODING_AUDIO, COMPRESSING_VIDEO, MUXING }
}
