package com.pomo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomo.app.R
import com.pomo.app.model.TimerMode
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TimerRing(
    mode: TimerMode,
    timeFormatted: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 150),
        label = "progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(310.dp)
    ) {
        Canvas(modifier = Modifier.size(300.dp)) {
            val strokeWidthPx = 13.dp.toPx()
            val diameter = size.minDimension - strokeWidthPx
            val radius = diameter / 2f
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            // Background full track
            drawCircle(
                color = trackBgColor,
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokeWidthPx)
            )

            // Progress Arc starting from top (-90 degrees)
            val sweepAngle = animatedProgress * 360f
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                size = Size(diameter, diameter),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Glowing Nub Head
            if (animatedProgress > 0.005f) {
                val angleRad = Math.toRadians((sweepAngle - 90f).toDouble())
                val nubX = centerOffset.x + radius * cos(angleRad).toFloat()
                val nubY = centerOffset.y + radius * sin(angleRad).toFloat()

                drawCircle(
                    color = primaryColor,
                    radius = strokeWidthPx * 0.75f,
                    center = Offset(nubX, nubY)
                )
            }
        }

        // Countdown Text in center
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 62.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.SansSerif
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    if (mode.isWork) R.string.focus_label else R.string.relax_label
                ).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
