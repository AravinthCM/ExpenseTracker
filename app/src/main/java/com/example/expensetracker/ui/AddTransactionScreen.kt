package com.example.expensetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionScreen(
    onSaveTransaction: (Transaction) -> Unit,
    onNavigateBack: () -> Unit
) {
    var type by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TransactionCategory.FOOD) }

    var amountError by remember { mutableStateOf<String?>(null) }
    var titleError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Transaction",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Dashboard"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Transaction Type Selector (Expense / Income)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = type == TransactionType.EXPENSE,
                    onClick = {
                        type = TransactionType.EXPENSE
                        if (category == TransactionCategory.SALARY) {
                            category = TransactionCategory.FOOD
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Expense")
                }
                SegmentedButton(
                    selected = type == TransactionType.INCOME,
                    onClick = {
                        type = TransactionType.INCOME
                        if (category == TransactionCategory.FOOD || category == TransactionCategory.GROCERY) {
                            category = TransactionCategory.SALARY
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Income")
                }
            }

            // 2. Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    amountText = input
                    amountError = null
                },
                label = { Text("Amount (₹)") },
                placeholder = { Text("0.00") },
                leadingIcon = { Text("₹", fontWeight = FontWeight.Bold) },
                isError = amountError != null,
                supportingText = amountError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Description / Merchant Input
            OutlinedTextField(
                value = titleText,
                onValueChange = { input ->
                    titleText = input
                    titleError = null
                },
                label = { Text("Description / Merchant") },
                placeholder = { Text("e.g. Swiggy, Grocery, Salary") },
                isError = titleError != null,
                supportingText = titleError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Category Selector
            Column {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TransactionCategory.values().forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = {
                                val labelName = cat.name.lowercase().replaceFirstChar { it.uppercase() }
                                Text(labelName)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Save Button
            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull()
                    val trimmedTitle = titleText.trim()

                    var hasError = false
                    if (parsedAmount == null || parsedAmount <= 0) {
                        amountError = "Please enter a valid amount greater than zero"
                        hasError = true
                    }
                    if (trimmedTitle.isBlank()) {
                        titleError = "Please enter a description or merchant name"
                        hasError = true
                    }

                    if (!hasError && parsedAmount != null) {
                        val newTransaction = Transaction(
                            id = UUID.randomUUID().toString(),
                            title = trimmedTitle,
                            amount = parsedAmount,
                            type = type,
                            category = category,
                            date = "Today",
                            source = TransactionSource.MANUAL
                        )
                        onSaveTransaction(newTransaction)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Save Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
