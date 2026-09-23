package dev.vimal.utl.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vimal.utl.core.domain.model.NormalizeProgress

/**
 * Progress indicator shown during normalization processing.
 * Shows animated linear progress bar + current stage label.
 */
@Composable
fun NormalizerProgressBar(
    progress: NormalizeProgress,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val result: Pair<Float, String> = when (progress) {
            is NormalizeProgress.Analyzing -> 0.05f to "Analyzing audio…"
            is NormalizeProgress.Processing -> progress.percent to when (progress.stage) {
                NormalizeProgress.Stage.DEMUXING -> "Separating tracks…"
                NormalizeProgress.Stage.MEASURING_LUFS -> "Measuring loudness…"
                NormalizeProgress.Stage.ENCODING_AUDIO -> "Encoding normalized audio…"
                NormalizeProgress.Stage.COMPRESSING_VIDEO -> "Compressing video & normalizing audio…"
                NormalizeProgress.Stage.MUXING -> "Muxing output…"
            }
            is NormalizeProgress.Muxing -> 0.95f to "Finalizing…"
            is NormalizeProgress.Completed -> 1.0f to "Done!"
        }
        val (fraction, stageLabel) = result

        val animatedProgress by animateFloatAsState(
            targetValue = fraction,
            animationSpec = tween(300),
            label = "progress_anim",
        )

        Text(
            text = stageLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = "${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
