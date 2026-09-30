package com.pomo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp

// Web parity: morphs between main's PAUSE_ICON_PATH / PLAY_ICON_PATH
// with PLAY_PAUSE_SPRING (stiffness 260, damping 26, mass 0.9).
// Each side is a quad, so pause <-> play is a plain vertex lerp.
private const val VIEWBOX = 24f
private const val STROKE_WIDTH = 1.5f

// x0,y0, x1,y1, x2,y2, x3,y3
private val PAUSE_LEFT = floatArrayOf(5f, 5f, 9f, 5f, 9f, 19f, 5f, 19f)
private val PLAY_LEFT = floatArrayOf(7f, 5f, 13f, 8.5f, 13f, 15.5f, 7f, 19f)
private val PAUSE_RIGHT = floatArrayOf(15f, 5f, 19f, 5f, 19f, 19f, 15f, 19f)
private val PLAY_RIGHT = floatArrayOf(13f, 8.5f, 19f, 12f, 19f, 12f, 13f, 15.5f)

private val MORPH_STROKE = Stroke(
    width = STROKE_WIDTH,
    join = StrokeJoin.Round,
    cap = StrokeCap.Round
)

private fun morphPath(from: FloatArray, to: FloatArray, t: Float): Path =
    Path().apply {
        for (i in from.indices step 2) {
            val x = lerp(from[i], to[i], t)
            val y = lerp(from[i + 1], to[i + 1], t)
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** Play <-> pause morph icon. `progress` 0f = pause, 1f = play. */
@Composable
fun PlayPauseIcon(
    isRunning: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    // Web spring (mass 0.9) normalized to Compose's unit mass:
    // dampingRatio ~0.85, stiffness ~(260 / 0.9) ~ 289.
    val progress by animateFloatAsState(
        targetValue = if (isRunning) 0f else 1f,
        animationSpec = spring(stiffness = 289f, dampingRatio = 0.85f),
        label = "playPauseMorph"
    )

    val left = remember(progress) { morphPath(PAUSE_LEFT, PLAY_LEFT, progress) }
    val right = remember(progress) { morphPath(PAUSE_RIGHT, PLAY_RIGHT, progress) }

    Canvas(modifier = modifier) {
        // Paths are in 24dp viewBox units; scale from top-left so
        // viewBox point p maps to p * s pixels. Default pivot is the
        // canvas center (pixels), which would fling small viewBox
        // coords off-screen — hence the explicit Zero pivot.
        val s = size.minDimension / VIEWBOX
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            drawPath(left, color = tint, style = Fill)
            drawPath(left, color = tint, style = MORPH_STROKE)
            drawPath(right, color = tint, style = Fill)
            drawPath(right, color = tint, style = MORPH_STROKE)
        }
    }
}
