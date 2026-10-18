package com.example.expensetracker.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.AccountEntity
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.theme.RadiusLg
import com.example.expensetracker.ui.util.CurrencyFormatter

@Composable
fun BalanceCard(
    accounts: List<AccountEntity> = emptyList(),
    selectedAccountId: String? = null,
    isBalanceVisible: Boolean = true,
    totalBalance: Double,
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    onSelectAccount: (String?) -> Unit = {},
    onToggleBalanceVisibility: () -> Unit = {},
    onManageAccountsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val heroBg = if (appColors.isDark) appColors.accent else Color(0xFF12172B)
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    val activeAccounts = accounts.filter { !it.isArchived }
    val selectedAccountName = when {
        selectedAccountId == null -> "All Accounts"
        else -> activeAccounts.find { it.id == selectedAccountId }?.name ?: "All Accounts"
    }

    val displayBalance = CurrencyFormatter.format(totalBalance, isBalanceVisible)
    val displayIncome = CurrencyFormatter.format(totalIncome, isBalanceVisible)
    val displayExpenses = CurrencyFormatter.format(totalExpenses, isBalanceVisible)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RadiusLg,
        colors = CardDefaults.cardColors(
            containerColor = heroBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // Header Row: Account Selector + Visibility Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { accountDropdownExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = selectedAccountName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Account",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Accounts (Combined)", fontWeight = if (selectedAccountId == null) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onSelectAccount(null)
                                accountDropdownExpanded = false
                            }
                        )
                        activeAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (acc.isPrimary) "${acc.name} (Primary)" else acc.name,
                                        fontWeight = if (selectedAccountId == acc.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSelectAccount(acc.id)
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Balance Visibility Toggle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onToggleBalanceVisibility() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                            tint = if (isBalanceVisible) Color.White.copy(alpha = 0.8f) else appColors.positive,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBalanceVisible) "Hide" else "Show",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (selectedAccountId == null) "Total active balance (Combined)" else "Current balance",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.White.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displayBalance,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Income Chip
                HeroChip(
                    label = "Income",
                    value = displayIncome,
                    icon = Icons.Default.KeyboardArrowUp,
                    modifier = Modifier.weight(1f)
                )

                // Expenses Chip
                HeroChip(
                    label = "Expenses",
                    value = displayExpenses,
                    icon = Icons.Default.KeyboardArrowDown,
                    modifier = Modifier.weight(1f)
                )
            }

            if (accounts.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onManageAccountsClick,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = "No accounts configured. Manage Accounts →",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                ),
                color = Color.White
            )
        }
    }
}
