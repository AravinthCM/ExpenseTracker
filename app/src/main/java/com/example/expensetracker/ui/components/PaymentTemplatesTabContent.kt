package com.example.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.PaymentTemplateEntity
import com.example.expensetracker.ui.theme.LocalAppColors

@Composable
fun PaymentTemplatesTabContent(
    templates: List<PaymentTemplateEntity>,
    onAddTemplateClick: () -> Unit,
    onEditTemplateClick: (PaymentTemplateEntity) -> Unit,
    onTestTemplateClick: (PaymentTemplateEntity) -> Unit,
    onToggleEnabledClick: (PaymentTemplateEntity) -> Unit,
    onDeleteTemplateClick: (PaymentTemplateEntity) -> Unit
) {
    val appColors = LocalAppColors.current
    var deletingTemplate by remember { mutableStateOf<PaymentTemplateEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = appColors.inkFaint
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Payment Templates Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = appColors.ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create custom templates to accurately extract transactions from real bank notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors.inkFaint,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(templates, key = { it.id }) { template ->
                    PaymentTemplateCard(
                        template = template,
                        onToggleEnabled = { onToggleEnabledClick(template) },
                        onEdit = { onEditTemplateClick(template) },
                        onTest = { onTestTemplateClick(template) },
                        onDelete = { deletingTemplate = template }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // "+ Add Payment Template" Block Button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAddTemplateClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
            border = BorderStroke(1.dp, appColors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = appColors.ink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add Payment Template",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = appColors.ink
                )
            }
        }
    }

    // Delete Confirmation Dialog
    deletingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = {
                Text(
                    text = "Delete Payment Template?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("Are you sure you want to delete '${template.name}'? Incoming notifications will no longer be matched against this template.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTemplateClick(template)
                        deletingTemplate = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTemplate = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PaymentTemplateCard(
    template: PaymentTemplateEntity,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    val appColors = LocalAppColors.current

    val isDebit = template.transactionType.equals("EXPENSE", ignoreCase = true) || template.transactionType.equals("DEBIT", ignoreCase = true)
    val badgeBg = if (isDebit) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    val badgeText = if (isDebit) Color(0xFFC62828) else Color(0xFF2E7D32)
    val badgeLabel = if (isDebit) "Debit" else "Credit"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.cardBackground),
        border = BorderStroke(1.dp, appColors.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top Row: Name + Type Badge + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = appColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = badgeText
                            )
                        )
                    }
                }

                Switch(
                    checked = template.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = appColors.accent,
                        checkedTrackColor = appColors.accentSoft
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Template Pattern Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(appColors.surfaceAlt)
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "PATTERN PREVIEW:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = appColors.inkFaint
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = template.templatePattern,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = appColors.ink
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Test Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.accentSoft)
                            .clickable { onTest() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test Template",
                                tint = appColors.accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Test",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.accent
                                )
                            )
                        }
                    }

                    // Edit Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.surfaceAlt)
                            .clickable { onEdit() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Template",
                                tint = appColors.inkMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = appColors.inkMuted
                                )
                            )
                        }
                    }
                }

                // Delete Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors.surfaceAlt)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Template",
                        tint = appColors.negative,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
