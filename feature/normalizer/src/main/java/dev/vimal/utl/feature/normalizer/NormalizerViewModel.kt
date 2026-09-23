package dev.vimal.utl.feature.normalizer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vimal.utl.core.domain.model.LoudnessPreset
import dev.vimal.utl.core.domain.model.MediaSource
import dev.vimal.utl.core.domain.model.NormalizeProgress
import dev.vimal.utl.core.domain.model.NormalizeResult
import dev.vimal.utl.core.domain.model.VideoResolutionPreset
import dev.vimal.utl.core.domain.usecase.GetVideoInfoUseCase
import dev.vimal.utl.core.domain.usecase.NormalizeVideoUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
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

    fun onResolutionSelected(preset: VideoResolutionPreset) {
        val current = _uiState.value as? NormalizerUiState.VideoLoaded ?: return
        _uiState.value = current.copy(selectedResolution = preset)
    }

    fun onCustomLufsChanged(lufs: Float?) {
        val current = _uiState.value as? NormalizerUiState.VideoLoaded ?: return
        _uiState.value = current.copy(customLufs = lufs)
    }

    fun onChangeVideo() {
        _uiState.value = NormalizerUiState.Empty
    }

    fun onNormalizeAnother() {
        _uiState.value = NormalizerUiState.Empty
    }

    fun onStartNormalization(context: Context) {
        val current = _uiState.value as? NormalizerUiState.VideoLoaded ?: return
        val source = MediaSource.LocalUri(current.videoInfo.uri)

        viewModelScope.launch {
            normalizeVideoUseCase(
                context           = context,
                source            = source,
                preset            = current.selectedPreset,
                customTargetLufs  = current.customLufs,
                resolutionPreset  = current.selectedResolution,
            )
                .catch { e ->
                    _uiState.value = NormalizerUiState.Error(e.localizedMessage ?: "Unknown error")
                }
                .collect { progress ->
                    when (progress) {
                        is NormalizeProgress.Completed -> {
                            val result = progress.result
                            _uiState.value = NormalizerUiState.Done(
                                outputUri     = result.outputUri,
                                measuredLufs  = result.measuredLufs,
                                targetLufs    = result.targetLufs,
                                appliedGainDb = result.appliedGainDb,
                            )
                            _events.send(NormalizerEvent.PlayCompletionSound)
                        }
                        else -> {
                            _uiState.value = NormalizerUiState.Processing(
                                videoInfo = current.videoInfo,
                                progress  = progress,
                            )
                        }
                    }
                }
        }
    }

    fun onErrorDismissed() {
        _uiState.value = NormalizerUiState.Empty
    }
}
