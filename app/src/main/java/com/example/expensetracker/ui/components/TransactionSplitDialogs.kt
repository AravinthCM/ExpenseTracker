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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.ExpenseSplitWithShares
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.model.ExpenseSplitCalculator
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectSplitGroupDialog(
    transaction: Transaction,
    splitGroups: List<SplitGroupWithMembers>,
    existingSplits: List<ExpenseSplitWithShares>,
    onSelectGroup: (SplitGroupWithMembers) -> Unit,
    onCreateGroupClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current

    // Group IDs that already have a split for this transaction
    val alreadySplitGroupIds = remember(existingSplits, transaction.id) {
        existingSplits
            .filter { it.split.transactionId == transaction.id }
            .map { it.split.groupId }
            .toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Split Expense",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = appColors.ink
                )
                Text(
                    text = "${transaction.title} — ${CurrencyFormatter.format(transaction.amount)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = appColors.inkFaint
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                if (splitGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(appColors.surfaceAlt, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = appColors.inkMuted,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No split groups found",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = appColors.ink
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a split group to configure members before splitting expenses.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    onCreateGroupClick()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.accent,
                                    contentColor = appColors.accentInk
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Create Group", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Select a saved group to split this expense:",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = appColors.inkFaint,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(splitGroups, key = { it.group.id }) { groupWithMembers ->
                            val isAlreadySplit = alreadySplitGroupIds.contains(groupWithMembers.group.id)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAlreadySplit) {
                                        onSelectGroup(groupWithMembers)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAlreadySplit) appColors.surfaceAlt else appColors.cardBackground
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isAlreadySplit) appColors.border else appColors.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = groupWithMembers.group.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            ),
                                            color = if (isAlreadySplit) appColors.inkFaint else appColors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${groupWithMembers.members.size} members (${groupWithMembers.members.joinToString { if (it.isMe) "Me" else it.name }})",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                            color = appColors.inkFaint,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (isAlreadySplit) {
                                        Box(
                                            modifier = Modifier
                                                .background(appColors.surfaceAlt, RoundedCornerShape(100.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Already split",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = appColors.inkFaint
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = appColors.ink)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureExpenseSplitDialog(
    transaction: Transaction,
    groupWithMembers: SplitGroupWithMembers,
    errorMessage: String? = null,
    onConfirmEqualSplit: () -> Unit,
    onConfirmCustomSplit: (Map<String, Long>) -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current
    var selectedModeTab by remember { mutableIntStateOf(0) } // 0 = Equal, 1 = Custom

    val totalPaise = remember(transaction.amount) {
        ExpenseSplitCalculator.rupeesToPaise(transaction.amount)
    }

    // Custom Share Inputs (Map memberId -> String input in Rupees)
    val customInputs = remember(groupWithMembers, transaction.amount) {
        val members = groupWithMembers.members
        val equalRupees = (transaction.amount / members.size)
        val initialMap = mutableStateMapOf<String, String>()
        members.forEach { member ->
            initialMap[member.id] = String.format(Locale.US, "%.2f", equalRupees)
        }
        initialMap
    }

    // Calculation validation for custom split mode
    val (parsedCustomShares, customSumPaise, isCustomValid, customValidationError) = remember(customInputs.toMap(), totalPaise) {
        var valid = true
        var err: String? = null
        val sharesMap = mutableMapOf<String, Long>()
        var sum = 0L

        for (member in groupWithMembers.members) {
            val inputStr = customInputs[member.id]?.trim() ?: ""
            val doubleVal = inputStr.toDoubleOrNull()

            if (doubleVal == null || doubleVal <= 0.0) {
                valid = false
                err = "All member shares must be greater than ₹0.00"
                break
            }

            val paiseVal = ExpenseSplitCalculator.rupeesToPaise(doubleVal)
            sharesMap[member.id] = paiseVal
            sum += paiseVal
        }

        if (valid && sum != totalPaise) {
            valid = false
            val diffRupees = CurrencyFormatter.format(ExpenseSplitCalculator.paiseToRupees(Math.abs(sum - totalPaise)))
            err = if (sum < totalPaise) {
                "Total is ₹$diffRupees short of expense total"
            } else {
                "Total exceeds expense total by ₹$diffRupees"
            }
        }

        Tuple4(sharesMap, sum, valid, err)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Configure Split — ${groupWithMembers.group.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = appColors.ink
                )
                Text(
                    text = "${transaction.title}: ${CurrencyFormatter.format(transaction.amount)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = appColors.inkFaint
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Mode Tabs
                TabRow(
                    selectedTabIndex = selectedModeTab,
                    containerColor = appColors.surfaceAlt,
                    contentColor = appColors.accent,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Tab(
                        selected = selectedModeTab == 0,
                        onClick = { selectedModeTab = 0 },
                        text = {
                            Text(
                                "Split Equal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedModeTab == 1,
                        onClick = { selectedModeTab = 1 },
                        text = {
                            Text(
                                "Split by Amount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    )
                }

                if (selectedModeTab == 0) {
                    // Equal Split Mode Preview
                    val members = groupWithMembers.members.sortedBy { it.id }
                    val basePaise = totalPaise / members.size
                    val remainder = (totalPaise % members.size).toInt()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Equal Allocation Preview",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            members.forEachIndexed { idx, member ->
                                val sharePaise = basePaise + (if (idx < remainder) 1L else 0L)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (member.isMe) "Me (${member.name})" else member.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (member.isMe) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = appColors.ink
                                        )
                                    }
                                    Text(
                                        text = CurrencyFormatter.format(ExpenseSplitCalculator.paiseToRupees(sharePaise)),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = appColors.ink
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Shares Sum:",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = appColors.inkFaint
                                )
                                Text(
                                    text = CurrencyFormatter.format(transaction.amount),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = appColors.accent
                                )
                            }
                        }
                    }
                } else {
                    // Custom Split Mode Input
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Enter Share Amount for Each Member",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            groupWithMembers.members.forEach { member ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (member.isMe) "Me (${member.name})" else member.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (member.isMe) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = appColors.ink,
                                        modifier = Modifier.weight(1f)
                                    )

                                    OutlinedTextField(
                                        value = customInputs[member.id] ?: "",
                                        onValueChange = { newValue ->
                                            customInputs[member.id] = newValue
                                        },
                                        prefix = { Text("₹", fontSize = 12.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.width(130.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Summary bar
                            val enteredSumRupees = ExpenseSplitCalculator.paiseToRupees(customSumPaise)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Entered: ${CurrencyFormatter.format(enteredSumRupees)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (customSumPaise == totalPaise) appColors.positive else appColors.negative
                                )
                                Text(
                                    text = "Target: ${CurrencyFormatter.format(transaction.amount)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = appColors.inkFaint
                                )
                            }
                        }
                    }

                    customValidationError?.let { errorText ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = appColors.negative,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // Error message from ViewModel / Backend save attempt
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appColors.negativeSoft, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = err,
                            color = appColors.negative,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedModeTab == 0 || (selectedModeTab == 1 && isCustomValid),
                onClick = {
                    if (selectedModeTab == 0) {
                        onConfirmEqualSplit()
                    } else {
                        onConfirmCustomSplit(parsedCustomShares)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = appColors.accentInk
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Confirm Split", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", color = appColors.ink)
            }
        }
    )
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
