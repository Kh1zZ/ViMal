package dev.vimal.utl.feature.normalizer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.NormalizeResult
import dev.vimal.utl.core.domain.usecase.GetVideoInfoUseCase
import dev.vimal.utl.core.domain.usecase.NormalizeVideoUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class NormalizerViewModel(
    private val getVideoInfoUseCase: GetVideoInfoUseCase,
    private val normalizeVideoUseCase: NormalizeVideoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<NormalizerUiState>(NormalizerUiState.Empty)
    val uiState: StateFlow<NormalizerUiState> = _uiState.asStateFlow()

    private val _events = Channel<NormalizerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ── Public actions ────────────────────────────────────────────────────────

    fun onVideoSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val source = MediaSource.LocalUri(uri)
                val info = getVideoInfoUseCase(context, source)
                _uiState.value = NormalizerUiState.VideoLoaded(videoInfo = info)
            } catch (e: Exception) {
                _uiState.value = NormalizerUiState.Error("Failed to read video: ${e.localizedMessage}")
            }
        }
    }

    fun onPresetSelected(preset: LoudnessPreset) {
        val current = _uiState.value as? NormalizerUiState.VideoLoaded ?: return
        _uiState.value = current.copy(selectedPreset = preset)
    }

    fun onCustomLufsChanged(lufs: Float?) {
        val current = _uiState.value as? NormalizerUiState.VideoLoaded ?: return
        _uiState.value = current.copy(customLufs = lufs)
    }

    fun onChangeVideo() {
        _uiState.value = NormalizerUiState.Empty
    }

    fun onStartNormalization(context: Context) {
        val current = _uiState.value as? NormalizerUiState.VideoLoaded ?: return
        val source = MediaSource.LocalUri(current.videoInfo.uri)

        viewModelScope.launch {
            normalizeVideoUseCase(
                context = context,
                source = source,
                preset = current.selectedPreset,
                customTargetLufs = current.customLufs,
            )
                .catch { e ->
                    _uiState.value = NormalizerUiState.Error(e.localizedMessage ?: "Unknown error")
                }
                .collect { progress ->
                    _uiState.value = NormalizerUiState.Processing(
                        videoInfo = current.videoInfo,
                        progress = progress,
                    )
                }

            // Flow completed — fetch result
            // (In production, repository returns the result after flow completes)
            // For now, transition to Done with a placeholder
            // The repository's awaitResult() is called here in a real wiring
            handleNormalizationComplete(current)
        }
    }

    fun onErrorDismissed() {
        _uiState.value = NormalizerUiState.Empty
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun handleNormalizationComplete(previous: NormalizerUiState.VideoLoaded) {
        // Emit completion sound event
        _events.send(NormalizerEvent.PlayCompletionSound)

        // Auto-transition back to VideoLoaded with the output video
        // (In a full wiring, we reload the output URI as the new VideoInfo)
        _uiState.value = previous.copy(
            videoInfo = previous.videoInfo.copy(measuredLufs = null),
        )
    }
}
