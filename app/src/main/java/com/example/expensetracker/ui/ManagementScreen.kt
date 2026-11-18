package com.example.expensetracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.CategoryMappingEntity
import com.example.expensetracker.data.local.GPayRequestEntity
import com.example.expensetracker.data.preferences.CloudSyncState
import com.example.expensetracker.data.preferences.GoogleAccountInfo
import com.example.expensetracker.model.GPayProcessingStatus
import com.example.expensetracker.service.CategoryMapper
import com.example.expensetracker.ui.components.CloudBackupSection
import com.example.expensetracker.ui.components.ManageEmptyState
import com.example.expensetracker.ui.components.ManageMenuAction
import com.example.expensetracker.ui.components.ManageOverflowMenu
import com.example.expensetracker.ui.components.ManagePill
import com.example.expensetracker.ui.components.ManageSurface
import com.example.expensetracker.ui.components.getCategoryIconAndColor
import com.example.expensetracker.ui.theme.LocalAppColors
import com.example.expensetracker.ui.util.CurrencyFormatter

// ───────────────────────── Navigation model ─────────────────────────

private enum class ManagePage(val title: String) {
    ACCOUNTS("Accounts"),
    CATEGORIES("Categories"),
    RULES("Category rules"),
    TEMPLATES("Payment templates"),
    GPAY_REQUESTS("GPay requests"),
    BACKUP("Backup & restore")
}

/** Maps the old tab indexes (used by callers / deep links) to pages. -1 (or anything else) = hub. */
private fun pageForIndex(index: Int): ManagePage? = when (index) {
    0 -> ManagePage.CATEGORIES
    1 -> ManagePage.RULES
    2 -> ManagePage.TEMPLATES
    3 -> ManagePage.ACCOUNTS
    4 -> ManagePage.BACKUP
    5 -> ManagePage.GPAY_REQUESTS
    else -> null
}

private data class HubItem(
    val page: ManagePage,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

private fun plural(n: Int, one: String, many: String = "${one}s") = "$n ${if (n == 1) one else many}"

// ───────────────────────── Screen ─────────────────────────

@Composable
fun ManagementScreen(
    categories: List<CategoryEntity>,
    mappings: List<CategoryMappingEntity>,
    paymentTemplates: List<com.example.expensetracker.data.local.PaymentTemplateEntity> = emptyList(),
    accountsWithBalances: List<com.example.expensetracker.model.AccountWithBalance> = emptyList(),
    transactions: List<com.example.expensetracker.model.Transaction> = emptyList(),
    unassignedTransactionCount: Int = 0,
    initialSelectedTab: Int = -1, // -1 = show the Manage hub. 0..5 open that section directly.
    initialSampleForTemplate: String? = null,
    onAddCategory: (String) -> Unit,
    onUpdateCategory: (CategoryEntity, String) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onAddMapping: (String, String, Int) -> Unit,
    onUpdateMapping: (CategoryMappingEntity) -> Unit,
    onDeleteMapping: (CategoryMappingEntity) -> Unit,
    onAddPaymentTemplate: (name: String, transactionType: String, sampleText: String, templatePattern: String) -> Unit = { _, _, _, _ -> },
    onUpdatePaymentTemplate: (com.example.expensetracker.data.local.PaymentTemplateEntity) -> Unit = {},
    onTogglePaymentTemplateEnabled: (com.example.expensetracker.data.local.PaymentTemplateEntity) -> Unit = {},
    onDeletePaymentTemplate: (com.example.expensetracker.data.local.PaymentTemplateEntity) -> Unit = {},
    onSaveAccount: (id: String?, name: String, currentBalancePaise: Long, isPrimary: Boolean, accountNumber: String?) -> String? = { _, _, _, _, _ -> null },
    onSetPrimaryAccount: (accountId: String) -> Unit = {},
    onSetAccountArchived: (accountId: String, isArchived: Boolean) -> Unit = { _, _ -> },
    onDeleteAccount: (com.example.expensetracker.data.local.AccountEntity) -> Unit = {},
    onAssignUnassignedTransactions: (accountId: String) -> Unit = {},
    onAssignSelectedUnassigned: (transactionIds: List<String>, accountId: String) -> Unit = { _, _ -> },
    gPayRequests: List<GPayRequestEntity> = emptyList(),
    onMapGPayRequestToExpense: (requestId: String, transactionId: String) -> String? = { _, _ -> null },
    onCreateExpenseFromGPayRequest: (requestId: String) -> String? = { null },
    onCreateExpensesFromGPayRequests: (requestIds: List<String>) -> String? = { null },
    totalTransactionCount: Int = 0,
    backupExportState: com.example.expensetracker.data.backup.BackupExportState = com.example.expensetracker.data.backup.BackupExportState.Idle,
    backupImportState: com.example.expensetracker.data.backup.BackupImportState = com.example.expensetracker.data.backup.BackupImportState.Idle,
    onExportBackupToUri: (android.net.Uri) -> Unit = {},
    onShareBackup: (((android.content.Intent) -> Unit) -> Unit) = {},
    onImportBackupFile: (android.net.Uri) -> Unit = {},
    onConfirmImport: (com.example.expensetracker.data.backup.ImportMode) -> Unit = {},
    onDismissBackupState: () -> Unit = {},
    onDismissImportState: () -> Unit = {},
    googleAccount: GoogleAccountInfo? = null,
    googleAccountLoading: Boolean = false,
    googleAccountError: String? = null,
    onGoogleSignIn: () -> Unit = {},
    onGoogleDisconnect: () -> Unit = {},
    onDismissGoogleError: () -> Unit = {},
    cloudBackupEnabled: Boolean = false,
    lastCloudBackupModifiedTime: String? = null,
    cloudSyncState: CloudSyncState = CloudSyncState.IDLE,
    lastSyncedTimestamp: Long? = null,
    lastSyncError: String? = null,
    cloudSyncing: Boolean = false,
    driveAuthorizationLoading: Boolean = false,
    driveAuthorizationMessage: String? = null,
    onEnableCloudBackup: () -> Unit = {},
    onRetryCloudBackup: () -> Unit = {},
    onBackUpNow: () -> Unit = {},
    onRestoreFromDrive: () -> Unit = {},
    onDisableCloudBackup: () -> Unit = {},
    onDismissDriveMessage: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var page by remember(initialSelectedTab, initialSampleForTemplate) {
        mutableStateOf<ManagePage?>(
            if (!initialSampleForTemplate.isNullOrBlank()) ManagePage.TEMPLATES
            else pageForIndex(initialSelectedTab)
        )
    }

    // System back from a section returns to the hub; from the hub it behaves as before.
    BackHandler(enabled = page != null) { page = null }

    // Dialog states
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var showAddMappingDialog by remember { mutableStateOf(false) }
    var editingMapping by remember { mutableStateOf<CategoryMappingEntity?>(null) }
    var deletingMapping by remember { mutableStateOf<CategoryMappingEntity?>(null) }

    var showAddTemplateDialog by remember(initialSampleForTemplate) { mutableStateOf(!initialSampleForTemplate.isNullOrBlank()) }
    var editingTemplate by remember { mutableStateOf<com.example.expensetracker.data.local.PaymentTemplateEntity?>(null) }
    var testingTemplate by remember { mutableStateOf<com.example.expensetracker.data.local.PaymentTemplateEntity?>(null) }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<com.example.expensetracker.data.local.AccountEntity?>(null) }

    // ── Derived summaries for the hub ──
    val activeAccounts = remember(accountsWithBalances) { accountsWithBalances.filter { !it.account.isArchived } }
    val totalBalance = remember(activeAccounts) { activeAccounts.sumOf { it.currentBalanceDouble } }
    val categoryUsage = remember(transactions) {
        transactions.groupingBy { it.displayCategoryName.lowercase() }.eachCount()
    }
    val pendingGPayRequests = remember(gPayRequests) {
        gPayRequests.filter { it.processingStatus == GPayProcessingStatus.PENDING }
    }

    val hubGroups = listOf(
        "MONEY" to listOf(
            HubItem(
                ManagePage.ACCOUNTS, Icons.Default.AccountBox, "Accounts",
                if (accountsWithBalances.isEmpty()) "Add your bank or cash accounts"
                else "${plural(activeAccounts.size, "account")} · ${CurrencyFormatter.format(totalBalance)}"
            ),
            HubItem(
                ManagePage.CATEGORIES, Icons.AutoMirrored.Filled.List, "Categories",
                plural(categories.size, "category", "categories")
            )
        ),
        "AUTO-TRACKING" to listOf(
            HubItem(
                ManagePage.TEMPLATES, Icons.Default.Notifications, "Payment templates",
                if (paymentTemplates.isEmpty()) "Teach the app to read your bank alerts"
                else "${paymentTemplates.count { it.enabled }} of ${paymentTemplates.size} active"
            ),
            HubItem(
                ManagePage.RULES, Icons.Default.Build, "Category rules",
                if (mappings.isEmpty()) "Auto-assign categories by merchant text"
                else plural(mappings.size, "rule")
            ),
            HubItem(
                ManagePage.GPAY_REQUESTS, Icons.Default.Notifications, "GPay requests",
                if (pendingGPayRequests.isEmpty()) "No pending requests"
                else "${plural(pendingGPayRequests.size, "request")} pending"
            )
        ),
        "DATA" to listOf(
            HubItem(
                ManagePage.BACKUP, Icons.Default.Refresh, "Backup & restore",
                "${plural(totalTransactionCount, "transaction")} safe to export"
            )
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(targetState = page, label = "managePage") { current ->
            if (current == null) {
                ManageHub(
                    unassignedCount = unassignedTransactionCount,
                    groups = hubGroups,
                    onOpen = { page = it },
                    googleAccount = googleAccount,
                    googleAccountLoading = googleAccountLoading,
                    googleAccountError = googleAccountError,
                    onGoogleSignIn = onGoogleSignIn,
                    onGoogleDisconnect = onGoogleDisconnect,
                    onDismissGoogleError = onDismissGoogleError,
                    cloudBackupEnabled = cloudBackupEnabled,
                    lastCloudBackupModifiedTime = lastCloudBackupModifiedTime,
                    cloudSyncState = cloudSyncState,
                    lastSyncedTimestamp = lastSyncedTimestamp,
                    lastSyncError = lastSyncError,
                    cloudSyncing = cloudSyncing,
                    driveAuthorizationLoading = driveAuthorizationLoading,
                    driveAuthorizationMessage = driveAuthorizationMessage,
                    onEnableCloudBackup = onEnableCloudBackup,
                    onRetryCloudBackup = onRetryCloudBackup,
                    onBackUpNow = onBackUpNow,
                    onRestoreFromDrive = onRestoreFromDrive,
                    onDisableCloudBackup = onDisableCloudBackup,
                    onDismissDriveMessage = onDismissDriveMessage
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val header: Pair<String?, (() -> Unit)?> = when (current) {
                        ManagePage.ACCOUNTS -> Pair(plural(accountsWithBalances.size, "account"), { showAddAccountDialog = true })
                        ManagePage.CATEGORIES -> Pair(plural(categories.size, "category", "categories"), { showAddCategoryDialog = true })
                        ManagePage.RULES -> Pair(plural(mappings.size, "rule"), { showAddMappingDialog = true })
                        ManagePage.TEMPLATES -> Pair(
                            "${paymentTemplates.count { it.enabled }} of ${paymentTemplates.size} active",
                            { showAddTemplateDialog = true }
                        )
                        ManagePage.GPAY_REQUESTS -> Pair(plural(pendingGPayRequests.size, "pending request"), null)
                        ManagePage.BACKUP -> Pair(null, null)
                    }

                    PageHeader(
                        title = current.title,
                        subtitle = header.first,
                        onBack = { page = null },
                        onAdd = header.second
                    )

                    when (current) {
                        ManagePage.CATEGORIES -> CategoriesPage(
                            categories = categories,
                            usage = categoryUsage,
                            onAdd = { showAddCategoryDialog = true },
                            onEdit = { editingCategory = it },
                            onDelete = { deletingCategory = it }
                        )
                        ManagePage.RULES -> RulesPage(
                            mappings = mappings,
                            onAdd = { showAddMappingDialog = true },
                            onEdit = { editingMapping = it },
                            onDelete = { deletingMapping = it }
                        )
                        ManagePage.TEMPLATES -> com.example.expensetracker.ui.components.PaymentTemplatesTabContent(
                            templates = paymentTemplates,
                            onAddTemplateClick = { showAddTemplateDialog = true },
                            onEditTemplateClick = { editingTemplate = it },
                            onTestTemplateClick = { testingTemplate = it },
                            onToggleEnabledClick = onTogglePaymentTemplateEnabled,
                            onDeleteTemplateClick = onDeletePaymentTemplate
                        )
                        ManagePage.ACCOUNTS -> com.example.expensetracker.ui.components.AccountsTabContent(
                            accountsWithBalances = accountsWithBalances,
                            transactions = transactions,
                            unassignedCount = unassignedTransactionCount,
                            onAddAccountClick = { showAddAccountDialog = true },
                            onEditAccountClick = { editingAccount = it },
                            onSetPrimaryAccountClick = onSetPrimaryAccount,
                            onToggleArchivedClick = { acc -> onSetAccountArchived(acc.id, !acc.isArchived) },
                            onDeleteAccountClick = onDeleteAccount,
                            onAssignUnassignedClick = onAssignUnassignedTransactions,
                            onAssignSelectedUnassigned = onAssignSelectedUnassigned
                        )
                        ManagePage.BACKUP -> com.example.expensetracker.ui.components.BackupTabContent(
                            totalTransactions = totalTransactionCount,
                            totalAccounts = accountsWithBalances.size,
                            totalCategories = categories.size,
                            totalSplitGroups = 0,
                            totalTemplates = paymentTemplates.size,
                            backupExportState = backupExportState,
                            backupImportState = backupImportState,
                            onExportToUri = onExportBackupToUri,
                            onShareBackup = onShareBackup,
                            onImportFileSelected = onImportBackupFile,
                            onConfirmImport = onConfirmImport,
                            onDismissState = onDismissBackupState,
                            onDismissImportState = onDismissImportState
                        )
                        ManagePage.GPAY_REQUESTS -> GPayRequestsSimplePage(
                            requests = pendingGPayRequests,
                            transactions = transactions,
                            onMapToExpense = onMapGPayRequestToExpense,
                            onCreateExpense = onCreateExpenseFromGPayRequest,
                            onCreateExpensesBulk = onCreateExpensesFromGPayRequests
                        )
                    }
                }
            }
        }
    }

    // ── Account dialog ──
    if (showAddAccountDialog || editingAccount != null) {
        val editingCurrentBalancePaise = editingAccount?.let { acc ->
            accountsWithBalances.firstOrNull { it.account.id == acc.id }?.currentBalancePaise
        }
        com.example.expensetracker.ui.components.AddOrEditAccountDialog(
            initialAccount = editingAccount,
            initialCurrentBalancePaise = editingCurrentBalancePaise,
            onDismiss = {
                showAddAccountDialog = false
                editingAccount = null
            },
            onSave = onSaveAccount
        )
    }

    // ── Template dialogs ──
    if (showAddTemplateDialog) {
        com.example.expensetracker.ui.components.AddOrEditTemplateDialog(
            title = "Add Payment Template",
            initialTemplate = null,
            initialSampleText = initialSampleForTemplate ?: "",
            onDismiss = { showAddTemplateDialog = false },
            onSave = { name, type, sample, pattern ->
                onAddPaymentTemplate(name, type, sample, pattern)
                showAddTemplateDialog = false
            }
        )
    }

    editingTemplate?.let { template ->
        com.example.expensetracker.ui.components.AddOrEditTemplateDialog(
            title = "Edit Payment Template",
            initialTemplate = template,
            onDismiss = { editingTemplate = null },
            onSave = { name, type, sample, pattern ->
                onUpdatePaymentTemplate(
                    template.copy(
                        name = name,
                        transactionType = type,
                        sampleText = sample,
                        templatePattern = pattern
                    )
                )
                editingTemplate = null
            }
        )
    }

    testingTemplate?.let { template ->
        com.example.expensetracker.ui.components.TestTemplateDialog(
            template = template,
            onDismiss = { testingTemplate = null }
        )
    }

    // ── Category dialogs ──
    if (showAddCategoryDialog) {
        AddOrEditCategoryDialog(
            title = "Add New Category",
            initialName = "",
            onDismiss = { showAddCategoryDialog = false },
            onSave = { name ->
                onAddCategory(name)
                showAddCategoryDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        AddOrEditCategoryDialog(
            title = "Edit Category",
            initialName = category.name,
            onDismiss = { editingCategory = null },
            onSave = { newName ->
                onUpdateCategory(category, newName)
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete Category?") },
            text = { Text("Transactions under '${category.name}' will be safely moved to 'Other'.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(category)
                    deletingCategory = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text("Cancel") } }
        )
    }

    // ── Rule dialogs ──
    if (showAddMappingDialog) {
        AddOrEditMappingDialog(
            title = "Add Mapping Rule",
            initialPattern = "",
            initialCategory = categories.firstOrNull()?.name ?: "Food",
            initialPriority = mappings.size + 1,
            availableCategories = categories.map { it.name },
            onDismiss = { showAddMappingDialog = false },
            onSave = { pattern, categoryName, priority ->
                onAddMapping(pattern, categoryName, priority)
                showAddMappingDialog = false
            }
        )
    }

    editingMapping?.let { mapping ->
        AddOrEditMappingDialog(
            title = "Edit Mapping Rule",
            initialPattern = mapping.pattern,
            initialCategory = mapping.categoryName,
            initialPriority = mapping.priority,
            availableCategories = categories.map { it.name },
            onDismiss = { editingMapping = null },
            onSave = { pattern, categoryName, priority ->
                onUpdateMapping(mapping.copy(pattern = pattern, categoryName = categoryName, priority = priority))
                editingMapping = null
            }
        )
    }

    deletingMapping?.let { mapping ->
        AlertDialog(
            onDismissRequest = { deletingMapping = null },
            title = { Text("Delete rule?") },
            text = { Text("New transactions matching this pattern will no longer be auto-assigned to '${mapping.categoryName}'.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteMapping(mapping)
                    deletingMapping = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingMapping = null }) { Text("Cancel") } }
        )
    }
}

// ───────────────────────── GPay requests ─────────────────────────

@Composable
private fun GPayRequestsSimplePage(
    requests: List<GPayRequestEntity>,
    transactions: List<com.example.expensetracker.model.Transaction>,
    onMapToExpense: (requestId: String, transactionId: String) -> String?,
    onCreateExpense: (requestId: String) -> String?,
    onCreateExpensesBulk: (requestIds: List<String>) -> String?
) {
    val appColors = LocalAppColors.current
    var selectedIds by remember(requests) { mutableStateOf(setOf<String>()) }
    var mappingRequestId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var locallyHandledIds by remember { mutableStateOf(setOf<String>()) }

    val visibleRequests = remember(requests, locallyHandledIds) {
        requests.filterNot { locallyHandledIds.contains(it.id) }
    }

    LaunchedEffect(requests) {
        val pendingIds = requests.map { it.id }.toSet()
        locallyHandledIds = locallyHandledIds.intersect(pendingIds)
        selectedIds = selectedIds.intersect(pendingIds)
    }

    val expenseTransactions = remember(transactions) {
        transactions
            .filter { it.type == com.example.expensetracker.model.TransactionType.EXPENSE }
            .sortedByDescending { it.timestamp }
            .take(120)
    }

    if (visibleRequests.isEmpty()) {
        ManageEmptyState(
            title = "No pending GPay requests",
            message = "Captured payment requests will appear here for quick mapping or expense creation.",
            actionLabel = null,
            onAction = {}
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ManageSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selected: ${selectedIds.size}",
                    color = appColors.ink,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = {
                        val selected = selectedIds.toList()
                        val error = onCreateExpensesBulk(selected)
                        if (error == null) {
                            locallyHandledIds = locallyHandledIds + selected
                            selectedIds = emptySet()
                        } else {
                            errorMessage = error
                        }
                    },
                    enabled = selectedIds.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.accent)
                ) {
                    Text("Create expenses", color = appColors.accentInk)
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visibleRequests, key = { it.id }) { request ->
                ManageSurface {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(request.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + request.id else selectedIds - request.id
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = request.requesterRawName,
                                    color = appColors.ink,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = request.description,
                                    color = appColors.inkFaint,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = CurrencyFormatter.formatPaise(request.amountPaise),
                                color = appColors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { mappingRequestId = request.id }) {
                                Text("Map to expense")
                            }
                            TextButton(onClick = {
                                val error = onCreateExpense(request.id)
                                if (error != null) {
                                    errorMessage = error
                                } else {
                                    locallyHandledIds = locallyHandledIds + request.id
                                    selectedIds = selectedIds - request.id
                                }
                            }) {
                                Text("Create expense")
                            }
                        }
                    }
                }
            }
        }
    }

    mappingRequestId?.let { requestId ->
        MapRequestToExpenseDialog(
            expenseTransactions = expenseTransactions,
            onDismiss = { mappingRequestId = null },
            onConfirm = { txId ->
                val error = onMapToExpense(requestId, txId)
                if (error == null) {
                    locallyHandledIds = locallyHandledIds + requestId
                    selectedIds = selectedIds - requestId
                    mappingRequestId = null
                } else {
                    errorMessage = error
                }
            }
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Action failed") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapRequestToExpenseDialog(
    expenseTransactions: List<com.example.expensetracker.model.Transaction>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTxId by remember { mutableStateOf<String?>(null) }
    val selected = expenseTransactions.firstOrNull { it.id == selectedTxId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map to expense") },
        text = {
            Column {
                Text("Pick an existing expense transaction")
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selected?.let { "${it.title} • ${it.date} • ₹${it.amount}" } ?: "Select expense",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (expenseTransactions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No expense transactions found") },
                                onClick = { expanded = false },
                                enabled = false
                            )
                        } else {
                            expenseTransactions.forEach { tx ->
                                DropdownMenuItem(
                                    text = { Text("${tx.title} • ${tx.date} • ₹${tx.amount}") },
                                    onClick = {
                                        selectedTxId = tx.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedTxId?.let(onConfirm) },
                enabled = selectedTxId != null
            ) { Text("Map") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ───────────────────────── Hub ─────────────────────────

@Composable
private fun ManageHub(
    unassignedCount: Int,
    groups: List<Pair<String, List<HubItem>>>,
    onOpen: (ManagePage) -> Unit,
    googleAccount: GoogleAccountInfo?,
    googleAccountLoading: Boolean,
    googleAccountError: String?,
    onGoogleSignIn: () -> Unit,
    onGoogleDisconnect: () -> Unit,
    onDismissGoogleError: () -> Unit,
    cloudBackupEnabled: Boolean,
    lastCloudBackupModifiedTime: String?,
    cloudSyncState: CloudSyncState,
    lastSyncedTimestamp: Long?,
    lastSyncError: String?,
    cloudSyncing: Boolean,
    driveAuthorizationLoading: Boolean,
    driveAuthorizationMessage: String?,
    onEnableCloudBackup: () -> Unit,
    onRetryCloudBackup: () -> Unit,
    onBackUpNow: () -> Unit,
    onRestoreFromDrive: () -> Unit,
    onDisableCloudBackup: () -> Unit,
    onDismissDriveMessage: () -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Only surface what needs action
        if (unassignedCount > 0) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = appColors.accentSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(ManagePage.ACCOUNTS) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = appColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${plural(unassignedCount, "transaction")} without an account",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = appColors.accent
                        )
                        Text("Tap to assign them", fontSize = 12.sp, color = appColors.inkMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = appColors.accent)
                }
            }
        }

        groups.forEach { (label, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = appColors.inkFaint,
                    modifier = Modifier.padding(start = 4.dp)
                )
                ManageSurface {
                    Column {
                        items.forEachIndexed { index, item ->
                            HubRow(item = item, onClick = { onOpen(item.page) })
                            if (index < items.lastIndex) {
                                HorizontalDivider(
                                    color = appColors.border,
                                    modifier = Modifier.padding(start = 66.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Cloud backup ──
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "CLOUD BACKUP",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = appColors.inkFaint,
                modifier = Modifier.padding(start = 4.dp)
            )
            CloudBackupSection(
                googleAccount = googleAccount,
                googleAccountLoading = googleAccountLoading,
                googleAccountError = googleAccountError,
                onGoogleSignIn = onGoogleSignIn,
                onGoogleDisconnect = onGoogleDisconnect,
                onDismissGoogleError = onDismissGoogleError,
                cloudBackupEnabled = cloudBackupEnabled,
                lastCloudBackupModifiedTime = lastCloudBackupModifiedTime,
                cloudSyncState = cloudSyncState,
                lastSyncedTimestamp = lastSyncedTimestamp,
                lastSyncError = lastSyncError,
                cloudSyncing = cloudSyncing,
                driveAuthorizationLoading = driveAuthorizationLoading,
                driveAuthorizationMessage = driveAuthorizationMessage,
                onEnableCloudBackup = onEnableCloudBackup,
                onRetryCloudBackup = onRetryCloudBackup,
                onBackUpNow = onBackUpNow,
                onRestoreFromDrive = onRestoreFromDrive,
                onDisableCloudBackup = onDisableCloudBackup,
                onDismissDriveMessage = onDismissDriveMessage
            )
        }
    }
}

@Composable
private fun HubRow(item: HubItem, onClick: () -> Unit) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(appColors.accentSoft, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = appColors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = appColors.ink)
            Text(
                item.subtitle,
                fontSize = 12.sp,
                color = appColors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = appColors.inkFaint)
    }
}

// ───────────────────────── Detail header ─────────────────────────

@Composable
private fun PageHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onAdd: (() -> Unit)?
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = appColors.ink)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = appColors.ink
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, color = appColors.inkFaint)
            }
        }
        if (onAdd != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(appColors.accent)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, null, tint = appColors.accentInk, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = appColors.accentInk)
            }
        }
    }
}

// ───────────────────────── Categories ─────────────────────────

@Composable
private fun CategoriesPage(
    categories: List<CategoryEntity>,
    usage: Map<String, Int>,
    onAdd: () -> Unit,
    onEdit: (CategoryEntity) -> Unit,
    onDelete: (CategoryEntity) -> Unit
) {
    val appColors = LocalAppColors.current

    if (categories.isEmpty()) {
        ManageEmptyState(
            title = "No categories yet",
            message = "Categories group your spending so analytics can show where your money goes.",
            actionLabel = "Add category",
            onAction = onAdd
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(categories, key = { it.id }) { category ->
            val categoryEnum = try {
                com.example.expensetracker.model.TransactionCategory.valueOf(category.name.uppercase())
            } catch (e: Exception) {
                com.example.expensetracker.model.TransactionCategory.OTHER
            }
            val (_, catColor, catSoftBg) = getCategoryIconAndColor(categoryEnum, appColors.isDark)
            val count = usage[category.name.lowercase()] ?: 0

            ManageSurface(onClick = { onEdit(category) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(catSoftBg, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = catColor
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            category.name,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (count == 0) "No transactions yet" else plural(count, "transaction"),
                            fontSize = 12.sp,
                            color = appColors.inkFaint
                        )
                    }
                    ManageOverflowMenu(
                        actions = listOf(
                            ManageMenuAction("Rename") { onEdit(category) },
                            ManageMenuAction("Delete", destructive = true) { onDelete(category) }
                        )
                    )
                }
            }
        }
    }
}

// ───────────────────────── Rules ─────────────────────────

@Composable
private fun RulesPage(
    mappings: List<CategoryMappingEntity>,
    onAdd: () -> Unit,
    onEdit: (CategoryMappingEntity) -> Unit,
    onDelete: (CategoryMappingEntity) -> Unit
) {
    val appColors = LocalAppColors.current

    if (mappings.isEmpty()) {
        ManageEmptyState(
            title = "No rules yet",
            message = "A rule assigns a category automatically when a transaction's text matches a pattern, like \"swiggy\" → Food.",
            actionLabel = "Add rule",
            onAction = onAdd
        )
        return
    }

    val sorted = remember(mappings) { mappings.sortedBy { it.priority } }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "Rules run top to bottom. The first one that matches decides the category.",
                fontSize = 12.sp,
                color = appColors.inkMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }
        items(sorted, key = { it.id }) { mapping ->
            ManageSurface(onClick = { onEdit(mapping) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ManagePill("#${mapping.priority}", appColors.surfaceAlt, appColors.inkMuted)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = mapping.pattern,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = appColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        ManageOverflowMenu(
                            actions = listOf(
                                ManageMenuAction("Edit") { onEdit(mapping) },
                                ManageMenuAction("Delete", destructive = true) { onDelete(mapping) }
                            )
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("assigns", fontSize = 12.sp, color = appColors.inkFaint)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(appColors.accentSoft, RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                mapping.categoryName,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.accent
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Dialogs (unchanged behavior) ─────────────────────────

@Composable
private fun AddOrEditCategoryDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Category Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddOrEditMappingDialog(
    title: String,
    initialPattern: String,
    initialCategory: String,
    initialPriority: Int,
    availableCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var pattern by remember { mutableStateOf(initialPattern) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var priorityText by remember { mutableStateOf(initialPriority.toString()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val isRegexValid = CategoryMapper.isValidRegex(pattern)
    val showError = pattern.isNotBlank() && !isRegexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern / Regex (e.g. (?i)amman)") },
                    isError = showError,
                    supportingText = {
                        if (showError) {
                            Text("Invalid regular expression syntax", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Mapped Category:")
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dropdownExpanded = true }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Category") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        availableCategories.forEach { catName ->
                            DropdownMenuItem(
                                text = { Text(catName) },
                                onClick = {
                                    selectedCategory = catName
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it },
                    label = { Text("Priority (Lower number = evaluated first)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pattern.isNotBlank() && isRegexValid,
                onClick = {
                    val priority = priorityText.toIntOrNull() ?: 1
                    onSave(pattern.trim(), selectedCategory, priority)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}