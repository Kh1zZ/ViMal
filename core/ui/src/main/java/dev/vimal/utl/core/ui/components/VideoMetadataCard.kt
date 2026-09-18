package dev.vimal.utl.core.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwitchVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import dev.vimal.utl.core.domain.model.VideoInfo
import kotlin.math.roundToInt

/**
 * Displays video metadata preview after a file has been selected.
 * Shows thumbnail (via Coil VideoFrameDecoder), file name, duration, resolution, size, codec.
 * Includes a compact "Change Video" button as required by PRD §4.1.
 */
@Composable
fun VideoMetadataCard(
    videoInfo: VideoInfo,
    onChangeVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(videoInfo.uri) {
        ImageRequest.Builder(context)
            .data(videoInfo.uri)
            .videoFrameMillis(1000L)
            .crossfade(true)
            .build()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .height(80.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Video thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = videoInfo.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                MetaRow(
                    label = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.meta_duration),
                    value = if (videoInfo.durationMs > 0) formatDuration(videoInfo.durationMs) else "-"
                )
                MetaRow(
                    label = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.meta_resolution),
                    value = if (videoInfo.width > 0 && videoInfo.height > 0) "${videoInfo.width}×${videoInfo.height}" else "-"
                )
                MetaRow(
                    label = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.meta_size),
                    value = if (videoInfo.fileSizeBytes > 0) formatFileSize(videoInfo.fileSizeBytes) else "-"
                )
                if (videoInfo.measuredLufs != null) {
                    MetaRow(
                        label = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.meta_loudness),
                        value = "${videoInfo.measuredLufs} LUFS"
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Compact change video button
            TextButton(
                onClick = onChangeVideo,
                modifier = Modifier.align(Alignment.Top),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwitchVideo,
                    contentDescription = "Change video",
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(dev.vimal.utl.core.ui.R.string.btn_change_video),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
