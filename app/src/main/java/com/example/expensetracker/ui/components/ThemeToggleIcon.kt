package com.example.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.LocalAppColors

@Composable
fun ThemeToggleIconButton(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        val iconColor = appColors.inkMuted

        Canvas(modifier = Modifier.size(17.dp)) {
            val width = size.width
            val height = size.height
            val strokeW = 1.8f * (width / 24f)

            if (!isDark) {
                // Draw Moon Icon in light mode (clicking toggles to dark mode)
                val moonPath = Path().apply {
                    fillType = PathFillType.EvenOdd
                    // Scaled path for 24x24 viewport onto canvas size
                    moveTo(20f / 24f * width, 14.5f / 24f * height)
                    cubicTo(
                        18f / 24f * width, 19f / 24f * height,
                        13f / 24f * width, 20f / 24f * height,
                        9.5f / 24f * width, 17f / 24f * height
                    )
                    cubicTo(
                        6f / 24f * width, 14f / 24f * height,
                        5.5f / 24f * width, 8.5f / 24f * height,
                        9.5f / 24f * width, 4f / 24f * height
                    )
                    cubicTo(
                        6f / 24f * width, 6f / 24f * height,
                        5f / 24f * width, 11f / 24f * height,
                        9f / 24f * width, 15f / 24f * height
                    )
                    cubicTo(
                        13f / 24f * width, 19f / 24f * height,
                        18f / 24f * width, 17.5f / 24f * height,
                        20f / 24f * width, 14.5f / 24f * height
                    )
                    close()
                }
                drawPath(
                    path = moonPath,
                    color = iconColor,
                    style = Stroke(width = strokeW)
                )
            } else {
                // Draw Sun Icon in dark mode (clicking toggles to light mode)
                drawCircle(
                    color = iconColor,
                    radius = (4f / 24f) * width,
                    center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f),
                    style = Stroke(width = strokeW)
                )

                // Sun rays
                val rays = listOf(
                    Pair(12f, 2f) to Pair(12f, 4.5f),
                    Pair(12f, 19.5f) to Pair(12f, 22f),
                    Pair(4.2f, 4.2f) to Pair(6f, 6f),
                    Pair(18f, 18f) to Pair(19.8f, 19.8f),
                    Pair(2f, 12f) to Pair(4.5f, 12f),
                    Pair(19.5f, 12f) to Pair(22f, 12f),
                    Pair(4.2f, 19.8f) to Pair(6f, 18f),
                    Pair(18f, 6f) to Pair(19.8f, 4.2f)
                )
                rays.forEach { (start, end) ->
                    drawLine(
                        color = iconColor,
                        start = androidx.compose.ui.geometry.Offset(
                            (start.first / 24f) * width,
                            (start.second / 24f) * height
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            (end.first / 24f) * width,
                            (end.second / 24f) * height
                        ),
                        strokeWidth = strokeW
                    )
                }
            }
        }
    }
}
