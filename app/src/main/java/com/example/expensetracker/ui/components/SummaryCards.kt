package com.example.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.BorderSubtle
import com.example.expensetracker.ui.theme.ExpenseRed
import com.example.expensetracker.ui.theme.ExpenseRedBg
import com.example.expensetracker.ui.theme.IncomeGreen
import com.example.expensetracker.ui.theme.IncomeGreenBg
import com.example.expensetracker.ui.theme.TextMuted
import com.example.expensetracker.ui.theme.TextPrimary
import com.example.expensetracker.ui.util.CurrencyFormatter

@Composable
fun SummaryCards(
    incomeAmount: Double,
    expenseAmount: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryItemCard(
            title = "Income",
            amount = CurrencyFormatter.format(incomeAmount),
            icon = Icons.Default.KeyboardArrowUp,
            accentColor = IncomeGreen,
            backgroundColor = IncomeGreenBg,
            modifier = Modifier.weight(1f)
        )
        SummaryItemCard(
            title = "Expenses",
            amount = CurrencyFormatter.format(expenseAmount),
            icon = Icons.Default.KeyboardArrowDown,
            accentColor = ExpenseRed,
            backgroundColor = ExpenseRedBg,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryItemCard(
    title: String,
    amount: String,
    icon: ImageVector,
    accentColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(color = backgroundColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary
            )
        }
    }
}
