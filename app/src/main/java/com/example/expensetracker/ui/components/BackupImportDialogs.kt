package com.example.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.backup.ImportMode
import com.example.expensetracker.data.backup.ImportPreviewSummary
import com.example.expensetracker.ui.theme.BorderSubtle
import com.example.expensetracker.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupImportPreviewDialog(
    summary: ImportPreviewSummary,
    isImporting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmImport: (ImportMode) -> Unit
) {
    val appColors = LocalAppColors.current
    var selectedMode by remember { mutableStateOf(ImportMode.MERGE) }
    var showFullReplaceConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showFullReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { showFullReplaceConfirm = false },
            title = {
                Text(
                    text = "Confirm Full Restore",
                    fontWeight = FontWeight.Bold,
                    color = appColors.negative
                )
            },
            text = {
                Text(
                    text = "This will completely replace all existing data in the application with the backup snapshot. An emergency safety copy of your current data will be saved to app cache before replacing. Are you sure you want to proceed?",
                    fontSize = 13.sp,
                    color = appColors.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFullReplaceConfirm = false
                        onConfirmImport(ImportMode.RESTORE_REPLACE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.negative)
                ) {
                    Text("Proceed with Restore", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullReplaceConfirm = false }) {
                    Text("Cancel", color = appColors.inkMuted)
                }
            },
            containerColor = appColors.surface
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isImporting) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = appColors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(appColors.border, RoundedCornerShape(3.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Backup Import Preview",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = appColors.ink,
                    modifier = Modifier.weight(1f)
                )

                if (!isImporting) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(appColors.surfaceAlt)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.inkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        PreviewDetailRow("Schema Version", "${summary.backup.schemaVersion}", appColors)
                        PreviewDetailRow("App Version", summary.backup.appVersion, appColors)
                        PreviewDetailRow("Exported At", summary.backup.exportedAt, appColors)
                    }
                }

                // Import Mode Selection
                Text(
                    text = "Select Import Strategy",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = appColors.ink
                )

                // Option 1: Merge
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = ImportMode.MERGE },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == ImportMode.MERGE)
                            appColors.accentSoft.copy(alpha = 0.25f)
                        else
                            appColors.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedMode == ImportMode.MERGE) appColors.accent else BorderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedMode == ImportMode.MERGE,
                            onClick = { selectedMode = ImportMode.MERGE },
                            colors = RadioButtonDefaults.colors(selectedColor = appColors.accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Merge with Existing Data (Safe)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = appColors.ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Preserves existing accounts and transactions. Skips duplicate transactions and adds missing categories and splits.",
                                fontSize = 12.sp,
                                color = appColors.inkMuted
                            )
                        }
                    }
                }

                // Option 2: Full Restore
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMode = ImportMode.RESTORE_REPLACE },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedMode == ImportMode.RESTORE_REPLACE)
                            appColors.negativeSoft.copy(alpha = 0.2f)
                        else
                            appColors.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedMode == ImportMode.RESTORE_REPLACE) appColors.negative else BorderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = selectedMode == ImportMode.RESTORE_REPLACE,
                            onClick = { selectedMode = ImportMode.RESTORE_REPLACE },
                            colors = RadioButtonDefaults.colors(selectedColor = appColors.negative)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Full Restore (Replace Database)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selectedMode == ImportMode.RESTORE_REPLACE) appColors.negative else appColors.ink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Replaces all existing data with this exact backup snapshot. An emergency safety backup will be created in cache before replacing.",
                                fontSize = 12.sp,
                                color = appColors.inkMuted
                            )
                        }
                    }
                }

                // Entity Summary Card
                Text(
                    text = "Incoming Dataset Summary",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = appColors.ink
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.surface),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PreviewDetailRow("Accounts", "${summary.accountsCount}", appColors)
                        PreviewDetailRow("Categories", "${summary.categoriesCount}", appColors)
                        PreviewDetailRow(
                            "Transactions",
                            "${summary.transactionsCount} (${summary.expenseCount} exp, ${summary.incomeCount} inc, ${summary.transferCount} trf)",
                            appColors
                        )
                        PreviewDetailRow("Split Groups & Members", "${summary.splitGroupsCount} groups, ${summary.splitGroupMembersCount} members", appColors)
                        PreviewDetailRow("Splits & Shares", "${summary.expenseSplitsCount} splits, ${summary.expenseSplitSharesCount} shares", appColors)
                        PreviewDetailRow("Repayments & Allocations", "${summary.repaymentsCount} repayments, ${summary.repaymentAllocationsCount} allocs", appColors)
                        PreviewDetailRow("Recurring & Templates", "${summary.recurringExpensesCount} recurring, ${summary.paymentTemplatesCount} templates", appColors)
                        PreviewDetailRow("Observations & Mappings", "${summary.balanceObservationsCount} obs, ${summary.sourceAccountMappingsCount} mappings", appColors)
                    }
                }

                // Potential Duplicates / Conflicts warning
                if (summary.duplicateCandidates.isNotEmpty() && selectedMode == ImportMode.MERGE) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.accentSoft.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, appColors.accent)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Duplicate Prevention (${summary.duplicateCandidates.size} candidates)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = appColors.ink
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${summary.duplicateCandidates.size} transaction(s) match existing reference numbers or exact amounts/dates and will be safely skipped during merge.",
                                fontSize = 12.sp,
                                color = appColors.inkMuted
                            )
                        }
                    }
                }

                // Validation Errors (if any)
                if (summary.validationErrors.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.negativeSoft.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, appColors.negative)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Errors",
                                    tint = appColors.negative,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Validation Errors (${summary.validationErrors.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = appColors.negative
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            summary.validationErrors.take(5).forEach { err ->
                                Text(
                                    text = "• [${err.entity}] ${err.message}",
                                    fontSize = 11.5.sp,
                                    color = appColors.ink
                                )
                            }
                            if (summary.validationErrors.size > 5) {
                                Text(
                                    text = "... and ${summary.validationErrors.size - 5} more error(s)",
                                    fontSize = 11.sp,
                                    color = appColors.inkMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Action Buttons – always pinned at bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isImporting) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Cancel", color = appColors.inkMuted, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = appColors.accent,
                            strokeWidth = 2.5.dp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (selectedMode == ImportMode.RESTORE_REPLACE) {
                                showFullReplaceConfirm = true
                            } else {
                                onConfirmImport(ImportMode.MERGE)
                            }
                        },
                        enabled = summary.isRestoreSafe,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMode == ImportMode.RESTORE_REPLACE) appColors.negative else appColors.accent,
                            contentColor = appColors.accentInk
                        ),
                        shape = RoundedCornerShape(13.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (selectedMode == ImportMode.RESTORE_REPLACE) "Restore Database" else "Merge Data",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewDetailRow(
    label: String,
    value: String,
    appColors: com.example.expensetracker.ui.theme.AppColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = appColors.inkMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = appColors.ink
        )
    }
}
