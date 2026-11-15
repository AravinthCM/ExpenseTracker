package com.example.expensetracker.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.backup.BACKUP_FORMAT_IDENTIFIER
import com.example.expensetracker.data.backup.BackupExportState
import com.example.expensetracker.data.backup.BackupImportState
import com.example.expensetracker.data.backup.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.expensetracker.data.backup.ImportMode
import com.example.expensetracker.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BannerKind { LOADING, SUCCESS, ERROR }

@Composable
fun BackupTabContent(
    totalTransactions: Int,
    totalAccounts: Int,
    totalCategories: Int,
    totalSplitGroups: Int,
    totalTemplates: Int,
    backupExportState: BackupExportState,
    backupImportState: BackupImportState = BackupImportState.Idle,
    onExportToUri: (Uri) -> Unit,
    onShareBackup: ((Intent) -> Unit) -> Unit,
    onImportFileSelected: (Uri) -> Unit = {},
    onConfirmImport: (ImportMode) -> Unit = {},
    onDismissState: () -> Unit = {},
    onDismissImportState: () -> Unit = {}
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    var showTechnical by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let { onExportToUri(it) } }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onImportFileSelected(it) } }

    if (backupImportState is BackupImportState.PreviewReady) {
        BackupImportPreviewDialog(
            summary = backupImportState.summary,
            isImporting = false,
            onDismiss = onDismissImportState,
            onConfirmImport = onConfirmImport
        )
    }

    val busy = backupExportState is BackupExportState.Loading ||
        backupImportState is BackupImportState.Validating ||
        backupImportState is BackupImportState.Importing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Status banners (one at a time, one shared component) ──
        when (backupImportState) {
            is BackupImportState.Validating -> StatusBanner(
                BannerKind.LOADING, "Checking backup file…", "Verifying structure and looking for duplicates", null, null
            )
            is BackupImportState.Importing -> StatusBanner(
                BannerKind.LOADING, "Restoring your data…", "Please keep the app open", null, null
            )
            is BackupImportState.Success -> StatusBanner(
                BannerKind.SUCCESS, "Import completed", backupImportState.message,
                backupImportState.result.safetyBackupFile?.let { "Safety backup: $it" },
                onDismissImportState
            )
            is BackupImportState.Error -> StatusBanner(
                BannerKind.ERROR, "Import failed", backupImportState.errorMessage, null, onDismissImportState
            )
            else -> {}
        }

        when (backupExportState) {
            is BackupExportState.Loading -> StatusBanner(
                BannerKind.LOADING, "Creating backup…", "Reading a consistent snapshot of your data", null, null
            )
            is BackupExportState.Success -> StatusBanner(
                BannerKind.SUCCESS, backupExportState.message, backupExportState.details, null, onDismissState
            )
            is BackupExportState.Error -> StatusBanner(
                BannerKind.ERROR, "Backup failed", backupExportState.errorMessage, null, onDismissState
            )
            BackupExportState.Idle -> {}
        }

        // ── What's in your data ──
        ManageSurface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Your data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = appColors.inkMuted)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("Transactions", totalTransactions.toString(), Modifier.weight(1f))
                    StatCell("Accounts", totalAccounts.toString(), Modifier.weight(1f))
                    StatCell("Categories", totalCategories.toString(), Modifier.weight(1f))
                    StatCell("Templates", totalTemplates.toString(), Modifier.weight(1f))
                }
            }
        }

        // ── Back up ──
        ManageSurface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Back up", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = appColors.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Save a copy of all your accounts, transactions and categories to a file you can keep or share.",
                    fontSize = 12.5.sp,
                    color = appColors.inkMuted
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            createDocumentLauncher.launch("expense_tracker_backup_$timestamp.json")
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.accent,
                            contentColor = appColors.accentInk
                        )
                    ) { Text("Save to file", fontWeight = FontWeight.SemiBold) }

                    OutlinedButton(
                        onClick = { onShareBackup { shareIntent -> context.startActivity(shareIntent) } },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, appColors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.ink)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ── Restore ──
        ManageSurface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Restore", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = appColors.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bring back data from a backup file. You'll see a preview and choose merge or replace before anything changes.",
                    fontSize = 12.5.sp,
                    color = appColors.inkMuted
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("application/json", "*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, appColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.ink)
                ) { Text("Choose backup file", fontWeight = FontWeight.SemiBold) }
            }
        }

        // ── Technical details, collapsed by default ──
        ManageSurface {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTechnical = !showTechnical }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Technical details",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.inkMuted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (showTechnical) "Hide" else "Show", fontSize = 12.sp, color = appColors.accent, fontWeight = FontWeight.SemiBold)
                }
                if (showTechnical) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                        if (totalSplitGroups > 0) DetailRow("Split groups", totalSplitGroups.toString())
                        DetailRow("Format", BACKUP_FORMAT_IDENTIFIER)
                        DetailRow("Schema version", "$CURRENT_BACKUP_SCHEMA_VERSION")
                        DetailRow("Money precision", "Integer paise + decimal")
                        DetailRow("Timestamps", "ISO-8601 UTC")
                        DetailRow("Excluded", "Raw debug notifications & secrets")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(
    kind: BannerKind,
    title: String,
    message: String?,
    mono: String?,
    onDismiss: (() -> Unit)?
) {
    val appColors = LocalAppColors.current
    val (bg: Color, fg: Color) = when (kind) {
        BannerKind.LOADING -> appColors.accentSoft to appColors.accent
        BannerKind.SUCCESS -> appColors.positiveSoft to appColors.positive
        BannerKind.ERROR -> appColors.negativeSoft to appColors.negative
    }

    Surface(shape = RoundedCornerShape(12.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (kind) {
                BannerKind.LOADING -> CircularProgressIndicator(
                    modifier = Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp
                )
                BannerKind.SUCCESS -> Icon(Icons.Default.CheckCircle, null, tint = fg, modifier = Modifier.size(22.dp))
                BannerKind.ERROR -> Icon(Icons.Default.Warning, null, tint = fg, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = appColors.ink)
                if (!message.isNullOrBlank()) {
                    Text(message, fontSize = 12.sp, color = appColors.inkMuted)
                }
                if (mono != null) {
                    Text(mono, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = appColors.inkMuted)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Text("✕", fontSize = 12.sp, color = appColors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Column(modifier = modifier) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = appColors.ink)
        Text(label, fontSize = 11.sp, color = appColors.inkFaint, maxLines = 1)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = appColors.inkMuted)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, color = appColors.ink)
    }
}