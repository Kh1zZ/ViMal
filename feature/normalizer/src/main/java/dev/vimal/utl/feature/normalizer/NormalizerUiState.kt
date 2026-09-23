package dev.vimal.utl.feature.normalizer

import android.net.Uri
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.VideoInfo

/** Complete UI state machine for the normalizer screen. */
sealed class NormalizerUiState {

    /** No video selected — show DashedDropZone. */
    data object Empty : NormalizerUiState()

    /** Video loaded — show metadata card, preset selector, Start button. */
    data class VideoLoaded(
        val videoInfo: VideoInfo,
        val selectedPreset: LoudnessPreset = LoudnessPreset.YOUTUBE,
        val customLufs: Float? = null,
        val selectedResolution: dev.vimal.utl.core.domain.model.VideoResolutionPreset = dev.vimal.utl.core.domain.model.VideoResolutionPreset.P720,
    ) : NormalizerUiState()

    /** Normalization in progress — show progress bar, hide Start. */
    data class Processing(
        val videoInfo: VideoInfo,
        val progress: NormalizeProgress,
    ) : NormalizerUiState()

    /**
     * Processing done — auto-transitions back to VideoLoaded with result video.
     * This state triggers the completion sound and then auto-pops back.
     */
    data class Done(
        val outputUri: Uri,
        val measuredLufs: Float,
        val targetLufs: Float,
        val appliedGainDb: Float,
    ) : NormalizerUiState()

    /** Unrecoverable error — show error message, reset button. */
    data class Error(val message: String) : NormalizerUiState()
}

/** One-shot UI events that cannot be modeled as state. */
sealed class NormalizerEvent {
    data object PlayCompletionSound : NormalizerEvent()
    data class ShowSnackbar(val message: String) : NormalizerEvent()
    data object NavigateToResult : NormalizerEvent()
}
