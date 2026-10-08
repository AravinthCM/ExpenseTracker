package com.example.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter

data class CategorySpendingItem(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

@Composable
fun CategoryDonutChart(
    categoryItems: List<CategorySpendingItem>,
    totalExpense: Double,
    modifier: Modifier = Modifier,
    centerLabel: String = "Spent",
    chartSize: Dp = 118.dp,
    strokeWidth: Dp = 15.dp
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut Chart Canvas
        Box(
            modifier = Modifier.size(chartSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
                val strokeWidthPx = strokeWidth.toPx()

                if (totalExpense <= 0 || categoryItems.isEmpty()) {
                    drawArc(
                        color = appColors.surfaceAlt,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx)
                    )
                } else {
                    var startAngle = -90f
                    categoryItems.forEach { item ->
                        val sweepAngle = (item.percentage / 100f) * 360f
                        if (sweepAngle > 0f) {
                            drawArc(
                                color = item.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Butt)
                            )
                            startAngle += sweepAngle
                        }
                    }
                }
            }

            // Center Text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = CurrencyFormatter.format(totalExpense),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = appColors.ink
                )
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = appColors.inkFaint
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Legend List
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            categoryItems.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(item.color, CircleShape)
                    )
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = appColors.ink,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = CurrencyFormatter.format(item.amount),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = appColors.inkMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "${item.percentage.toInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp
                        ),
                        color = appColors.inkFaint,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}
