package com.example.expensetracker.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.service.PaymentTemplateMatcher
import com.example.expensetracker.ui.theme.LocalAppColors

@Composable
fun TestTemplateDialog(
    template: PaymentTemplateEntity,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current

    var testInputText by remember { mutableStateOf(template.sampleText) }

    val matchResult = remember(testInputText, template) {
        if (testInputText.isNotBlank()) {
            PaymentTemplateMatcher.matchTemplate(testInputText, template)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Test Template: ${template.name}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = appColors.ink
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Paste test notification message below to verify matching:",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.inkFaint
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    label = { Text("Test Input Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 160.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val isMatched = matchResult?.matched == true
                val cardBg = if (isMatched) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                val cardBorder = if (isMatched) Color(0xFFA5D6A7) else Color(0xFFFFCDD2)
                val textColor = if (isMatched) Color(0xFF2E7D32) else Color(0xFFC62828)

                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, cardBorder, RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isMatched) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isMatched) "MATCH SUCCESSFUL" else "NO MATCH",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = textColor
                                )
                            )
                        }

                        if (isMatched && matchResult != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Divider(color = cardBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(6.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                TestResultRow("Amount", matchResult.amount?.let { "₹%.2f".format(it) } ?: "(none)")
                                TestResultRow("Type", matchResult.transactionType?.name ?: template.transactionType)
                                TestResultRow("Merchant", matchResult.merchant ?: "(none)")
                                TestResultRow("Account", matchResult.account ?: "(none)")
                                TestResultRow("Reference", matchResult.reference ?: "(none)")
                                TestResultRow("Balance", matchResult.balance?.let { "₹%.2f".format(it) } ?: "(none)")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun TestResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                color = Color(0xFF2E7D32)
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF1B5E20)
            )
        )
    }
}
