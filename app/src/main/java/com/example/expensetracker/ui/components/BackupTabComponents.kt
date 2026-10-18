package com.example.expensetracker.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.backup.BACKUP_FORMAT_IDENTIFIER
import com.example.expensetracker.data.backup.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.expensetracker.data.backup.BackupExportState
import com.example.expensetracker.ui.theme.BorderSubtle
import com.example.expensetracker.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupTabContent(
    totalTransactions: Int,
    totalAccounts: Int,
    totalCategories: Int,
    totalSplitGroups: Int,
    totalTemplates: Int,
    backupExportState: BackupExportState,
    onExportToUri: (Uri) -> Unit,
    onShareBackup: ((Intent) -> Unit) -> Unit,
    onDismissState: () -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { onExportToUri(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card (Loading / Success / Error)
        when (backupExportState) {
            is BackupExportState.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.accentSoft.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, appColors.accent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = appColors.accent,
                            strokeWidth = 2.5.dp
                        )
                        Column {
                            Text(
                                text = "Creating snapshot & exporting...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = appColors.ink
                            )
                            Text(
                                text = "Reading consistent database snapshot",
                                fontSize = 12.sp,
                                color = appColors.inkMuted
                            )
                        }
                    }
                }
            }
            is BackupExportState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.accentSoft.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, appColors.accent)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = appColors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = backupExportState.message,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = appColors.ink
                            )
                            backupExportState.details?.let { details ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = details,
                                    fontSize = 12.sp,
                                    color = appColors.inkMuted
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismissState,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", fontSize = 12.sp, color = appColors.inkMuted)
                        }
                    }
                }
            }
            is BackupExportState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.negativeSoft.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, appColors.negative)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = appColors.negative,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Export Failed",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = appColors.negative
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = backupExportState.errorMessage,
                                fontSize = 12.sp,
                                color = appColors.ink
                            )
                        }
                        IconButton(
                            onClick = onDismissState,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", fontSize = 12.sp, color = appColors.inkMuted)
                        }
                    }
                }
            }
            BackupExportState.Idle -> { /* No banner */ }
        }

        // Export Actions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surface),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Backup & Export Data",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Export a complete, versioned snapshot of all financial accounts, transactions, splits, and categories in UTF-8 JSON format.",
                    fontSize = 12.sp,
                    color = appColors.inkMuted
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        createDocumentLauncher.launch("expense_tracker_backup_$timestamp.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.accent,
                        contentColor = appColors.accentInk
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Save Backup to File",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        onShareBackup { shareIntent ->
                            context.startActivity(shareIntent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = appColors.ink
                    ),
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(16.dp),
                        tint = appColors.ink
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Share Backup File",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Snapshot Inventory Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surface),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Current Database Inventory",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.ink
                )
                Spacer(modifier = Modifier.height(12.dp))

                InventoryRow("Accounts", totalAccounts.toString(), appColors)
                InventoryRow("Transactions", totalTransactions.toString(), appColors)
                InventoryRow("Categories", totalCategories.toString(), appColors)
                InventoryRow("Split Groups", totalSplitGroups.toString(), appColors)
                InventoryRow("Payment Templates", totalTemplates.toString(), appColors)
            }
        }

        // Schema & Specification Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surface),
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Format Info",
                        tint = appColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Backup Specification",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.ink
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                FormatDetailRow("Format Identifier", BACKUP_FORMAT_IDENTIFIER, appColors)
                FormatDetailRow("Schema Version", "$CURRENT_BACKUP_SCHEMA_VERSION", appColors)
                FormatDetailRow("Monetary Precision", "Integer Paise + Decimal", appColors)
                FormatDetailRow("Timestamp Format", "ISO-8601 UTC", appColors)
                FormatDetailRow("Foreign Keys", "Preserved with Stable IDs", appColors)
                FormatDetailRow("Exclusions", "Raw debug notifications & secrets", appColors)
            }
        }
    }
}

@Composable
private fun InventoryRow(
    label: String,
    value: String,
    appColors: com.example.expensetracker.ui.theme.AppColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = appColors.inkMuted
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors.ink
        )
    }
}

@Composable
private fun FormatDetailRow(
    label: String,
    value: String,
    appColors: com.example.expensetracker.ui.theme.AppColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = appColors.inkMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = appColors.ink
        )
    }
}
