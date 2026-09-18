package dev.vimal.utl.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vimal.utl.core.domain.model.LoudnessPreset

/**
 * Horizontal scrollable preset chip selector.
 * Shows all platform presets + Custom option that expands a LUFS text field.
 */
@Composable
fun PresetSelector(
    selectedPreset: LoudnessPreset,
    customLufs: Float?,
    onPresetSelected: (LoudnessPreset) -> Unit,
    onCustomLufsChanged: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Normalization Target",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            // Platform presets
            items(LoudnessPreset.platformPresets) { preset ->
                PresetChip(
                    label = preset.label,
                    lufsLabel = "${preset.targetLufs.toInt()} LUFS",
                    selected = selectedPreset == preset,
                    onClick = { onPresetSelected(preset) },
                )
            }
            // Custom chip
            item {
                PresetChip(
                    label = "Custom",
                    lufsLabel = customLufs?.let { "${it.toInt()} LUFS" } ?: "Enter LUFS",
                    selected = selectedPreset == LoudnessPreset.CUSTOM,
                    onClick = { onPresetSelected(LoudnessPreset.CUSTOM) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }

        // Custom LUFS input field — animated expand/collapse
        AnimatedVisibility(
            visible = selectedPreset == LoudnessPreset.CUSTOM,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            var textValue by remember { mutableStateOf(customLufs?.toString() ?: "") }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = { raw ->
                    textValue = raw
                    raw.toFloatOrNull()?.let { onCustomLufsChanged(it) }
                },
                label = { Text("Target LUFS (e.g. -14)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Recommended: −14 to −16 LUFS", style = MaterialTheme.typography.bodySmall)
                },
            )
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    lufsLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.invoke()
                    if (icon != null) Spacer(modifier = Modifier.width(4.dp))
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lufsLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
