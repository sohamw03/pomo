package com.pomo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pomo.app.R
import com.pomo.app.model.TimerPreset

@Composable
fun PresetChips(
    selectedPreset: TimerPreset,
    customWork: Int,
    customBreak: Int,
    onSelectPreset: (TimerPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally)
    ) {
        FilterChip(
            selected = selectedPreset == TimerPreset.P_25_5,
            onClick = { onSelectPreset(TimerPreset.P_25_5) },
            label = { Text(stringResource(R.string.preset_25_5)) },
            shape = MaterialTheme.shapes.large,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        FilterChip(
            selected = selectedPreset == TimerPreset.P_50_10,
            onClick = { onSelectPreset(TimerPreset.P_50_10) },
            label = { Text(stringResource(R.string.preset_50_10)) },
            shape = MaterialTheme.shapes.large,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        FilterChip(
            selected = selectedPreset == TimerPreset.CUSTOM,
            onClick = { onSelectPreset(TimerPreset.CUSTOM) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("$customWork / $customBreak") },
            shape = MaterialTheme.shapes.large,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}
