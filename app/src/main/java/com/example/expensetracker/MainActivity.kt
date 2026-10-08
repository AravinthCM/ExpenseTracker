package com.example.expensetracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.service.TransactionNotificationHelper
import com.example.expensetracker.ui.AddTransactionScreen
import com.example.expensetracker.ui.AnalyticsScreen
import com.example.expensetracker.ui.DashboardScreen
import com.example.expensetracker.ui.ManagementScreen
import com.example.expensetracker.ui.TransactionsScreen
import com.example.expensetracker.ui.components.EditTransactionDialog
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.viewmodel.TransactionViewModel

import com.example.expensetracker.ui.TestDataScreen

enum class Screen(val title: String, val icon: ImageVector) {
    Overview("Overview", Icons.Default.Home),
    Transactions("Transactions", Icons.Default.List),
    Analytics("Analytics", Icons.Default.DateRange),
    Management("Categories & rules", Icons.Default.Settings),
    TestData("Test Data", Icons.Default.Info),
    AddTransaction("Add Transaction", Icons.Default.Add)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TransactionNotificationHelper.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        setContent {
            ExpenseTrackerApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(
    viewModel: TransactionViewModel = viewModel()
) {
    val transactions by viewModel.transactionsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val categoryMappings by viewModel.categoryMappingsState.collectAsState()
    val recurringExpenses by viewModel.recurringExpensesState.collectAsState()
    val testNotifications by viewModel.testNotificationsState.collectAsState()
    val paymentTemplates by viewModel.paymentTemplatesState.collectAsState()
    val splitGroups by viewModel.splitGroupsState.collectAsState()

    val systemDark = isSystemInDarkTheme()
    var isDarkTheme by rememberSaveable { mutableStateOf(systemDark) }

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Overview) }
    var managementInitialTab by remember { mutableStateOf(0) }
    var sampleForTemplate by remember { mutableStateOf<String?>(null) }

    var showAddSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var selectedDetailTransaction by remember { mutableStateOf<Transaction?>(null) }

    ExpenseTrackerTheme(darkTheme = isDarkTheme) {
        val appColors = LocalAppColors.current

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = currentScreen.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            color = appColors.ink
                        )
                    },
                    actions = {
                        IconButton(onClick = { currentScreen = Screen.TestData }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Test Data",
                                tint = if (currentScreen == Screen.TestData) appColors.accent else appColors.ink
                            )
                        }
                        com.example.expensetracker.ui.components.ThemeToggleIconButton(
                            isDark = isDarkTheme,
                            onToggle = { isDarkTheme = !isDarkTheme },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appColors.surface,
                        titleContentColor = appColors.ink
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = appColors.surface,
                    tonalElevation = 0.dp
                ) {
                    val navItems = listOf(Screen.Overview, Screen.Transactions, Screen.Analytics, Screen.Management, Screen.TestData)
                    navItems.forEach { screen ->
                        val selected = currentScreen == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(21.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = when (screen) {
                                        Screen.Overview -> "Home"
                                        Screen.Transactions -> "Transactions"
                                        Screen.Analytics -> "Analytics"
                                        Screen.Management -> "Manage"
                                        Screen.TestData -> "Test Data"
                                        else -> screen.title
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = appColors.accent,
                                selectedTextColor = appColors.accent,
                                unselectedIconColor = appColors.inkFaint,
                                unselectedTextColor = appColors.inkFaint,
                                indicatorColor = appColors.accentSoft
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    shape = CircleShape,
                    containerColor = appColors.accent,
                    contentColor = appColors.accentInk,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction",
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            containerColor = appColors.bg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    Screen.Overview -> {
                        DashboardScreen(
                            transactions = transactions,
                            categories = categories,
                            onAddTransactionClick = { showAddSheet = true },
                            onAnalyticsClick = { currentScreen = Screen.Analytics },
                            onTransactionsClick = { currentScreen = Screen.Transactions },
                            onManagementClick = { currentScreen = Screen.Management },
                            onEditTransaction = { transaction -> editingTransaction = transaction },
                            onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction) }
                        )
                    }
                    Screen.Transactions -> {
                        TransactionsScreen(
                            transactions = transactions,
                            categories = categories,
                            onEditTransaction = { transaction -> editingTransaction = transaction },
                            onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction) }
                        )
                    }
                    Screen.Analytics -> {
                        AnalyticsScreen(
                            transactions = transactions,
                            categories = categories,
                            recurringEntities = recurringExpenses,
                            onNavigateBack = { currentScreen = Screen.Overview },
                            onConfirmCandidate = { candidate -> viewModel.confirmRecurringCandidate(candidate) },
                            onDismissCandidate = { candidate -> viewModel.dismissRecurringCandidate(candidate) },
                            onEditTransaction = { transaction -> editingTransaction = transaction },
                            onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction) }
                        )
                    }
                    Screen.Management -> {
                        ManagementScreen(
                            categories = categories,
                            mappings = categoryMappings,
                            paymentTemplates = paymentTemplates,
                            splitGroups = splitGroups,
                            initialSelectedTab = managementInitialTab,
                            initialSampleForTemplate = sampleForTemplate,
                            onAddCategory = { name -> viewModel.addCategory(name) },
                            onUpdateCategory = { cat, newName -> viewModel.updateCategory(cat, newName) },
                            onDeleteCategory = { cat -> viewModel.deleteCategory(cat) },
                            onAddMapping = { pat, catName, prio -> viewModel.addCategoryMapping(pat, catName, prio) },
                            onUpdateMapping = { mapping -> viewModel.updateCategoryMapping(mapping) },
                            onDeleteMapping = { mapping -> viewModel.deleteCategoryMapping(mapping) },
                            onAddPaymentTemplate = { name, type, sample, pattern ->
                                viewModel.addPaymentTemplate(name, type, sample, pattern)
                                sampleForTemplate = null
                            },
                            onUpdatePaymentTemplate = { template -> viewModel.updatePaymentTemplate(template) },
                            onTogglePaymentTemplateEnabled = { template -> viewModel.togglePaymentTemplateEnabled(template) },
                            onDeletePaymentTemplate = { template -> viewModel.deletePaymentTemplate(template) },
                            onSaveSplitGroup = { groupId, name, members -> viewModel.saveSplitGroup(groupId, name, members) },
                            onDeleteSplitGroup = { group -> viewModel.deleteSplitGroup(group) },
                            onNavigateBack = { currentScreen = Screen.Overview }
                        )
                    }
                    Screen.TestData -> {
                        TestDataScreen(
                            testNotifications = testNotifications,
                            onClearAll = { viewModel.clearTestNotifications() },
                            onUseAsTemplate = { text ->
                                sampleForTemplate = text
                                managementInitialTab = 2
                                currentScreen = Screen.Management
                            },
                            onNavigateBack = { currentScreen = Screen.Overview }
                        )
                    }
                    else -> {}
                }
            }
        }

        // Add Transaction Sheet
        if (showAddSheet) {
            com.example.expensetracker.ui.components.AddOrEditTransactionBottomSheet(
                initialTransaction = null,
                availableCategories = categories.map { it.name },
                onDismiss = { showAddSheet = false },
                onSave = { newTx ->
                    viewModel.addTransaction(newTx)
                    showAddSheet = false
                }
            )
        }

        // Edit Transaction Sheet
        editingTransaction?.let { transaction ->
            com.example.expensetracker.ui.components.AddOrEditTransactionBottomSheet(
                initialTransaction = transaction,
                availableCategories = categories.map { it.name },
                onDismiss = { editingTransaction = null },
                onSave = { updatedTx ->
                    viewModel.updateTransaction(updatedTx)
                    editingTransaction = null
                }
            )
        }

        // Detail View Sheet
        selectedDetailTransaction?.let { transaction ->
            com.example.expensetracker.ui.components.TransactionDetailBottomSheet(
                transaction = transaction,
                onDismiss = { selectedDetailTransaction = null },
                onEdit = { tx ->
                    selectedDetailTransaction = null
                    editingTransaction = tx
                },
                onDelete = { tx ->
                    viewModel.deleteTransaction(tx)
                    selectedDetailTransaction = null
                }
            )
        }
    }
}
