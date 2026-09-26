package com.pomo.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import com.pomo.app.ui.components.SettingsBottomSheet
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
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar
            TopBarSection(
                mode = uiState.mode,
                completedSessions = uiState.completedSessionsToday,
                onModeChange = { viewModel.setMode(it) },
                onOpenSettings = { viewModel.setSettingsOpen(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Center Ring and Timer Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {
                TimerRing(
                    mode = uiState.mode,
                    timeFormatted = uiState.timeFormatted,
                    progress = uiState.progress,
                    isRunning = uiState.isRunning
                )

                Spacer(modifier = Modifier.height(36.dp))

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
                    .padding(bottom = 16.dp)
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

        // Settings Bottom Sheet
        SettingsBottomSheet(
            isOpen = uiState.isSettingsOpen,
            onDismiss = { viewModel.setSettingsOpen(false) },
            settings = uiState.settings,
            completedSessionsToday = uiState.completedSessionsToday,
            onUpdateSettings = { viewModel.updateSettings(it) },
            onResetStreak = { viewModel.resetStreak() },
            onTestAlarm = { viewModel.testAudioAlarm() }
        )
    }
}

@Composable
private fun TopBarSection(
    mode: TimerMode,
    completedSessions: Int,
    onModeChange: (TimerMode) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onOpenSettings() }
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (completedSessions > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "🍅 $completedSessions",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Mode Segmented Buttons & Settings Action
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.height(36.dp)
            ) {
                SegmentedButton(
                    selected = mode == TimerMode.WORK,
                    onClick = { onModeChange(TimerMode.WORK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(
                        text = stringResource(R.string.work_mode),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                SegmentedButton(
                    selected = mode == TimerMode.BREAK,
                    onClick = { onModeChange(TimerMode.BREAK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(
                        text = stringResource(R.string.break_mode),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.preferences_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
    val playButtonScale by animateFloatAsState(
        targetValue = if (isRunning) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "playScale"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reset Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 2.dp,
            modifier = Modifier.size(56.dp)
        ) {
            IconButton(
                onClick = onReset,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.RotateLeft,
                    contentDescription = stringResource(R.string.reset_timer),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Hero Play / Pause Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(92.dp)
                .scale(playButtonScale)
        ) {
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = isRunning,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "playPauseIcon"
                ) { running ->
                    Icon(
                        imageVector = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (running) R.string.pause_timer else R.string.play_timer
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
        }

        // Skip Button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 2.dp,
            modifier = Modifier.size(56.dp)
        ) {
            IconButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.skip_phase),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
