package com.example.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.BorderSubtle
import com.example.expensetracker.ui.util.CurrencyFormatter

@Composable
fun IncomeExpenseBar(
    incomeAmount: Double,
    expenseAmount: Double,
    modifier: Modifier = Modifier
) {
    val total = (incomeAmount + expenseAmount).coerceAtLeast(1.0)
    val incomeFraction = (incomeAmount / total).toFloat().coerceIn(0f, 1f)
    val expenseFraction = (expenseAmount / total).toFloat().coerceIn(0f, 1f)

    val incomeColor = Color(0xFF2E7D32)
    val expenseColor = MaterialTheme.colorScheme.error

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Income vs Expense",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Numerical Labels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(incomeAmount),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = incomeColor
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.format(expenseAmount),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = expenseColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Comparative Horizontal Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (incomeAmount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(incomeFraction.coerceAtLeast(0.01f))
                                .background(incomeColor)
                        )
                    }
                    if (expenseAmount > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(expenseFraction.coerceAtLeast(0.01f))
                                .background(expenseColor)
                        )
                    }
                }
            }
        }
    }
}
