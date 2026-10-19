package com.example.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.theme.RadiusMd
import com.example.expensetracker.ui.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItem(
    transaction: Transaction,
    onCardClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    isSelected: Boolean = false,
    splitCount: Int = 0,
    accountName: String? = null,
    showBorder: Boolean = true,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isSelected) Modifier.border(2.dp, appColors.accent, RadiusMd) else Modifier)
            .combinedClickable(
                onClick = { onCardClick() },
                onLongClick = { onLongClick() }
            ),
        shape = RadiusMd,
        colors = CardDefaults.cardColors(
            containerColor = appColors.surface
        ),
        border = if (showBorder) BorderStroke(1.dp, appColors.border) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge (38dp, radius 11dp)
            val (icon, tintColor, bgColor) = getCategoryIconAndColor(transaction.category, appColors.isDark)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(color = bgColor, shape = RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = transaction.title,
                    tint = tintColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title and Subtitle Caption
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.8.sp
                    ),
                    color = appColors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val categoryName = transaction.displayCategoryName
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        ),
                        color = appColors.inkFaint
                    )
                    if (!accountName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(appColors.surfaceAlt, RoundedCornerShape(100.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = accountName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.inkMuted
                            )
                        }
                    }
                    if (splitCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(appColors.accentSoft, RoundedCornerShape(100.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = appColors.accent,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (splitCount == 1) "Split" else "Split ($splitCount)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.accent
                                )
                            }
                        }
                    }
                }
            }

            // Amount and Time
            val amountText = when (transaction.type) {
                TransactionType.EXPENSE -> "−${CurrencyFormatter.format(transaction.amount)}"
                TransactionType.INCOME -> "+${CurrencyFormatter.format(transaction.amount)}"
                TransactionType.TRANSFER -> CurrencyFormatter.format(transaction.amount)
            }
            val amountColor = when (transaction.type) {
                TransactionType.EXPENSE -> appColors.negative
                TransactionType.INCOME -> appColors.positive
                TransactionType.TRANSFER -> appColors.transfer
            }

            val timeText = rememberFormattedTime(transaction.timestamp)

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = amountColor
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp
                    ),
                    color = appColors.inkFaint
                )
            }
        }
    }
}

fun getCategoryIconAndColor(category: TransactionCategory, isDark: Boolean = false): Triple<ImageVector, Color, Color> {
    return when (category) {
        TransactionCategory.BILLS -> Triple(
            Icons.Default.List,
            Color(0xFF1C4FBF),
            if (isDark) Color(0xFF1B2A4A) else Color(0xFFE7F0FE)
        )
        TransactionCategory.FOOD -> Triple(
            Icons.Default.Favorite,
            Color(0xFFB3540A),
            if (isDark) Color(0xFF3D2614) else Color(0xFFFFF1E4)
        )
        TransactionCategory.GROCERY, TransactionCategory.SHOPPING -> Triple(
            Icons.Default.ShoppingCart,
            Color(0xFF6A3DDB),
            if (isDark) Color(0xFF2B1F4A) else Color(0xFFF0ECFE)
        )
        TransactionCategory.TRANSPORT -> Triple(
            Icons.Default.Place,
            Color(0xFF0A7A5D),
            if (isDark) Color(0xFF12342A) else Color(0xFFE4F6F1)
        )
        TransactionCategory.SALARY -> Triple(
            Icons.Default.Star,
            Color(0xFF157F4C),
            if (isDark) Color(0xFF133426) else Color(0xFFE6F5EC)
        )
        TransactionCategory.OTHER -> Triple(
            Icons.Default.Info,
            Color(0xFF63697B),
            if (isDark) Color(0xFF222838) else Color(0xFFF0F1F4)
        )
    }
}

private fun rememberFormattedTime(timestamp: Long): String {
    if (timestamp <= 0) return "11:45 AM"
    return try {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "11:45 AM"
    }
}
