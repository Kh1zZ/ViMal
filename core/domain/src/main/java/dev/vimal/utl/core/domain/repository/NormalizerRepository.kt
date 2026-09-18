package dev.vimal.utl.core.domain.repository

import android.content.Context
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.NormalizeResult
import dev.vimal.utl.core.domain.model.VideoInfo
import kotlinx.coroutines.flow.Flow

/**
 * Contract for the normalization pipeline.
 * Implemented in :core:data — ViewModel and UseCase depend only on this interface.
 */
interface NormalizerRepository {

    /** Extract metadata from the given video source. */
    suspend fun getVideoInfo(context: Context, source: MediaSource): VideoInfo

    /**
     * Run the full normalization pipeline:
     * demux → measure LUFS → apply gain → re-encode audio → mux.
     * Video stream is always stream-copied (bit-for-bit) in MVP.
     *
     * @param customTargetLufs overrides [preset].targetLufs if [preset] is [LoudnessPreset.CUSTOM].
     * @return Flow<NormalizeProgress> that terminates with [NormalizeProgress.Muxing] on success.
     *         Collect [NormalizeResult] via [awaitResult] or listen for completion in the ViewModel.
     */
    fun normalize(
        context: Context,
        source: MediaSource,
        preset: LoudnessPreset,
        customTargetLufs: Float? = null,
    ): Flow<NormalizeProgress>

    /** Suspend until a normalize Flow completes and return the final result. */
    suspend fun awaitResult(): NormalizeResult
}
