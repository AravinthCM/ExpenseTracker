package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.PaymentTemplateHelper
import com.example.expensetracker.service.PaymentTemplateMatcher
import com.example.expensetracker.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddOrEditTemplateDialog(
    title: String,
    initialTemplate: PaymentTemplateEntity? = null,
    initialSampleText: String = "",
    onDismiss: () -> Unit,
    onSave: (name: String, transactionType: String, sampleText: String, templatePattern: String) -> Unit
) {
    val appColors = LocalAppColors.current

    var name by remember { mutableStateOf(initialTemplate?.name ?: "") }
    var transactionType by remember { mutableStateOf(initialTemplate?.transactionType ?: "EXPENSE") }
    var sampleText by remember { mutableStateOf(initialTemplate?.sampleText ?: initialSampleText) }
    var templatePattern by remember { mutableStateOf(initialTemplate?.templatePattern ?: "") }

    var step by remember { mutableStateOf(if (initialTemplate != null || initialSampleText.isNotBlank()) 2 else 1) }

    // Run auto-detection when entering Step 2 if templatePattern is empty
    LaunchedEffect(sampleText, step) {
        if (step == 2 && templatePattern.isBlank() && sampleText.isNotBlank()) {
            val autoDetected = PaymentTemplateHelper.autoDetectTemplate(sampleText)
            templatePattern = autoDetected.suggestedPattern
        }
    }

    val tempEntity = remember(name, transactionType, sampleText, templatePattern) {
        PaymentTemplateEntity(
            id = initialTemplate?.id ?: "temp",
            name = name.ifBlank { "Test Template" },
            transactionType = transactionType,
            sampleText = sampleText,
            templatePattern = templatePattern
        )
    }

    val liveMatch = remember(sampleText, templatePattern, transactionType) {
        if (sampleText.isNotBlank() && templatePattern.isNotBlank()) {
            PaymentTemplateMatcher.matchTemplate(sampleText, tempEntity)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = appColors.ink
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (step == 1) {
                    // STEP 1: FORM INPUT
                    Text(
                        text = "Enter details & paste sample notification email or SMS text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors.inkFaint
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Template Name (e.g. Canara Debit)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Transaction Type",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = appColors.ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { transactionType = "EXPENSE" }
                        ) {
                            RadioButton(
                                selected = transactionType.equals("EXPENSE", ignoreCase = true) || transactionType.equals("DEBIT", ignoreCase = true),
                                onClick = { transactionType = "EXPENSE" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Debit (Expense)", fontSize = 13.5.sp, color = appColors.ink)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { transactionType = "INCOME" }
                        ) {
                            RadioButton(
                                selected = transactionType.equals("INCOME", ignoreCase = true) || transactionType.equals("CREDIT", ignoreCase = true),
                                onClick = { transactionType = "INCOME" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Credit (Income)", fontSize = 13.5.sp, color = appColors.ink)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = sampleText,
                        onValueChange = { sampleText = it },
                        label = { Text("Sample Message") },
                        placeholder = { Text("Paste real bank notification text here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 160.dp)
                    )
                } else {
                    // STEP 2: TEMPLATE CONFIGURATION & REVIEW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Review Detected Template",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = appColors.ink
                        )
                        TextButton(
                            onClick = { step = 1 },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                        ) {
                            Text("Edit Input", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Template Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Template Pattern (Editable):",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = appColors.inkFaint
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = templatePattern,
                        onValueChange = { templatePattern = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 140.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Insert Variable Tags Helper
                    Text(
                        text = "Insert Variable Placeholder:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = appColors.inkFaint
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val variableTags = listOf("AMOUNT", "DATE", "ACCOUNT", "MERCHANT", "REFERENCE", "BALANCE")

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        variableTags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = appColors.accentSoft,
                                modifier = Modifier.clickable {
                                    templatePattern += " {$tag}"
                                }
                            ) {
                                Text(
                                    text = "+ {$tag}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = appColors.accent
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Matching Validation Card
                    val isMatched = liveMatch?.matched == true
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
                                    text = if (isMatched) "LIVE PREVIEW: TEMPLATE MATCHES SAMPLE" else "LIVE PREVIEW: NO MATCH",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = textColor
                                    )
                                )
                            }

                            if (isMatched && liveMatch != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Divider(color = cardBorder, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(6.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    PreviewFieldRow("Amount", liveMatch.amount?.let { "₹%.2f".format(it) } ?: "(none)")
                                    PreviewFieldRow("Type", liveMatch.transactionType?.name ?: transactionType)
                                    PreviewFieldRow("Merchant", liveMatch.merchant ?: "(none)")
                                    PreviewFieldRow("Account", liveMatch.account ?: "(none)")
                                    PreviewFieldRow("Reference", liveMatch.reference ?: "(none)")
                                    PreviewFieldRow("Balance", liveMatch.balance?.let { "₹%.2f".format(it) } ?: "(none)")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                TextButton(
                    enabled = sampleText.isNotBlank(),
                    onClick = { step = 2 }
                ) {
                    Text("Next →", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    enabled = name.isNotBlank() && templatePattern.isNotBlank() && (liveMatch?.matched == true),
                    onClick = {
                        onSave(name.trim(), transactionType, sampleText.trim(), templatePattern.trim())
                    }
                ) {
                    Text("Save Template", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PreviewFieldRow(label: String, value: String) {
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
