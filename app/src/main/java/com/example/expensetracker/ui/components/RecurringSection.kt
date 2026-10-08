package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.expensetracker.ui.theme.BorderSubtle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.local.RecurringExpenseEntity
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.service.ConfirmedRecurringStatus
import com.example.expensetracker.service.DetectedRecurringCandidate
import com.example.expensetracker.service.RecurringExpenseDetector
import com.example.expensetracker.ui.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecurringSection(
    transactions: List<Transaction>,
    recurringEntities: List<RecurringExpenseEntity>,
    onConfirmCandidate: (DetectedRecurringCandidate) -> Unit,
    onDismissCandidate: (DetectedRecurringCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    val confirmedEntities = recurringEntities.filter { it.active && !it.dismissed }

    val candidates = RecurringExpenseDetector.detectCandidates(
        transactions = transactions,
        existingConfirmedOrDismissed = recurringEntities
    )

    val confirmedStatuses = RecurringExpenseDetector.evaluateConfirmedStatus(confirmedEntities)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Text(
            text = "Recurring Expenses & Subscriptions",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Confirmed Recurring Expenses List
        if (confirmedStatuses.isNotEmpty()) {
            Text(
                text = "Confirmed Recurring",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            confirmedStatuses.forEach { status ->
                ConfirmedRecurringCard(status = status)
            }
        }

        // 2. Detected Possible Recurring Candidates
        if (candidates.isNotEmpty()) {
            Text(
                text = "Possible Recurring Candidates",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            candidates.forEach { candidate ->
                DetectedCandidateCard(
                    candidate = candidate,
                    onConfirm = { onConfirmCandidate(candidate) },
                    onDismiss = { onDismissCandidate(candidate) }
                )
            }
        }

        if (confirmedStatuses.isEmpty() && candidates.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No recurring expenses detected yet. The app will automatically identify recurring subscriptions as matching transactions arrive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmedRecurringCard(status: ConfirmedRecurringStatus) {
    val entity = status.entity
    val amountFormatted = "${CurrencyFormatter.format(entity.amount)} / ${entity.frequency.lowercase()}"

    val statusColor = if (status.isMissed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.merchant,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }

            Text(
                text = amountFormatted,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DetectedCandidateCard(
    candidate: DetectedRecurringCandidate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
    val lastChargedStr = "Last charged ${sdf.format(Date(candidate.lastTransactionTimestamp))}"
    val amountFormatted = "${CurrencyFormatter.format(candidate.amount)} / ${candidate.frequency.lowercase()}"

    val isHighConf = candidate.confidence.contains("High", ignoreCase = true)
    val confidenceBadgeBg = if (isHighConf) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val confidenceBadgeTextColor = if (isHighConf) Color(0xFF2E7D32) else Color(0xFFE65100)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.displayMerchant,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lastChargedStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = amountFormatted,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = confidenceBadgeBg
                    ) {
                        Text(
                            text = candidate.confidence,
                            style = MaterialTheme.typography.labelSmall,
                            color = confidenceBadgeTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons: Confirm and Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(onClick = onConfirm) {
                    Text("Confirm")
                }
            }
        }
    }
}
