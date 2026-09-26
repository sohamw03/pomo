package com.pomo.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomo.app.R
import com.pomo.app.model.TimerMode
import com.pomo.app.model.TimerUiState
import com.pomo.app.ui.components.CustomDurationPanel
import com.pomo.app.ui.components.PresetChips
import com.pomo.app.ui.components.TimerRing
import com.pomo.app.viewmodel.TimerViewModel

@Composable
fun HomeScreen(
    viewModel: TimerViewModel,
    uiState: TimerUiState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Center: mode toggle above the ring, like the web's desktop mode
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                ModeToggle(
                    mode = uiState.mode,
                    onModeChange = { viewModel.setMode(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                TimerRing(
                    mode = uiState.mode,
                    timeFormatted = uiState.timeFormatted,
                    progress = uiState.progress,
                    isRunning = uiState.isRunning
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Play / Pause / Reset / Skip Controls
                TimerControlsSection(
                    isRunning = uiState.isRunning,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onReset = { viewModel.resetTimer() },
                    onSkip = { viewModel.skipTimer() }
                )
            }

            // Bottom Section: Presets & Custom Duration Panel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                PresetChips(
                    selectedPreset = uiState.preset,
                    customWork = uiState.customWorkMinutes,
                    customBreak = uiState.customBreakMinutes,
                    onSelectPreset = { viewModel.setPreset(it) }
                )

                CustomDurationPanel(
                    isExpanded = uiState.isCustomExpanded,
                    customWork = uiState.customWorkMinutes,
                    customBreak = uiState.customBreakMinutes,
                    onWorkChange = { viewModel.setCustomDurations(it, uiState.customBreakMinutes) },
                    onBreakChange = { viewModel.setCustomDurations(uiState.customWorkMinutes, it) },
                    onWorkAdjust = { viewModel.adjustCustomWork(it) },
                    onBreakAdjust = { viewModel.adjustCustomBreak(it) }
                )
            }
        }
    }
}

@Composable
private fun ModeToggle(
    mode: TimerMode,
    onModeChange: (TimerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mode toggle: plain track pill with two ripple pills inside, matching
        // the web's custom toggle (no M3 segmented control, no outline).
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModePill(
                    selected = mode == TimerMode.WORK,
                    label = stringResource(R.string.work_mode),
                    onClick = { onModeChange(TimerMode.WORK) }
                )
                ModePill(
                    selected = mode == TimerMode.BREAK,
                    label = stringResource(R.string.break_mode),
                    onClick = { onModeChange(TimerMode.BREAK) }
                )
            }
        }
    }
}

@Composable
private fun ModePill(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun TimerControlsSection(
    isRunning: Boolean,
    onTogglePlayPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reset Button
        Surface(
            onClick = onReset,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 2.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.RotateLeft,
                    contentDescription = stringResource(R.string.reset_timer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Hero Play / Pause Button
        Surface(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp,
            modifier = Modifier.size(96.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = isRunning,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                            scaleIn(spring(stiffness = Spring.StiffnessMedium))) togetherWith
                            (fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                                scaleOut(spring(stiffness = Spring.StiffnessMedium)))
                    },
                    label = "playPauseIcon"
                ) { running ->
                    Icon(
                        imageVector = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (running) R.string.pause_timer else R.string.play_timer
                        ),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Skip Button
        Surface(
            onClick = onSkip,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 2.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.skip_phase),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
