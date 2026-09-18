package dev.vimal.utl.core.domain.strategy

import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.NormalizeResult
import kotlinx.coroutines.flow.Flow

/**
 * Strategy contract for video track processing.
 *
 * Pipeline: demux → [VideoProcessingStrategy] → mux
 * The strategy is responsible only for the VIDEO track.
 * Audio is always handled separately by AudioNormalizer.
 *
 * MVP: CopyStrategy (bit-for-bit stream copy — no re-encode).
 * Future: TranscodeStrategy (decode → resize/compress → encode via MediaCodec/FFmpeg-Kit).
 */
interface VideoProcessingStrategy {
    /**
     * Process the video track from [source] and write to [tempVideoPath].
     * @return Flow of progress updates; completes when video track is written.
     */
    fun process(
        source: MediaSource,
        tempVideoPath: String,
    ): Flow<NormalizeProgress>
}
