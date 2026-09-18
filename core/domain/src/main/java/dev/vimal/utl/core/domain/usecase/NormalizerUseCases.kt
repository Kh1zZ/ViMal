package dev.vimal.utl.core.domain.usecase

import android.content.Context
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.VideoInfo
import dev.vimal.utl.core.domain.repository.NormalizerRepository
import kotlinx.coroutines.flow.Flow

/**
 * Orchestrates fetching video metadata.
 * Thin wrapper — keeps ViewModel clean and enables future validation logic here.
 */
class GetVideoInfoUseCase(private val repository: NormalizerRepository) {
    suspend operator fun invoke(context: Context, source: MediaSource): VideoInfo =
        repository.getVideoInfo(context, source)
}

/**
 * Orchestrates the full audio normalization pipeline.
 * Validates inputs, delegates to repository, emits progress.
 */
class NormalizeVideoUseCase(private val repository: NormalizerRepository) {

    operator fun invoke(
        context: Context,
        source: MediaSource,
        preset: LoudnessPreset,
        customTargetLufs: Float? = null,
    ): Flow<NormalizeProgress> {
        val targetLufs = if (preset == LoudnessPreset.CUSTOM) {
            requireNotNull(customTargetLufs) { "customTargetLufs required when preset is CUSTOM" }
        } else {
            preset.targetLufs
        }
        require(targetLufs in -70f..-1f) { "Target LUFS must be between -70 and -1, got $targetLufs" }

        return repository.normalize(context, source, preset, customTargetLufs)
    }
}
