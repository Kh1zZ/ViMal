package dev.vimal.utl.core.domain.repository

import android.content.Context
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.VideoInfo
import dev.vimal.utl.core.domain.model.VideoResolutionPreset
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the normalization and compression pipeline.
 * Implemented in :core:data — ViewModel and UseCase depend only on this interface.
 */
interface NormalizerRepository {

    /** Extract metadata from the given video source. */
    suspend fun getVideoInfo(context: Context, source: MediaSource): VideoInfo

    /**
     * Run the full normalization & compression pipeline (unified — audio and video processed together):
     *   Pass 1: decode audio → measure integrated LUFS (EBU R128 / ITU-R BS.1770-4)
     *   Pass 2: compress video (H.264 at [resolutionPreset], 60 FPS preserved) + apply audio gain.
     *
     * The flow terminates with [NormalizeProgress.Completed] which carries the
     * [dev.vimal.utl.core.domain.model.NormalizeResult.Success] directly.
     *
     * @param customTargetLufs overrides [preset].targetLufs when [preset] is [LoudnessPreset.CUSTOM].
     * @param resolutionPreset target resolution for WhatsApp Story video compression.
     */
    fun normalize(
        context: Context,
        source: MediaSource,
        preset: LoudnessPreset,
        customTargetLufs: Float? = null,
        resolutionPreset: VideoResolutionPreset = VideoResolutionPreset.P720,
    ): Flow<NormalizeProgress>
}
