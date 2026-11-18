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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.expensetracker.ui.viewmodel.GoogleAccountViewModel
import com.example.expensetracker.model.Transaction
import com.example.expensetracker.model.TransactionCategory
import com.example.expensetracker.model.TransactionSource
import com.example.expensetracker.model.TransactionType
import com.example.expensetracker.service.TransactionNotificationHelper
import com.example.expensetracker.ui.AddTransactionScreen
import com.example.expensetracker.ui.AnalyticsScreen
import com.example.expensetracker.ui.DashboardScreen
import com.example.expensetracker.ui.ManagementScreen
import com.example.expensetracker.ui.TransactionsScreen
import com.example.expensetracker.ui.theme.ExpenseTrackerTheme
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.viewmodel.TransactionViewModel

import com.example.expensetracker.ui.TestDataScreen

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

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
        installSplashScreen()
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
    viewModel: TransactionViewModel = viewModel(),
    googleAccountViewModel: GoogleAccountViewModel = viewModel()
) {
    val activityContext = LocalContext.current
    val googleAccount by googleAccountViewModel.connectedAccount.collectAsState()
    val googleUiState by googleAccountViewModel.uiState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val categories by viewModel.categoriesState.collectAsState()
    val transactionEditLogs by viewModel.transactionEditLogsState.collectAsState()
    val categoryMappings by viewModel.categoryMappingsState.collectAsState()
    val recurringExpenses by viewModel.recurringExpensesState.collectAsState()
    val testNotifications by viewModel.testNotificationsState.collectAsState()
    val paymentTemplates by viewModel.paymentTemplatesState.collectAsState()
    val accounts by viewModel.accountsState.collectAsState()
    val primaryAccount by viewModel.primaryAccountState.collectAsState()
    val accountsWithBalances by viewModel.accountsWithBalancesState.collectAsState()
    val selectedAccountId by viewModel.selectedAccountIdState.collectAsState()
    val isBalanceVisible by viewModel.isBalanceVisibleState.collectAsState()
    val backupExportState by viewModel.backupExportState.collectAsState()
    val backupImportState by viewModel.backupImportState.collectAsState()
    val gPayRequests by viewModel.gPayRequestsState.collectAsState()

    val unassignedTransactionCount = remember(transactions) {
        transactions.count { it.accountId == null }
    }

    val systemDark = isSystemInDarkTheme()
    var isDarkTheme by rememberSaveable { mutableStateOf(systemDark) }

    var currentScreen by rememberSaveable { mutableStateOf(Screen.Overview) }
    var sampleForTemplate by remember { mutableStateOf<String?>(null) }

    var showAddSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var categoryLearningPreview by remember { mutableStateOf<TransactionViewModel.CategoryLearningPreview?>(null) }
    var selectedDetailTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showSplash by rememberSaveable { mutableStateOf(true) }

        ExpenseTrackerTheme(darkTheme = isDarkTheme) {
        val appColors = LocalAppColors.current

        if (showSplash) {
            com.example.expensetracker.ui.SplashScreen(
                onSplashFinished = { showSplash = false }
            )
        } else {
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
                    val navItems = listOf(Screen.Overview, Screen.Transactions, Screen.Analytics, Screen.Management)
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
                            accounts = accounts,
                            selectedAccountId = selectedAccountId,
                            isBalanceVisible = isBalanceVisible,
                            onSelectAccount = { accId -> viewModel.selectAccount(accId) },
                            onToggleBalanceVisibility = { viewModel.toggleBalanceVisibility() },
                            onAddTransactionClick = { showAddSheet = true },
                            onAnalyticsClick = { currentScreen = Screen.Analytics },
                            onTransactionsClick = { currentScreen = Screen.Transactions },
                            onViewAllExpenses = { currentScreen = Screen.Transactions },
                            onManagementClick = { currentScreen = Screen.Management },
                            onEditTransaction = { transaction -> editingTransaction = transaction },
                            onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction) }
                        )
                    }
                    Screen.Transactions -> {
                        TransactionsScreen(
                            transactions = transactions,
                            transactionEditLogs = transactionEditLogs,
                            categories = categories,
                            onEditTransaction = { transaction -> editingTransaction = transaction },
                            onDeleteTransaction = { transaction -> viewModel.deleteTransaction(transaction) },
                        )
                    }
                    Screen.Analytics -> {
                        AnalyticsScreen(
                            transactions = transactions,
                            categories = categories,
                            accounts = accounts,
                            selectedAccountId = selectedAccountId,
                            onSelectAccount = { accId -> viewModel.selectAccount(accId) },
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
                            accountsWithBalances = accountsWithBalances,
                            transactions = transactions,
                            unassignedTransactionCount = unassignedTransactionCount,
                            initialSelectedTab = -1,
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
                            onSaveAccount = { id, name, openingBal, isPrimary, accNum ->
                                viewModel.saveAccount(id, name, openingBal, isPrimary, accNum)
                            },
                            onSetPrimaryAccount = { accId -> viewModel.setPrimaryAccount(accId) },
                            onSetAccountArchived = { accId, isArchived -> viewModel.setAccountArchived(accId, isArchived) },
                            onDeleteAccount = { acc -> viewModel.deleteAccount(acc) },
                            onAssignUnassignedTransactions = { accId -> viewModel.assignUnassignedTransactionsToAccount(accId) },
                            onAssignSelectedUnassigned = { transactionIds, accountId ->
                                viewModel.assignUnassignedTransactionIdsToAccount(transactionIds, accountId)
                            },
                            gPayRequests = gPayRequests,
                            onMapGPayRequestToExpense = { requestId, transactionId ->
                                viewModel.mapGPayRequestToExpense(requestId, transactionId)
                            },
                            onCreateExpenseFromGPayRequest = { requestId ->
                                viewModel.createSimpleExpenseFromGPayRequest(requestId)
                            },
                            onCreateExpensesFromGPayRequests = { requestIds ->
                                viewModel.createExpensesFromGPayRequests(requestIds)
                            },
                            totalTransactionCount = transactions.size,
                            backupExportState = backupExportState,
                            backupImportState = backupImportState,
                            onExportBackupToUri = { uri -> viewModel.exportBackupToUri(uri) },
                            onShareBackup = { launcher -> viewModel.exportBackupForShare(launcher) },
                            onDismissBackupState = { viewModel.resetBackupExportState() },
                            onImportBackupFile = { uri -> viewModel.loadAndValidateBackup(uri) },
                            onConfirmImport = { mode -> viewModel.confirmImport(mode) },
                            onDismissImportState = { viewModel.resetBackupImportState() },
                            googleAccount = googleAccount,
                            googleAccountLoading = googleUiState == GoogleAccountViewModel.UiState.Loading,
                            googleAccountError = (googleUiState as? GoogleAccountViewModel.UiState.Error)?.message,
                            onGoogleSignIn = { googleAccountViewModel.signIn(activityContext) },
                            onGoogleDisconnect = { googleAccountViewModel.disconnect() },
                            onDismissGoogleError = { googleAccountViewModel.dismissError() },
                            onNavigateBack = { currentScreen = Screen.Overview }
                        )
                    }
                    Screen.TestData -> {
                        TestDataScreen(
                            testNotifications = testNotifications,
                            onClearAll = { viewModel.clearTestNotifications() },
                            onUseAsTemplate = { text ->
                                sampleForTemplate = text
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
                accounts = accounts.filter { !it.isArchived },
                defaultAccountId = primaryAccount?.id,
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
                accounts = accounts,
                defaultAccountId = primaryAccount?.id,
                onDismiss = { editingTransaction = null },
                onSave = { updatedTx ->
                    val preview = viewModel.previewCategoryLearning(transaction, updatedTx)
                    if (preview != null) {
                        categoryLearningPreview = preview
                    } else {
                        viewModel.updateTransaction(updatedTx)
                        editingTransaction = null
                    }
                }
            )
        }

        categoryLearningPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = { categoryLearningPreview = null },
                title = { Text("Learn this category change?") },
                text = {
                    Text(
                        "You changed '${preview.transaction.title}' from ${preview.oldCategory} to ${preview.newCategory}. " +
                                "${preview.matchingTransactionCount} other matching expense(s) were found. Apply this category to them and future matching transactions?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateTransactionAndLearn(editingTransaction!!, preview.transaction)
                        categoryLearningPreview = null
                        editingTransaction = null
                    }) { Text("Apply and save rule") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.updateTransactionOnly(preview.transaction)
                            categoryLearningPreview = null
                            editingTransaction = null
                        }) { Text("Only this expense") }
                        TextButton(onClick = { categoryLearningPreview = null }) { Text("Cancel") }
                    }
                }
            )
        }

        // Detail View Sheet
        selectedDetailTransaction?.let { transaction ->
            com.example.expensetracker.ui.components.TransactionDetailBottomSheet(
                transaction = transaction,
                editLogs = transactionEditLogs.filter { it.transactionId == transaction.id },
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
}
