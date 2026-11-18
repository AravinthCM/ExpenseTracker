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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.preferences.CloudSyncState
import com.example.expensetracker.data.preferences.GoogleAccountInfo
import com.example.expensetracker.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Cloud backup card. Three clear states:
 *  1. Signed out        → value proposition + "Continue with Google"
 *  2. Signed in, off    → account header + "Enable cloud backup"
 *  3. Signed in, on     → live status, key facts, Back up now / Restore, account menu
 */
@Composable
fun CloudBackupSection(
    googleAccount: GoogleAccountInfo?,
    googleAccountLoading: Boolean,
    googleAccountError: String?,
    onGoogleSignIn: () -> Unit,
    onGoogleDisconnect: () -> Unit,
    onDismissGoogleError: () -> Unit,
    cloudBackupEnabled: Boolean,
    lastCloudBackupModifiedTime: String?,
    cloudSyncState: CloudSyncState,
    lastSyncedTimestamp: Long?,
    lastSyncError: String?,
    cloudSyncing: Boolean,
    driveAuthorizationLoading: Boolean,
    driveAuthorizationMessage: String?,
    onEnableCloudBackup: () -> Unit,
    onRetryCloudBackup: () -> Unit,
    onBackUpNow: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onDisableCloudBackup: () -> Unit,
    onDismissDriveMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val busy = driveAuthorizationLoading || cloudSyncing

    ManageSurface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (googleAccount == null) {
                SignedOutContent(loading = googleAccountLoading, onSignIn = onGoogleSignIn)
            } else {
                val menu = buildList {
                    if (cloudBackupEnabled) {
                        add(ManageMenuAction("Re-authorize access") { onEnableCloudBackup() })
                        add(ManageMenuAction("Turn off cloud backup") { confirmDisable = true })
                    }
                    add(ManageMenuAction("Disconnect account", destructive = true) { confirmDisconnect = true })
                }
                AccountHeader(
                    name = googleAccount.displayName.ifBlank { googleAccount.email },
                    email = googleAccount.email,
                    enabled = cloudBackupEnabled,
                    menu = menu
                )
                HorizontalDivider(color = LocalAppColors.current.border)

                if (!cloudBackupEnabled) {
                    EnableContent(loading = driveAuthorizationLoading, onEnable = onEnableCloudBackup)
                } else {
                    BackupContent(
                        state = if (cloudSyncing) CloudSyncState.SYNCING else cloudSyncState,
                        error = lastSyncError,
                        lastSyncedTimestamp = lastSyncedTimestamp,
                        lastBackupTime = lastCloudBackupModifiedTime,
                        busy = busy,
                        onRetry = onRetryCloudBackup,
                        onBackUpNow = onBackUpNow,
                        onRestore = { confirmRestore = true }
                    )
                }
            }

            if (googleAccountError != null || driveAuthorizationMessage != null) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    googleAccountError?.let {
                        CloudMessage(text = it, isError = true, onDismiss = onDismissGoogleError)
                    }
                    driveAuthorizationMessage?.let {
                        CloudMessage(
                            text = it,
                            isError = !it.startsWith("Cloud backup authorization denied", ignoreCase = true) &&
                                !it.startsWith("Cloud Backup authorization is enabled", ignoreCase = true) &&
                                !it.startsWith("Backup uploaded successfully", ignoreCase = true) &&
                                !it.startsWith("Backup downloaded", ignoreCase = true),
                            onDismiss = onDismissDriveMessage
                        )
                    }
                }
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google Drive?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You'll review the backup before anything changes, but continuing can replace data currently stored on this device.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    onRestoreFromDrive()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
        )
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Turn off cloud backup?", fontWeight = FontWeight.Bold) },
            text = { Text("Automatic backups will stop. Data on this device is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    onDisableCloudBackup()
                }) { Text("Turn off", color = LocalAppColors.current.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Cancel") } }
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Google account?", fontWeight = FontWeight.Bold) },
            text = { Text("Cloud backup will stop on this device. Data on this device is not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onGoogleDisconnect()
                }) { Text("Disconnect", color = LocalAppColors.current.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } }
        )
    }
}

// ───────────────────────── State 1: signed out ─────────────────────────

@Composable
private fun SignedOutContent(loading: Boolean, onSignIn: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(appColors.accentSoft, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Cloud, null, tint = appColors.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Back up to Google Drive", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = appColors.ink)
                Text("Never lose your expense history", fontSize = 12.5.sp, color = appColors.inkMuted)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Benefit("Automatic backups when your data changes")
            Benefit("Restore on a new phone in a few taps")
            Benefit("Saved to your own Google Drive")
        }

        Button(
            onClick = onSignIn,
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = appColors.accent,
                contentColor = appColors.accentInk
            )
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = appColors.accentInk)
            } else {
                Text("Continue with Google", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Benefit(text: String) {
    val appColors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = appColors.positive, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = appColors.ink)
    }
}

// ───────────────────────── Account header ─────────────────────────

@Composable
private fun AccountHeader(
    name: String,
    email: String,
    enabled: Boolean,
    menu: List<ManageMenuAction>
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(appColors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.take(1).uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = appColors.accent
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (enabled) ManagePill("ON", appColors.positiveSoft, appColors.positive)
            }
            Text(
                email,
                fontSize = 12.5.sp,
                color = appColors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ManageOverflowMenu(actions = menu)
    }
}

// ───────────────────────── State 2: signed in, backup off ─────────────────────────

@Composable
private fun EnableContent(loading: Boolean, onEnable: () -> Unit) {
    val appColors = LocalAppColors.current
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.CloudOff, null, tint = appColors.inkFaint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Cloud backup is off", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = appColors.ink)
                Text(
                    "Allow access to Google Drive and your data will be backed up automatically.",
                    fontSize = 12.5.sp,
                    color = appColors.inkMuted
                )
            }
        }
        Button(
            onClick = onEnable,
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = appColors.accent,
                contentColor = appColors.accentInk
            )
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = appColors.accentInk)
            } else {
                Text("Enable cloud backup", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ───────────────────────── State 3: backup on ─────────────────────────

private data class StatusPresentation(
    val title: String,
    val subtitle: String,
    val color: Color,
    val background: Color,
    val icon: ImageVector?
)

@Composable
private fun BackupContent(
    state: CloudSyncState,
    error: String?,
    lastSyncedTimestamp: Long?,
    lastBackupTime: String?,
    busy: Boolean,
    onRetry: () -> Unit,
    onBackUpNow: () -> Unit,
    onRestore: () -> Unit
) {
    val appColors = LocalAppColors.current

    val status = when (state) {
        CloudSyncState.SYNCED -> StatusPresentation(
            "All changes backed up", "Your data is safe in Google Drive.",
            appColors.positive, appColors.positiveSoft, Icons.Default.CheckCircle
        )
        CloudSyncState.PENDING -> StatusPresentation(
            "Backup pending", "Waiting for a network connection or for recent changes to settle.",
            appColors.accent, appColors.accentSoft, Icons.Default.Schedule
        )
        CloudSyncState.SYNCING -> StatusPresentation(
            "Backing up…", "Uploading your latest changes.",
            appColors.accent, appColors.accentSoft, null
        )
        CloudSyncState.ERROR -> StatusPresentation(
            "Needs attention", error ?: "The latest backup could not be completed.",
            appColors.negative, appColors.negativeSoft, Icons.Default.Warning
        )
        CloudSyncState.IDLE -> StatusPresentation(
            "Cloud backup is on", "Changes will be backed up automatically.",
            appColors.accent, appColors.accentSoft, Icons.Default.Cloud
        )
    }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Status panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(status.background)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                if (status.icon == null) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = status.color)
                } else {
                    Icon(status.icon, null, tint = status.color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(status.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = status.color)
                    Text(status.subtitle, fontSize = 12.sp, color = appColors.inkMuted)
                }
            }
            if (state == CloudSyncState.ERROR) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        if (needsReauthorization(error)) "Re-authorize" else "Try again",
                        color = status.color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Key facts
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CloudDetailRow("Last synced", formatLastSynced(lastSyncedTimestamp))
            CloudDetailRow("Latest Drive backup", formatDriveTime(lastBackupTime) ?: "No backup yet")
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onBackUpNow,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = appColors.accentInk
                )
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = appColors.accentInk)
                } else {
                    Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Back up now", fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedButton(
                onClick = onRestore,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, appColors.border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = appColors.ink)
            ) {
                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Restore", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CloudDetailRow(label: String, value: String) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = appColors.inkMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = appColors.ink)
    }
}

@Composable
private fun CloudMessage(text: String, isError: Boolean, onDismiss: () -> Unit) {
    val appColors = LocalAppColors.current
    val fg = if (isError) appColors.negative else appColors.accent
    val bg = if (isError) appColors.negativeSoft else appColors.accentSoft

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isError) Icons.Default.Warning else Icons.Default.Info,
            null,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = appColors.ink, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Text("✕", fontSize = 12.sp, color = appColors.inkMuted)
        }
    }
}

// ───────────────────────── Helpers ─────────────────────────

private fun needsReauthorization(error: String?): Boolean = error.orEmpty().let {
    it.contains("authoriz", ignoreCase = true) ||
        it.contains("approval", ignoreCase = true) ||
        it.contains("sign in", ignoreCase = true)
}

private fun formatLastSynced(timestamp: Long?): String {
    if (timestamp == null) return "Never"
    val elapsedMillis = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    val minutes = elapsedMillis / 60_000
    val hours = elapsedMillis / (60 * 60 * 1000)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes ${if (minutes == 1L) "min" else "mins"} ago"
        hours < 24 -> "$hours ${if (hours == 1L) "hr" else "hrs"} ago"
        else -> SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

/** Turns Drive's ISO-8601 UTC string (e.g. 2026-11-18T19:54:20.852Z) into local, readable time. */
private fun formatDriveTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = parser.parse(iso) ?: continue
            return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            // try next pattern
        }
    }
    return iso
}
