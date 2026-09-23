package dev.vimal.utl.feature.normalizer

import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vimal.utl.core.ui.components.DashedDropZone
import dev.vimal.utl.core.ui.components.NormalizerProgressBar
import dev.vimal.utl.core.ui.components.PresetSelector
import dev.vimal.utl.core.ui.components.PressableButton
import dev.vimal.utl.core.ui.components.VideoMetadataCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalizerScreen(
    viewModel: NormalizerViewModel,
    isDarkTheme: Boolean,
    currentLanguage: String = "id",
    onToggleTheme: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.onVideoSelected(context, it) }
    }

    // Collect one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NormalizerEvent.PlayCompletionSound -> {
                    // Play a short system notification tone
                    MediaPlayer.create(context, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                        ?.apply { start(); setOnCompletionListener { release() } }
                }
                is NormalizerEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ViMal",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                navigationIcon = {
                    // Light/dark toggle — top left
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    // Language toggle — clear text button EN / ID
                    androidx.compose.material3.OutlinedButton(
                        onClick = onToggleLanguage,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (currentLanguage.equals("id", ignoreCase = true) || currentLanguage.equals("in", ignoreCase = true)) "ID" else "EN",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 8 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 8 })
            },
            contentKey = { state ->
                // Crucial: Group states so progress updates do NOT trigger slide/fade animations!
                when (state) {
                    is NormalizerUiState.Empty -> 0
                    is NormalizerUiState.VideoLoaded -> 1
                    is NormalizerUiState.Processing -> 2
                    is NormalizerUiState.Done -> 3
                    is NormalizerUiState.Error -> 4
                }
            },
            label = "state_transition",
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { state ->
            when (state) {
                is NormalizerUiState.Empty -> {
                    EmptyStateContent(onPickVideo = { videoPickerLauncher.launch("video/*") })
                }

                is NormalizerUiState.VideoLoaded -> {
                    VideoLoadedContent(
                        state = state,
                        onChangeVideo = { videoPickerLauncher.launch("video/*") },
                        onPresetSelected = viewModel::onPresetSelected,
                        onResolutionSelected = viewModel::onResolutionSelected,
                        onCustomLufsChanged = viewModel::onCustomLufsChanged,
                        onStart = { viewModel.onStartNormalization(context) },
                    )
                }

                is NormalizerUiState.Processing -> {
                    ProcessingContent(state = state)
                }

                is NormalizerUiState.Done -> {
                    DoneContent(
                        state = state,
                        onNormalizeAnother = viewModel::onNormalizeAnother,
                    )
                }

                is NormalizerUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onDismiss = viewModel::onErrorDismissed,
                    )
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun EmptyStateContent(onPickVideo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.title_normalize),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.subtitle_normalize),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        DashedDropZone(
            onPickVideo = onPickVideo,
            label = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.label_pick_video),
            sublabel = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.label_supported_formats),
        )
    }
}

@Composable
private fun VideoLoadedContent(
    state: NormalizerUiState.VideoLoaded,
    onChangeVideo: () -> Unit,
    onPresetSelected: (dev.vimal.utl.core.domain.model.LoudnessPreset) -> Unit,
    onResolutionSelected: (dev.vimal.utl.core.domain.model.VideoResolutionPreset) -> Unit,
    onCustomLufsChanged: (Float?) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        VideoMetadataCard(
            videoInfo = state.videoInfo,
            selectedResolution = state.selectedResolution,
            onResolutionSelected = onResolutionSelected,
            onChangeVideo = onChangeVideo,
        )

        PresetSelector(
            selectedPreset = state.selectedPreset,
            customLufs = state.customLufs,
            onPresetSelected = onPresetSelected,
            onCustomLufsChanged = onCustomLufsChanged,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Start button at bottom
        PressableButton(
            text = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.btn_start),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ProcessingContent(state: NormalizerUiState.Processing) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Processing…",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))
        NormalizerProgressBar(
            progress = state.progress,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DoneContent(
    state: NormalizerUiState.Done,
    onNormalizeAnother: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓ Done!", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Gain applied: +${"%.1f".format(state.appliedGainDb)} dB",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${"%.1f".format(state.measuredLufs)} LUFS → ${"%.1f".format(state.targetLufs)} LUFS",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.msg_saved_to_gallery),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
        PressableButton(
            text = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.btn_normalize_another),
            onClick = onNormalizeAnother,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        PressableButton(
            text = "Try Again",
            onClick = onDismiss,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
