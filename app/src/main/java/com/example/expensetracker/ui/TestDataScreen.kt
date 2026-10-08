package com.example.expensetracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.TestNotificationEntity
import com.example.expensetracker.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestDataScreen(
    testNotifications: List<TestNotificationEntity>,
    onClearAll: () -> Unit,
    onUseAsTemplate: ((String) -> Unit)? = null,
    onNavigateBack: () -> Unit
) {
    val appColors = LocalAppColors.current
    var showClearDialog by remember { mutableStateOf(false) }

    val successCount = testNotifications.count { it.status == "SUCCESS" }
    val failCount = testNotifications.count { it.status != "SUCCESS" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Stats Summary & Action Bar
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = appColors.surface,
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, appColors.border, RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Summary Stats
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.accentSoft)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${testNotifications.size} Total",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = appColors.accent
                            )
                        )
                    }

                    // Success pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8F5E9))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$successCount SUCCESS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        )
                    }

                    // Fail pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFFEBEE))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$failCount FAIL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC62828)
                            )
                        )
                    }
                }

                // Clear Test Data Action Button
                if (testNotifications.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Test Data",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (testNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = appColors.inkFaint
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No test notifications captured yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = appColors.ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notifications received by Android NotificationListenerService will appear here in real time.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = appColors.inkFaint,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(testNotifications, key = { it.id }) { item ->
                    TestNotificationCard(item = item, onUseAsTemplate = onUseAsTemplate)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear all test notification data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("This will delete all captured raw test notifications. Actual ExpenseTracker transactions will not be affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53935))
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun TestNotificationCard(
    item: TestNotificationEntity,
    onUseAsTemplate: ((String) -> Unit)? = null
) {
    val appColors = LocalAppColors.current
    var expanded by remember { mutableStateOf(false) }

    val isSuccess = item.status == "SUCCESS"
    val statusBg = if (isSuccess) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    val statusText = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()) }
    val formattedTime = remember(item.receivedAt) { dateFormat.format(Date(item.receivedAt)) }

    val rawBodyText = item.bigText ?: item.text ?: item.title ?: ""

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSuccess) Color(0xFFA5D6A7) else appColors.border,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Status Badge + Method Badge + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge with icon + text
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Clear,
                                contentDescription = null,
                                tint = statusText,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.status,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = statusText
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Detection Method Pill (Template vs Generic)
                    val methodLabel = when (item.detectionMethod) {
                        "TEMPLATE" -> "Template Match"
                        "GENERIC" -> "Generic Detector"
                        else -> "Not Detected"
                    }
                    val methodBg = when (item.detectionMethod) {
                        "TEMPLATE" -> appColors.accentSoft
                        "GENERIC" -> Color(0xFFE3F2FD)
                        else -> appColors.surfaceAlt
                    }
                    val methodTextColor = when (item.detectionMethod) {
                        "TEMPLATE" -> appColors.accent
                        "GENERIC" -> Color(0xFF1976D2)
                        else -> appColors.inkFaint
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(methodBg)
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = methodLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = methodTextColor
                            )
                        )
                    }
                }

                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = appColors.inkFaint
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Preview Line
            if (!item.title.isNullOrBlank()) {
                Text(
                    text = "Title: ${item.title}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = appColors.ink
                )
            }

            if (!item.text.isNullOrBlank()) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = appColors.inkFaint,
                    maxLines = if (expanded) Int.MAX_VALUE else 2
                )
            }

            if (isSuccess && item.amount != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Parsed: ₹%.2f".format(item.amount),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        )
                        if (!item.merchant.isNullOrBlank()) {
                            Text(
                                text = " (${item.merchant})",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = appColors.ink
                                )
                            )
                        }
                    }

                    if (!item.matchedTemplateName.isNullOrBlank()) {
                        Text(
                            text = "[${item.matchedTemplateName}]",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = appColors.accent
                            )
                        )
                    }
                }
            }

            // "Use as Payment Template" Action Button
            if (rawBodyText.isNotBlank() && onUseAsTemplate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { onUseAsTemplate(rawBodyText) },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use as Payment Template", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Expand/Collapse Indicator
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Tap to collapse" else "[ Tap to expand full raw & detection data ]",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = appColors.accent
                    )
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = appColors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Detailed Sections when expanded
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider(color = appColors.border, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // SECTION 1: RAW NOTIFICATION
                    Text(
                        text = "RAW NOTIFICATION (RECEIVED FROM ANDROID)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = appColors.accent
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(appColors.bg)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RawFieldRow(label = "Package", value = item.packageName)
                            RawFieldRow(label = "Notification ID", value = item.notificationId.toString())
                            RawFieldRow(label = "Notification Tag", value = item.notificationTag ?: "(null)")
                            RawFieldRow(label = "Notification Key", value = item.notificationKey ?: "(null)")
                            RawFieldRow(label = "Timestamp", value = "$formattedTime (${item.receivedAt})")
                            RawFieldRow(label = "Title (EXTRA_TITLE)", value = item.title ?: "(null)")
                            RawFieldRow(label = "Text (EXTRA_TEXT)", value = item.text ?: "(null)")
                            RawFieldRow(label = "Big Text (EXTRA_BIG_TEXT)", value = item.bigText ?: "(null)")
                            RawFieldRow(label = "Text Lines (EXTRA_TEXT_LINES)", value = item.textLines ?: "(null)")
                            RawFieldRow(label = "Sub Text", value = item.subText ?: "(null)")
                            RawFieldRow(label = "Summary Text", value = item.summaryText ?: "(null)")
                            RawFieldRow(label = "Title Big", value = item.titleBig ?: "(null)")
                            RawFieldRow(label = "Other Extras", value = item.rawExtras ?: "(none)")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // SECTION 2: PROCESSING RESULT
                    Text(
                        text = "PROCESSING RESULT (APP PAYMENT DETECTION)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSuccess) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusBg)
                            .padding(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RawFieldRow(label = "Status", value = item.status, valueColor = statusText, isBold = true)
                            RawFieldRow(label = "Detection Method", value = item.detectionMethod ?: "GENERIC", isBold = true)
                            if (!item.matchedTemplateName.isNullOrBlank()) {
                                RawFieldRow(label = "Matched Template", value = item.matchedTemplateName, valueColor = appColors.accent, isBold = true)
                            }
                            if (!item.confidence.isNullOrBlank()) {
                                RawFieldRow(label = "Confidence", value = item.confidence)
                            }
                            RawFieldRow(label = "Detected Payment", value = if (item.detectedPayment) "YES" else "NO", valueColor = statusText, isBold = true)
                            RawFieldRow(label = "Amount", value = item.amount?.let { "₹%.2f".format(it) } ?: "(none)")
                            RawFieldRow(label = "Transaction Type", value = item.transactionType ?: "(none)")
                            RawFieldRow(label = "Merchant / Name", value = item.merchant ?: "(none)")
                            if (!item.accountNumber.isNullOrBlank()) {
                                RawFieldRow(label = "Account Number", value = item.accountNumber)
                            }
                            if (!item.referenceNumber.isNullOrBlank()) {
                                RawFieldRow(label = "Reference / UPI Ref", value = item.referenceNumber)
                            }
                            RawFieldRow(label = "Source", value = item.source ?: "(none)")
                            if (!item.failureReason.isNullOrBlank()) {
                                RawFieldRow(label = "Failure / Detection Reason", value = item.failureReason, valueColor = Color(0xFFC62828), isBold = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RawFieldRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    isBold: Boolean = false
) {
    val appColors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                color = appColors.inkFaint
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                color = if (valueColor != Color.Unspecified) valueColor else appColors.ink
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
