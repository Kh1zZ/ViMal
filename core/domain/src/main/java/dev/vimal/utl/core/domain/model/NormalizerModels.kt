package dev.vimal.utl.core.domain.model

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
)

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
    data object Completed : NormalizeProgress()

    enum class Stage { DEMUXING, MEASURING_LUFS, ENCODING_AUDIO, MUXING }
}
