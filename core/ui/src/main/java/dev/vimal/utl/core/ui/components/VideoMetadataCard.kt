package dev.vimal.utl.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwitchVideo
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import dev.vimal.utl.core.domain.model.VideoInfo
import dev.vimal.utl.core.domain.model.VideoResolutionPreset

/**
 * Displays video metadata preview after a file has been selected,
 * along with WhatsApp Story resolution selection and dynamic size estimation.
 */
@Composable
fun VideoMetadataCard(
    videoInfo: VideoInfo,
    selectedResolution: VideoResolutionPreset = VideoResolutionPreset.P720,
    onResolutionSelected: (VideoResolutionPreset) -> Unit = {},
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

    val (targetW, targetH) = videoInfo.getTargetResolution(selectedResolution)
    val estimatedBytes = videoInfo.getEstimatedSizeBytes(selectedResolution)
    val savingsPercent = if (videoInfo.fileSizeBytes > 0 && videoInfo.fileSizeBytes > estimatedBytes) {
        (((videoInfo.fileSizeBytes - estimatedBytes).toDouble() / videoInfo.fileSizeBytes) * 100).toInt()
    } else 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: Video File Name + Change Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = videoInfo.fileName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Compact Change Button
                TextButton(
                    onClick = onChangeVideo,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwitchVideo,
                        contentDescription = "Change video",
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(dev.vimal.utl.core.ui.R.string.btn_change_video),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            // Preview & Details Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Video thumbnail (16:9 ratio)
                Box(
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
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

                Spacer(modifier = Modifier.width(14.dp))

                // Metadata Key-Value specs
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MetaRow(
                        label = stringResource(dev.vimal.utl.core.ui.R.string.meta_duration),
                        value = if (videoInfo.durationMs > 0) formatDuration(videoInfo.durationMs) else "-"
                    )
                    MetaRow(
                        label = stringResource(dev.vimal.utl.core.ui.R.string.meta_resolution),
                        value = if (videoInfo.width > 0 && videoInfo.height > 0) "${videoInfo.width}×${videoInfo.height}" else "-"
                    )
                    MetaRow(
                        label = stringResource(dev.vimal.utl.core.ui.R.string.meta_size),
                        value = if (videoInfo.fileSizeBytes > 0) formatFileSize(videoInfo.fileSizeBytes) else "-"
                    )
                    if (videoInfo.measuredLufs != null) {
                        MetaRow(
                            label = stringResource(dev.vimal.utl.core.ui.R.string.meta_loudness),
                            value = "${videoInfo.measuredLufs} LUFS"
                        )
                    }
                }
            }

            // ── Compression & Resolution Selection Section ────────────────────
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(dev.vimal.utl.core.ui.R.string.label_story_compression),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "60 FPS Preserved",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Preset selector buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ResolutionChip(
                        title = "720p HD",
                        subtitle = stringResource(dev.vimal.utl.core.ui.R.string.desc_preset_720),
                        isSelected = selectedResolution == VideoResolutionPreset.P720,
                        onClick = { onResolutionSelected(VideoResolutionPreset.P720) },
                        modifier = Modifier.weight(1f),
                    )

                    ResolutionChip(
                        title = "540p Compact",
                        subtitle = stringResource(dev.vimal.utl.core.ui.R.string.desc_preset_540),
                        isSelected = selectedResolution == VideoResolutionPreset.P540,
                        onClick = { onResolutionSelected(VideoResolutionPreset.P540) },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Comparison info & Savings badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${stringResource(dev.vimal.utl.core.ui.R.string.meta_target_resolution)}: ${if (targetW > 0) "${targetW}×${targetH}" else "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${stringResource(dev.vimal.utl.core.ui.R.string.meta_estimated_size)}: ~${formatFileSize(estimatedBytes)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    if (savingsPercent > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "Hemat ~$savingsPercent% ⚡",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
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
