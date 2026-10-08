package com.example.expensetracker.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.expensetracker.data.local.RepaymentWithAllocations
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.model.ExpenseSplitCalculator
import com.example.expensetracker.model.MemberDebtSummary
import com.example.expensetracker.model.RepaymentRecordResult
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    expenseSplits: List<ExpenseSplitWithShares>,
    splitGroups: List<SplitGroupWithMembers>,
    transactions: List<Transaction>,
    repayments: List<RepaymentWithAllocations> = emptyList(),
    onRecordRepayment: (groupId: String, memberId: String, amountPaise: Long, paymentDate: String, linkedTransactionId: String?, note: String?) -> RepaymentRecordResult = { _, _, _, _, _, _ -> RepaymentRecordResult.Error("") },
    onUndoRepayment: (repaymentId: String) -> Unit = {},
    onNavigateToTransactions: () -> Unit = {}
) {
    val appColors = LocalAppColors.current

    val ledger = remember(expenseSplits, splitGroups, transactions, repayments) {
        ExpenseSplitCalculator.calculateSettleUpLedgerFromDomain(expenseSplits, splitGroups, transactions, repayments)
    }

    var selectedMemberSummary by remember { mutableStateOf<MemberDebtSummary?>(null) }
    var repaymentMemberSummary by remember { mutableStateOf<MemberDebtSummary?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Balances, 1 = History, 2 = Repayment History

    var undoSnackbarInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(undoSnackbarInfo) {
        if (undoSnackbarInfo != null) {
            delay(5000)
            undoSnackbarInfo = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Summary Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "TOTAL OUTSTANDING OWED TO YOU",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = appColors.inkFaint
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = CurrencyFormatter.formatPaise(ledger.totalOutstandingPaise),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp
                        ),
                        color = if (ledger.totalOutstandingPaise > 0) appColors.accent else appColors.ink
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${ledger.membersWhoOweCount} ${if (ledger.membersWhoOweCount == 1) "member owes" else "members owe"} you",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.ink
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${ledger.groupsWithDebtCount} ${if (ledger.groupsWithDebtCount == 1) "group" else "groups"}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.inkFaint
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Empty State View if no splits exist at all
            if (expenseSplits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(appColors.surfaceAlt, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = appColors.accent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No shared expenses yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = appColors.ink
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Split a transaction with a group to see what your friends owe you here.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            color = appColors.inkFaint,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToTransactions,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = appColors.accent,
                                contentColor = appColors.accentInk
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Go to Transactions", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else {
                // 3. Tab Selector: Balances vs Split History vs Repayments
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = appColors.surface,
                    contentColor = appColors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Balances (${ledger.memberBalances.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) appColors.accent else appColors.inkFaint,
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "Splits (${ledger.splitHistory.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) appColors.accent else appColors.inkFaint,
                                fontSize = 12.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Text(
                                "Repayments (${ledger.repaymentHistory.size})",
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 2) appColors.accent else appColors.inkFaint,
                                fontSize = 12.sp
                            )
                        }
                    )
                }

                if (selectedTab == 0) {
                    // Member Balances Tab
                    if (ledger.memberBalances.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = appColors.accent,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No outstanding debts",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = appColors.ink
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "All shared expenses are settled or no members currently owe you money.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = appColors.inkFaint
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(ledger.memberBalances, key = { it.memberId }) { summary ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedMemberSummary = summary },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = appColors.surface),
                                    border = BorderStroke(1.dp, appColors.border)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .background(appColors.surfaceAlt, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = appColors.accent,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = summary.memberName,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp
                                                        ),
                                                        color = appColors.ink
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Group: ${summary.groupName} • ${summary.itemizedExpenses.size} ${if (summary.itemizedExpenses.size == 1) "active expense" else "active expenses"}",
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                                        color = appColors.inkFaint
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = "Owes you",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = appColors.inkFaint
                                                )
                                                Text(
                                                    text = CurrencyFormatter.formatPaise(summary.totalOwedPaise),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 15.sp
                                                    ),
                                                    color = if (summary.totalOwedPaise > 0) appColors.accent else appColors.inkFaint
                                                )
                                            }
                                        }

                                        if (summary.unallocatedExcessPaise > 0L) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(appColors.accentSoft, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "Unallocated excess credit: ${CurrencyFormatter.formatPaise(summary.unallocatedExcessPaise)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = appColors.accent
                                                )
                                            }
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            color = appColors.border,
                                            thickness = 1.dp
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { selectedMemberSummary = summary },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text("View Details", fontSize = 12.sp, color = appColors.inkFaint)
                                            }

                                            Button(
                                                onClick = { repaymentMemberSummary = summary },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = appColors.accent,
                                                    contentColor = appColors.accentInk
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddCard,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Record Repayment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTab == 1) {
                    // Split History Tab
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(ledger.splitHistory, key = { it.splitId }) { historyItem ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                                border = BorderStroke(1.dp, appColors.border)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = historyItem.transactionTitle,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.5.sp
                                                ),
                                                color = appColors.ink,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${if (historyItem.transactionDate.isNotBlank()) historyItem.transactionDate + " • " else ""}${historyItem.groupName}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                                color = appColors.inkFaint
                                            )
                                        }

                                        Text(
                                            text = CurrencyFormatter.formatPaise(historyItem.transactionAmountPaise),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.5.sp
                                            ),
                                            color = appColors.ink
                                        )
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = appColors.border,
                                        thickness = 1.dp
                                    )

                                    Text(
                                        text = "Member Shares & Settlement Status:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = appColors.inkFaint,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        historyItem.memberShares.forEach { shareStatus ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${shareStatus.memberName}: ${CurrencyFormatter.formatPaise(shareStatus.originalSharePaise)}",
                                                    fontSize = 11.5.sp,
                                                    color = appColors.ink
                                                )

                                                if (shareStatus.isFullySettled) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(appColors.surfaceAlt, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Settled", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = appColors.accent)
                                                    }
                                                } else if (shareStatus.allocatedPaise > 0L) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(appColors.surfaceAlt, RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Paid ${CurrencyFormatter.formatPaise(shareStatus.allocatedPaise)}", fontSize = 10.sp, color = appColors.inkFaint)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Repayment History Tab
                    if (ledger.repaymentHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No repayment history",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = appColors.ink
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Recorded repayments and debt settlements will appear here.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = appColors.inkFaint
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(ledger.repaymentHistory, key = { it.repaymentId }) { repaymentItem ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = appColors.surface),
                                    border = BorderStroke(1.dp, appColors.border)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${repaymentItem.memberName} repaid ${CurrencyFormatter.formatPaise(repaymentItem.amountPaise)}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.5.sp
                                                    ),
                                                    color = appColors.accent
                                                )
                                                Text(
                                                    text = "${repaymentItem.paymentDate} • ${repaymentItem.groupName}",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                                    color = appColors.inkFaint
                                                )
                                            }
                                        }

                                        if (!repaymentItem.linkedTransactionTitle.isNullBrak()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Linked Transaction: ${repaymentItem.linkedTransactionTitle}",
                                                fontSize = 11.sp,
                                                color = appColors.inkFaint
                                            )
                                        }

                                        if (!repaymentItem.note.isNullBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Note: ${repaymentItem.note}",
                                                fontSize = 11.sp,
                                                color = appColors.inkFaint
                                            )
                                        }

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            color = appColors.border,
                                            thickness = 1.dp
                                        )

                                        if (repaymentItem.itemizedAllocations.isNotEmpty()) {
                                            Text(
                                                text = "Allocations against expenses:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = appColors.inkFaint,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            repaymentItem.itemizedAllocations.forEach { (title, paise) ->
                                                Text(
                                                    text = "• Cleared ${CurrencyFormatter.formatPaise(paise)} for $title",
                                                    fontSize = 11.5.sp,
                                                    color = appColors.ink
                                                )
                                            }
                                        }

                                        if (repaymentItem.unallocatedAmountPaise > 0L) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(appColors.accentSoft, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Unallocated excess credit: ${CurrencyFormatter.formatPaise(repaymentItem.unallocatedAmountPaise)}",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = appColors.accent
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5-Second Undo Snackbar
        undoSnackbarInfo?.let { (repaymentId, message) ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.ink),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message,
                            color = appColors.surface,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        TextButton(
                            onClick = {
                                onUndoRepayment(repaymentId)
                                undoSnackbarInfo = null
                            }
                        ) {
                            Text("UNDO", color = appColors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Member Debt Details Modal Dialog
    selectedMemberSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { selectedMemberSummary = null },
            title = {
                Column {
                    Text(
                        text = summary.memberName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = appColors.ink
                    )
                    Text(
                        text = "Group: ${summary.groupName} — Total Owed: ${CurrencyFormatter.formatPaise(summary.totalOwedPaise)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = appColors.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Itemized Shared Expenses:",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appColors.inkFaint,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (summary.itemizedExpenses.isEmpty()) {
                        Text("No active unsettled expenses for this member.", fontSize = 12.sp, color = appColors.inkFaint)
                    } else {
                        summary.itemizedExpenses.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                                border = BorderStroke(1.dp, appColors.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.transactionTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            color = appColors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Original share: ${CurrencyFormatter.formatPaise(item.originalShareAmountPaise)}${if (item.allocatedPaise > 0L) " • Paid: ${CurrencyFormatter.formatPaise(item.allocatedPaise)}" else ""}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = appColors.inkFaint
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = CurrencyFormatter.formatPaise(item.remainingAmountPaise),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            color = appColors.accent
                                        )
                                        Text("Remaining", fontSize = 9.5.sp, color = appColors.inkFaint)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMemberSummary = null }) {
                    Text("Close", color = appColors.ink, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {}
        )
    }

    // Record Repayment Dialog
    repaymentMemberSummary?.let { summary ->
        RecordRepaymentDialog(
            memberSummary = summary,
            transactions = transactions,
            existingRepayments = repayments,
            onConfirm = { amountPaise, date, linkedTxId, note ->
                val result = onRecordRepayment(
                    summary.groupId,
                    summary.memberId,
                    amountPaise,
                    date,
                    linkedTxId,
                    note
                )
                if (result is RepaymentRecordResult.Success) {
                    val repaymentId = result.repayment.id
                    val memberName = summary.memberName
                    val amountStr = CurrencyFormatter.formatPaise(amountPaise)
                    repaymentMemberSummary = null
                    undoSnackbarInfo = Pair(repaymentId, "Repayment of $amountStr from $memberName recorded")
                    true
                } else false
            },
            onDismiss = { repaymentMemberSummary = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordRepaymentDialog(
    memberSummary: MemberDebtSummary,
    transactions: List<Transaction>,
    existingRepayments: List<RepaymentWithAllocations>,
    onConfirm: (amountPaise: Long, date: String, linkedTxId: String?, note: String?) -> Boolean,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current

    val todayStr = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date())
    }

    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(todayStr) }
    var noteText by remember { mutableStateOf("") }
    var selectedLinkedTx by remember { mutableStateOf<Transaction?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Filter incoming transactions not already linked to another repayment
    val linkedTxIds = remember(existingRepayments) {
        existingRepayments.mapNotNull { it.repayment.linkedTransactionId }.toSet()
    }

    val eligibleIncomeTransactions = remember(transactions, linkedTxIds) {
        transactions.filter { tx ->
            tx.type == TransactionType.INCOME && !linkedTxIds.contains(tx.id)
        }
    }

    var dropdownExpanded by remember { mutableStateOf(false) }

    val amountDouble = amountText.toDoubleOrNull() ?: 0.0
    val amountPaise = ExpenseSplitCalculator.rupeesToPaise(amountDouble)
    val isValidAmount = amountPaise > 0L

    val debtClearedPaise = minOf(amountPaise, memberSummary.totalOwedPaise)
    val unallocatedExcessPaise = maxOf(0L, amountPaise - memberSummary.totalOwedPaise)
    val remainingDebtPaise = maxOf(0L, memberSummary.totalOwedPaise - amountPaise)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Record Repayment",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = appColors.ink
                )
                Text(
                    text = "${memberSummary.memberName} (${memberSummary.groupName})",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = appColors.inkFaint
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Outstanding Debt Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                    border = BorderStroke(1.dp, appColors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Outstanding Debt:", fontSize = 12.sp, color = appColors.inkFaint)
                        Text(
                            text = CurrencyFormatter.formatPaise(memberSummary.totalOwedPaise),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = appColors.accent
                        )
                    }
                }

                // Amount Text Field
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = input
                            errorMessage = null
                        }
                    },
                    label = { Text("Repayment Amount (₹)") },
                    placeholder = { Text("e.g. 70.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Date Text Field
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Payment Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Optional Linked Transaction Dropdown
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedLinkedTx?.let { "${it.title} (₹${it.amount})" } ?: "None (Manual Repayment)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Link Incoming Credit (Optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None (Manual Repayment)") },
                            onClick = {
                                selectedLinkedTx = null
                                dropdownExpanded = false
                            }
                        )

                        eligibleIncomeTransactions.forEach { tx ->
                            DropdownMenuItem(
                                text = { Text("${tx.title} — ₹${tx.amount} (${tx.date})") },
                                onClick = {
                                    selectedLinkedTx = tx
                                    if (amountText.isEmpty()) {
                                        amountText = tx.amount.toString()
                                    }
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Optional Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Reference (Optional)") },
                    placeholder = { Text("e.g. GPay transfer for mess") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Real-time settlement breakdown preview
                if (isValidAmount) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.bg),
                        border = BorderStroke(1.dp, appColors.border)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Settlement Preview:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = appColors.inkFaint)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Debt to be cleared:", fontSize = 11.5.sp, color = appColors.ink)
                                Text(CurrencyFormatter.formatPaise(debtClearedPaise), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = appColors.accent)
                            }
                            if (unallocatedExcessPaise > 0L) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Unallocated excess credit:", fontSize = 11.5.sp, color = appColors.ink)
                                    Text(CurrencyFormatter.formatPaise(unallocatedExcessPaise), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = appColors.accent)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Remaining debt after saving:", fontSize = 11.5.sp, color = appColors.ink)
                                Text(CurrencyFormatter.formatPaise(remainingDebtPaise), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = appColors.ink)
                            }
                        }
                    }
                }

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = appColors.negative,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isValidAmount) {
                        errorMessage = "Please enter a valid positive repayment amount."
                        return@Button
                    }
                    val success = onConfirm(
                        amountPaise,
                        dateText.ifBlank { todayStr },
                        selectedLinkedTx?.id,
                        noteText.ifBlank { null }
                    )
                    if (!success) {
                        errorMessage = "Failed to record repayment. Ensure transaction is not already linked."
                    }
                },
                enabled = isValidAmount,
                colors = ButtonDefaults.buttonColors(
                    containerColor = appColors.accent,
                    contentColor = appColors.accentInk
                )
            ) {
                Text("Confirm Repayment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = appColors.ink)
            }
        }
    )
}

private fun String?.isNullBrak(): Boolean = this.isNullOrBlank()
