package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.LocalAppColors

data class TrendPoint(
    val label: String,
    val amount: Double
)

@Composable
fun SpendingTrendChart(
    trendPoints: List<TrendPoint>,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    if (trendPoints.isEmpty()) return

    val maxAmount = (trendPoints.maxOfOrNull { it.amount } ?: 0.0).coerceAtLeast(1.0)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        trendPoints.forEach { point ->
            val fillFraction = (point.amount / maxAmount).toFloat().coerceIn(0.06f, 1.0f)
            val valueText = formatShortAmount(point.amount)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                // Value text above bar
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = appColors.inkMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bar
                Box(
                    modifier = Modifier
                        .fillMaxHeight(fillFraction)
                        .width(26.dp)
                        .background(
                            color = appColors.accent,
                            shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                        )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // X-Axis Label below bar
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp
                    ),
                    color = appColors.inkFaint,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatShortAmount(amount: Double): String {
    if (amount <= 0) return "0"
    return if (amount >= 1000) {
        String.format(java.util.Locale.US, "%.1fk", amount / 1000)
    } else {
        amount.toInt().toString()
    }
}
