package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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

    if (templates.isEmpty()) {
        ManageEmptyState(
            title = "No templates yet",
            message = "Paste a real bank SMS or email once, and the app will learn to read similar alerts automatically.",
            actionLabel = "Add template",
            onAction = onAddTemplateClick
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Templates read your bank alerts and turn them into transactions. Turn one off to pause it.",
                    fontSize = 12.sp,
                    color = appColors.inkMuted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
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

    deletingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = { Text("Delete template?", fontWeight = FontWeight.Bold) },
            text = {
                Text("'${template.name}' will be removed. Incoming notifications will no longer be matched against it.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTemplateClick(template)
                    deletingTemplate = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { deletingTemplate = null }) { Text("Cancel") } }
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

    val isDebit = template.transactionType.equals("EXPENSE", ignoreCase = true) ||
        template.transactionType.equals("DEBIT", ignoreCase = true)

    ManageSurface(onClick = onEdit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 8.dp, end = 4.dp)
        ) {
            // Name + type + on/off
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (template.enabled) 1f else 0.55f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = template.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isDebit) ManagePill("DEBIT", appColors.negativeSoft, appColors.negative)
                    else ManagePill("CREDIT", appColors.positiveSoft, appColors.positive)
                }
                Switch(
                    checked = template.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = appColors.accentInk,
                        checkedTrackColor = appColors.accent
                    ),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .height(24.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // The sample message is what people recognise; the raw pattern is an implementation detail.
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(appColors.surfaceAlt)
                    .padding(10.dp)
                    .alpha(if (template.enabled) 1f else 0.55f)
            ) {
                Text(
                    text = template.sampleText.ifBlank { template.templatePattern },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = appColors.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors.accentSoft)
                        .clickable { onTest() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, "Test template", tint = appColors.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Test", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = appColors.accent)
                }
                Spacer(Modifier.weight(1f))
                ManageOverflowMenu(
                    actions = listOf(
                        ManageMenuAction("Edit") { onEdit() },
                        ManageMenuAction("Delete", destructive = true) { onDelete() }
                    )
                )
            }
        }
    }
}