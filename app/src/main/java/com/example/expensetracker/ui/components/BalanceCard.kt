package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

data class AccountBalanceSnapshot(
    val accountId: String,
    val accountName: String,
    val isPrimary: Boolean,
    val totalBalance: Double,
    val currentMonthIncome: Double,
    val currentMonthExpense: Double
)

@Composable
fun BalanceCard(
    accounts: List<AccountEntity> = emptyList(),
    accountSnapshots: List<AccountBalanceSnapshot> = emptyList(),
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

    val activeAccounts = accounts.filter { !it.isArchived }
    val cards = accountSnapshots.ifEmpty {
        listOf(
            AccountBalanceSnapshot(
                accountId = "all_accounts",
                accountName = "All Accounts",
                isPrimary = false,
                totalBalance = totalBalance,
                currentMonthIncome = totalIncome,
                currentMonthExpense = totalExpenses
            )
        )
    }
    val selectedCardId = selectedAccountId ?: "all_accounts"
    val selectedPage = cards.indexOfFirst { it.accountId == selectedCardId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = selectedPage,
        pageCount = { cards.size }
    )

    if (activeAccounts.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RadiusLg,
            colors = CardDefaults.cardColors(containerColor = heroBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = "No accounts configured",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onManageAccountsClick,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Manage Accounts →",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
        return
    }

    LaunchedEffect(selectedPage, cards.size) {
        if (cards.isNotEmpty() && pagerState.currentPage != selectedPage) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    LaunchedEffect(pagerState, cards) {
        snapshotFlow { pagerState.settledPage }
            .filter { it in cards.indices }
            .map { cards[it].accountId }
            .distinctUntilChanged()
            .collect { id ->
                if (id == "all_accounts") onSelectAccount(null) else onSelectAccount(id)
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            pageSpacing = 12.dp
        ) { page ->
            val snapshot = cards[page]
            val accountEntity = activeAccounts.find { it.id == snapshot.accountId }
            val isSelected = pagerState.settledPage == page

            AccountBalanceSlide(
                snapshot = snapshot,
                isSelected = isSelected,
                isBalanceVisible = isBalanceVisible,
                heroBg = heroBg,
                onToggleBalanceVisibility = onToggleBalanceVisibility,
                onCardClick = {
                    if (snapshot.accountId == "all_accounts") onSelectAccount(null) else onSelectAccount(snapshot.accountId)
                },
                primaryLabel = accountEntity?.isPrimary == true
            )
        }

        if (cards.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(cards.size) { index ->
                    val isActive = pagerState.settledPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isActive) 8.dp else 6.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color.White.copy(alpha = if (isActive) 1f else 0.38f))
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountBalanceSlide(
    snapshot: AccountBalanceSnapshot,
    isSelected: Boolean,
    isBalanceVisible: Boolean,
    heroBg: Color,
    onToggleBalanceVisibility: () -> Unit,
    onCardClick: () -> Unit,
    primaryLabel: Boolean
) {
    val displayBalance = CurrencyFormatter.format(snapshot.totalBalance, isBalanceVisible)
    val displayIncome = CurrencyFormatter.format(snapshot.currentMonthIncome, isBalanceVisible)
    val displayExpense = CurrencyFormatter.format(snapshot.currentMonthExpense, isBalanceVisible)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp),
        onClick = onCardClick,
        shape = RadiusLg,
        colors = CardDefaults.cardColors(
            containerColor = heroBg
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) Color.White.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (primaryLabel) "${snapshot.accountName} • Primary" else snapshot.accountName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = Color.White
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onToggleBalanceVisibility() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isBalanceVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isBalanceVisible) "Hide balance" else "Show balance",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = displayBalance,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 29.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                MetricMiniCard(
                    title = "Income",
                    value = displayIncome,
                    accent = Color(0xFF62E4AA),
                    modifier = Modifier.weight(1f)
                )
                MetricMiniCard(
                    title = "Expense",
                    value = displayExpense,
                    accent = Color(0xFFFF8A8A),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricMiniCard(
    title: String,
    subtitle: String? = null,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(58.dp),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, RoundedCornerShape(99.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White.copy(alpha = 0.88f)
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = Color.White.copy(alpha = 0.62f)
                        )
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
