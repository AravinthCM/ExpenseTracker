package com.example.expensetracker.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.GPayMemberMappingEntity
import com.example.expensetracker.data.local.GPayRequestEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.model.GPayMatchStatus
import com.example.expensetracker.model.GPayProcessingStatus
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GPayRequestsScreen(
    requests: List<GPayRequestEntity>,
    splitGroups: List<SplitGroupWithMembers>,
    memberMappings: List<GPayMemberMappingEntity>,
    transactions: List<Transaction>,
    onAssignGroup: (requestId: String, groupId: String) -> Unit = { _, _ -> },
    onAssignMember: (requestId: String, memberId: String, remember: Boolean) -> Unit = { _, _, _ -> },
    onDismissRequest: (requestId: String) -> Unit = {},
    onCloseRequest: (requestId: String, transactionId: String?) -> Unit = { _, _ -> },
    onConfirmExpense: (requestId: String) -> String? = { null },
    onConfirmRepayment: (requestId: String) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val appColors = LocalAppColors.current
    var detailRequestId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pendingCount = requests.count { it.processingStatus == GPayProcessingStatus.PENDING }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = appColors.ink)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "GPay Requests",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = appColors.ink
                )
                Text(
                    text = "$pendingCount pending review",
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors.inkFaint
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (requests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No GPay payment requests captured yet.",
                    color = appColors.inkFaint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(requests, key = { it.id }) { request ->
                    GPayRequestCard(
                        request = request,
                        splitGroups = splitGroups,
                        onClick = { detailRequestId = request.id }
                    )
                }
            }
        }
    }

    val detailRequest = requests.firstOrNull { it.id == detailRequestId }

    detailRequest?.let { request ->
        GPayRequestDetailDialog(
            request = request,
            splitGroups = splitGroups,
            memberMappings = memberMappings,
            transactions = transactions,
            onDismiss = { detailRequestId = null },
            onAssignGroup = { groupId -> onAssignGroup(request.id, groupId) },
            onAssignMember = { memberId, remember -> onAssignMember(request.id, memberId, remember) },
            onDismissRequest = {
                onDismissRequest(request.id)
                detailRequestId = null
            },
            onCloseRequest = { transactionId ->
                onCloseRequest(request.id, transactionId)
                detailRequestId = null
            },
            onConfirmExpense = {
                val error = onConfirmExpense(request.id)
                if (error != null) {
                    errorMessage = error
                } else {
                    detailRequestId = null
                }
            },
            onConfirmRepayment = {
                onConfirmRepayment(request.id)
                detailRequestId = null
            }
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Couldn't add expense") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun matchStatusLabel(status: String): String = when (status) {
    GPayMatchStatus.AUTO_MATCHED -> "Matched"
    GPayMatchStatus.NEEDS_GROUP -> "Needs group"
    GPayMatchStatus.NEEDS_MEMBER -> "Needs member"
    GPayMatchStatus.AMBIGUOUS_GROUP -> "Ambiguous group"
    GPayMatchStatus.AMBIGUOUS_MEMBER -> "Ambiguous member"
    else -> status
}

@Composable
private fun processingStatusLabel(status: String): String = when (status) {
    GPayProcessingStatus.PENDING -> "Pending review"
    GPayProcessingStatus.ADDED_AS_EXPENSE -> "Added as group expense"
    GPayProcessingStatus.DISMISSED -> "Dismissed"
    GPayProcessingStatus.DUPLICATE -> "Duplicate"
    else -> status
}

@Composable
private fun GPayRequestCard(
    request: GPayRequestEntity,
    splitGroups: List<SplitGroupWithMembers>,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val groupName = splitGroups.firstOrNull { it.group.id == request.matchedGroupId }?.group?.name
        ?: request.groupNameRaw ?: "Unknown group"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surface),
        border = BorderStroke(1.dp, appColors.border),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.requesterRawName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = appColors.ink
                )
                Text(
                    text = CurrencyFormatter.formatPaise(request.amountPaise),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = appColors.accent
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${request.description} • $groupName",
                fontSize = 12.5.sp,
                color = appColors.inkFaint
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(text = matchStatusLabel(request.matchStatus), appColors = appColors)
                StatusChip(text = processingStatusLabel(request.processingStatus), appColors = appColors)
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, appColors: com.example.expensetracker.ui.theme.AppColors) {
    Box(
        modifier = Modifier
            .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = appColors.ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GPayRequestDetailDialog(
    request: GPayRequestEntity,
    splitGroups: List<SplitGroupWithMembers>,
    memberMappings: List<GPayMemberMappingEntity>,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onAssignGroup: (groupId: String) -> Unit,
    onAssignMember: (memberId: String, remember: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onCloseRequest: (transactionId: String?) -> Unit,
    onConfirmExpense: () -> Unit,
    onConfirmRepayment: () -> Unit
) {
    val appColors = LocalAppColors.current
    val activeGroups = splitGroups.filter { group -> group.members.any { !it.isMe } }
    val resolvedGroup = splitGroups.firstOrNull { it.group.id == request.matchedGroupId }
    var groupExpanded by remember { mutableStateOf(false) }
    var selectedGroupId by remember { mutableStateOf(request.matchedGroupId) }
    var memberExpanded by remember { mutableStateOf(false) }
    var selectedMemberId by remember { mutableStateOf(request.matchedMemberId) }
    var rememberMapping by remember { mutableStateOf(true) }
    var closeTxExpanded by remember { mutableStateOf(false) }
    var selectedCloseTxId by remember { mutableStateOf<String?>(request.paymentConfirmationTransactionId) }

    val closeCandidates = remember(transactions, request.id) {
        transactions
            .asSequence()
            .filter { it.id != request.linkedTransactionId }
            .filter { it.type.name == "EXPENSE" }
            .filter { (it.amount * 100.0).roundToLong() == request.amountPaise }
            .sortedByDescending { it.timestamp }
            .take(20)
            .toList()
    }

    val selectedCloseTx = closeCandidates.firstOrNull { it.id == selectedCloseTxId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review request") },
        text = {
            Column {
                Text("Requester: ${request.requesterRawName}", color = appColors.ink)
                Text("Amount: ${CurrencyFormatter.formatPaise(request.amountPaise)}", color = appColors.ink)
                Text("Description: ${request.description}", color = appColors.ink)
                Text("Status: ${processingStatusLabel(request.processingStatus)} • ${matchStatusLabel(request.matchStatus)}", color = appColors.inkFaint)
                Spacer(modifier = Modifier.height(12.dp))

                if (request.matchStatus == GPayMatchStatus.NEEDS_GROUP || request.matchStatus == GPayMatchStatus.AMBIGUOUS_GROUP) {
                    Text("Select group", fontWeight = FontWeight.SemiBold, color = appColors.ink)
                    ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
                        OutlinedTextField(
                            value = activeGroups.firstOrNull { it.group.id == selectedGroupId }?.group?.name
                                ?: if (activeGroups.isEmpty()) "No active groups" else "",
                            onValueChange = {},
                            readOnly = true,
                            enabled = activeGroups.isNotEmpty(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) }
                        )
                        ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                            if (activeGroups.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No active groups") },
                                    onClick = { groupExpanded = false },
                                    enabled = false
                                )
                            }
                            activeGroups.forEach { g ->
                                DropdownMenuItem(text = { Text(g.group.name) }, onClick = {
                                    selectedGroupId = g.group.id
                                    groupExpanded = false
                                    onAssignGroup(g.group.id)
                                })
                            }
                        }
                    }
                    if (activeGroups.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No active groups available. Create a split group first.",
                            fontSize = 12.sp,
                            color = appColors.inkFaint
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (resolvedGroup != null) {
                    Text("Group: ${resolvedGroup.group.name}", color = appColors.ink)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (resolvedGroup != null && (request.matchStatus == GPayMatchStatus.NEEDS_MEMBER || request.matchStatus == GPayMatchStatus.AMBIGUOUS_MEMBER)) {
                    Text("Select member", fontWeight = FontWeight.SemiBold, color = appColors.ink)
                    val candidates = resolvedGroup.members.filter { !it.isMe }
                    ExposedDropdownMenuBox(expanded = memberExpanded, onExpandedChange = { memberExpanded = it }) {
                        OutlinedTextField(
                            value = candidates.firstOrNull { it.id == selectedMemberId }?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberExpanded) }
                        )
                        ExposedDropdownMenu(expanded = memberExpanded, onDismissRequest = { memberExpanded = false }) {
                            candidates.forEach { m ->
                                DropdownMenuItem(text = { Text(m.name) }, onClick = {
                                    selectedMemberId = m.id
                                    memberExpanded = false
                                    onAssignMember(m.id, rememberMapping)
                                })
                            }
                        }
                    }
                }

                if (request.processingStatus == GPayProcessingStatus.ADDED_AS_EXPENSE && request.linkedTransactionId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Added as a group expense in ${resolvedGroup?.group?.name ?: ""}.", color = appColors.inkFaint)
                }

                if (request.paymentConfirmationTransactionId != null && request.repaymentId == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A matching GPay payment was captured — you can confirm it as the repayment.", color = appColors.accent)
                }
                if (request.repaymentId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Repayment recorded for this request.", color = appColors.inkFaint)
                }

                if (request.processingStatus == GPayProcessingStatus.PENDING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Close with transaction (optional)", fontWeight = FontWeight.SemiBold, color = appColors.ink)
                    ExposedDropdownMenuBox(expanded = closeTxExpanded, onExpandedChange = { closeTxExpanded = it }) {
                        OutlinedTextField(
                            value = selectedCloseTx?.let {
                                "${it.title} • ${it.date} • ${CurrencyFormatter.formatPaise((it.amount * 100.0).roundToLong())}"
                            } ?: "No transaction selected",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = closeTxExpanded) }
                        )
                        ExposedDropdownMenu(expanded = closeTxExpanded, onDismissRequest = { closeTxExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("No transaction") },
                                onClick = {
                                    selectedCloseTxId = null
                                    closeTxExpanded = false
                                }
                            )
                            closeCandidates.forEach { tx ->
                                DropdownMenuItem(
                                    text = { Text("${tx.title} • ${tx.date} • ₹${tx.amount}") },
                                    onClick = {
                                        selectedCloseTxId = tx.id
                                        closeTxExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (closeCandidates.isEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No matching transactions found for this amount.",
                            fontSize = 12.sp,
                            color = appColors.inkFaint
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (request.processingStatus == GPayProcessingStatus.PENDING &&
                    request.matchStatus == GPayMatchStatus.AUTO_MATCHED
                ) {
                    Button(onClick = onConfirmExpense, colors = ButtonDefaults.buttonColors(containerColor = appColors.accent, contentColor = appColors.accentInk)) {
                        Text("Add expense")
                    }
                }
                if (request.paymentConfirmationTransactionId != null && request.repaymentId == null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirmRepayment, colors = ButtonDefaults.buttonColors(containerColor = appColors.accent, contentColor = appColors.accentInk)) {
                        Text("Confirm repayment")
                    }
                }
                if (request.processingStatus == GPayProcessingStatus.PENDING) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = { onCloseRequest(selectedCloseTxId) }) {
                        Text("Mark closed")
                    }
                }
            }
        },
        dismissButton = {
            Row {
                if (request.processingStatus == GPayProcessingStatus.PENDING) {
                    TextButton(onClick = onDismissRequest) { Text("Dismiss") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}
