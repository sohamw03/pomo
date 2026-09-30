package com.pomo.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import com.pomo.app.R
import com.pomo.app.model.TimerMode
import com.pomo.app.model.TimerUiState
import com.pomo.app.ui.components.CustomDurationPanel
import com.pomo.app.ui.components.PlayPauseIcon
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
                    progress = uiState.progress
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
        // Sliding segmented switch, zero-gap by construction:
        // segments hug their labels, the thumb animates to the selected
        // segment's measured x + width, so there is exactly one rhythm —
        // 4.dp outer padding, 0.dp between segments.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            var tabX by remember { mutableStateOf(intArrayOf(0, 0)) }
            var tabW by remember { mutableStateOf(intArrayOf(0, 0)) }
            // One clock drives both offset and width so they stay in sync
            // (separate springs finish at different times = rubber-band jank).
            // Critically damped: glides without overshoot wobble.
            val progress by animateFloatAsState(
                targetValue = if (mode == TimerMode.WORK) 0f else 1f,
                animationSpec = spring(stiffness = 400f, dampingRatio = 1f),
                label = "modeThumb"
            )
            val thumbX = lerp(tabX[0], tabX[1], progress)
            val thumbWpx = lerp(tabW[0], tabW[1], progress)
            val density = LocalDensity.current
            // Three layers, bottom to top:
            // 1. click + ripple targets (invisible text, same size as labels),
            // 2. sliding thumb (no input, draws OVER the ripples),
            // 3. visible labels (no input, clicks pass through).
            // Per M3 segmented-button guidance the selection indicator covers
            // the pressed ripple instead of letting it linger on top of it.
            Box(modifier = Modifier.padding(4.dp)) {
                Row {
                    ModeRippleTab(
                        label = stringResource(R.string.work_mode),
                        onClick = { onModeChange(TimerMode.WORK) },
                        onBounds = { x, w ->
                            if (tabX[0] != x || tabW[0] != w) {
                                tabX = intArrayOf(x, tabX[1])
                                tabW = intArrayOf(w, tabW[1])
                            }
                        }
                    )
                    ModeRippleTab(
                        label = stringResource(R.string.break_mode),
                        onClick = { onModeChange(TimerMode.BREAK) },
                        onBounds = { x, w ->
                            if (tabX[1] != x || tabW[1] != w) {
                                tabX = intArrayOf(tabX[0], x)
                                tabW = intArrayOf(tabW[0], w)
                            }
                        }
                    )
                }
                if (thumbWpx > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .offset { IntOffset(thumbX, 0) }
                            .size(
                                width = with(density) { thumbWpx.toDp() },
                                height = 36.dp
                            )
                    ) {}
                }
                Row {
                    ModeLabel(
                        selected = mode == TimerMode.WORK,
                        label = stringResource(R.string.work_mode)
                    )
                    ModeLabel(
                        selected = mode == TimerMode.BREAK,
                        label = stringResource(R.string.break_mode)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRippleTab(
    label: String,
    onClick: () -> Unit,
    onBounds: (x: Int, w: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Invisible text keeps this exactly the same size as the visible label.
    // Ripple draws here, underneath the thumb that slides over it.
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .height(36.dp)
            .onGloballyPositioned {
                onBounds(
                    it.positionInParent().x.roundToInt(),
                    it.size.width
                )
            }
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
                color = Color.Transparent
            )
        }
    }
}

@Composable
private fun ModeLabel(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier
) {
    // No input handling: clicks pass through to the ripple layer below.
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "modeLabel"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(36.dp)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = textColor
        )
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
        val playPauseDesc = stringResource(
            if (isRunning) R.string.pause_timer else R.string.play_timer
        )
        Surface(
            onClick = onTogglePlayPause,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 8.dp,
            modifier = Modifier.size(96.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = playPauseDesc
                    }
            ) {
                PlayPauseIcon(
                    isRunning = isRunning,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
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
