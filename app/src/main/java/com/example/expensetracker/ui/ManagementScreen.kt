package com.example.expensetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.example.expensetracker.ui.theme.BorderSubtle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRow
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.LocalAppColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.local.CategoryEntity
import com.example.expensetracker.data.local.CategoryMappingEntity
import com.example.expensetracker.service.CategoryMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementScreen(
    categories: List<CategoryEntity>,
    mappings: List<CategoryMappingEntity>,
    paymentTemplates: List<com.example.expensetracker.data.local.PaymentTemplateEntity> = emptyList(),
    splitGroups: List<com.example.expensetracker.data.local.SplitGroupWithMembers> = emptyList(),
    accountsWithBalances: List<com.example.expensetracker.model.AccountWithBalance> = emptyList(),
    unassignedTransactionCount: Int = 0,
    initialSelectedTab: Int = 0,
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
    onSaveSplitGroup: (groupId: String?, name: String, members: List<com.example.expensetracker.model.DraftMember>) -> com.example.expensetracker.model.ValidationResult = { _, _, _ -> com.example.expensetracker.model.ValidationResult(false) },
    onDeleteSplitGroup: (com.example.expensetracker.data.local.SplitGroupEntity) -> Unit = {},
    onSaveAccount: (id: String?, name: String, openingBalancePaise: Long, isPrimary: Boolean, accountNumber: String?) -> String? = { _, _, _, _, _ -> null },
    onSetPrimaryAccount: (accountId: String) -> Unit = {},
    onSetAccountArchived: (accountId: String, isArchived: Boolean) -> Unit = { _, _ -> },
    onDeleteAccount: (com.example.expensetracker.data.local.AccountEntity) -> Unit = {},
    onAssignUnassignedTransactions: (accountId: String) -> Unit = {},
    unresolvedObservations: List<com.example.expensetracker.data.local.BalanceObservationEntity> = emptyList(),
    reconciliationResults: Map<String, com.example.expensetracker.model.ReconciliationResult> = emptyMap(),
    onResolveObservation: (observationId: String, accountId: String, rememberMapping: Boolean) -> Unit = { _, _, _ -> },
    onDeleteObservation: (observationId: String) -> Unit = {},
    totalTransactionCount: Int = 0,
    backupExportState: com.example.expensetracker.data.backup.BackupExportState = com.example.expensetracker.data.backup.BackupExportState.Idle,
    onExportBackupToUri: (android.net.Uri) -> Unit = {},
    onShareBackup: (((android.content.Intent) -> Unit) -> Unit) = {},
    onDismissBackupState: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    var selectedTabIndex by remember(initialSelectedTab) { mutableIntStateOf(initialSelectedTab) }

    // Dialog States
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var deletingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    var showAddMappingDialog by remember { mutableStateOf(false) }
    var editingMapping by remember { mutableStateOf<CategoryMappingEntity?>(null) }

    var showAddTemplateDialog by remember(initialSampleForTemplate) { mutableStateOf(!initialSampleForTemplate.isNullOrBlank()) }
    var editingTemplate by remember { mutableStateOf<com.example.expensetracker.data.local.PaymentTemplateEntity?>(null) }
    var testingTemplate by remember { mutableStateOf<com.example.expensetracker.data.local.PaymentTemplateEntity?>(null) }

    // Account Dialog States
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<com.example.expensetracker.data.local.AccountEntity?>(null) }

    // Split Group States
    var isCreatingGroup by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<com.example.expensetracker.data.local.SplitGroupWithMembers?>(null) }
    var deletingGroup by remember { mutableStateOf<com.example.expensetracker.data.local.SplitGroupEntity?>(null) }

    if (showAddAccountDialog || editingAccount != null) {
        com.example.expensetracker.ui.components.AddOrEditAccountDialog(
            initialAccount = editingAccount,
            onDismiss = {
                showAddAccountDialog = false
                editingAccount = null
            },
            onSave = onSaveAccount
        )
    }

    if (isCreatingGroup || editingGroup != null) {
        com.example.expensetracker.ui.components.CreateOrEditSplitGroupScreen(
            initialGroup = editingGroup,
            onSaveGroup = onSaveSplitGroup,
            onCancel = {
                isCreatingGroup = false
                editingGroup = null
            }
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                val appColors = LocalAppColors.current

                // Subtab Strip with horizontal scrolling for 5 tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { selectedTabIndex = 0 }
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Categories (${categories.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == 0) appColors.ink else appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedTabIndex == 0) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(70.dp)
                                        .background(appColors.accent, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clickable { selectedTabIndex = 1 }
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Regex mappings (${mappings.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == 1) appColors.ink else appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedTabIndex == 1) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(90.dp)
                                        .background(appColors.accent, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clickable { selectedTabIndex = 2 }
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Templates (${paymentTemplates.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == 2) appColors.ink else appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedTabIndex == 2) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(70.dp)
                                        .background(appColors.accent, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clickable { selectedTabIndex = 3 }
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Split groups (${splitGroups.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == 3) appColors.ink else appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedTabIndex == 3) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(75.dp)
                                        .background(appColors.accent, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clickable { selectedTabIndex = 4 }
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Accounts (${accountsWithBalances.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == 4) appColors.ink else appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedTabIndex == 4) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(65.dp)
                                        .background(appColors.accent, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clickable { selectedTabIndex = 5 }
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Backup & Export",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTabIndex == 5) appColors.ink else appColors.inkFaint
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (selectedTabIndex == 5) {
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(90.dp)
                                        .background(appColors.accent, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }

                when (selectedTabIndex) {
                    0 -> CategoryTabContent(
                        categories = categories,
                        onAddCategoryClick = { showAddCategoryDialog = true },
                        onEditCategoryClick = { editingCategory = it },
                        onDeleteCategoryClick = { deletingCategory = it }
                    )
                    1 -> MappingTabContent(
                        mappings = mappings,
                        categories = categories,
                        onAddMappingClick = { showAddMappingDialog = true },
                        onEditMappingClick = { editingMapping = it },
                        onDeleteMappingClick = onDeleteMapping
                    )
                    2 -> com.example.expensetracker.ui.components.PaymentTemplatesTabContent(
                        templates = paymentTemplates,
                        onAddTemplateClick = { showAddTemplateDialog = true },
                        onEditTemplateClick = { editingTemplate = it },
                        onTestTemplateClick = { testingTemplate = it },
                        onToggleEnabledClick = onTogglePaymentTemplateEnabled,
                        onDeleteTemplateClick = onDeletePaymentTemplate
                    )
                    3 -> com.example.expensetracker.ui.components.SplitGroupsTabContent(
                        splitGroups = splitGroups,
                        onAddGroupClick = { isCreatingGroup = true },
                        onEditGroupClick = { editingGroup = it },
                        onDeleteGroupClick = { deletingGroup = it }
                    )
                    4 -> com.example.expensetracker.ui.components.AccountsTabContent(
                        accountsWithBalances = accountsWithBalances,
                        unassignedCount = unassignedTransactionCount,
                        unresolvedObservations = unresolvedObservations,
                        reconciliationResults = reconciliationResults,
                        onAddAccountClick = { showAddAccountDialog = true },
                        onEditAccountClick = { editingAccount = it },
                        onSetPrimaryAccountClick = onSetPrimaryAccount,
                        onToggleArchivedClick = { acc -> onSetAccountArchived(acc.id, !acc.isArchived) },
                        onDeleteAccountClick = onDeleteAccount,
                        onAssignUnassignedClick = onAssignUnassignedTransactions,
                        onResolveObservation = onResolveObservation,
                        onDeleteObservation = onDeleteObservation
                    )
                    5 -> com.example.expensetracker.ui.components.BackupTabContent(
                        totalTransactions = totalTransactionCount,
                        totalAccounts = accountsWithBalances.size,
                        totalCategories = categories.size,
                        totalSplitGroups = splitGroups.size,
                        totalTemplates = paymentTemplates.size,
                        backupExportState = backupExportState,
                        onExportToUri = onExportBackupToUri,
                        onShareBackup = onShareBackup,
                        onDismissState = onDismissBackupState
                    )
                }
            }
        }
    }

    // --- Payment Template Dialogs ---
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

    // --- Category Dialogs ---
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
                TextButton(
                    onClick = {
                        onDeleteCategory(category)
                        deletingCategory = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Mapping Dialogs ---
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

    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("Delete Split Group?") },
            text = { Text("Are you sure you want to delete '${group.name}'? Its members will be removed. Existing transactions will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSplitGroup(group)
                        deletingGroup = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingGroup = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryTabContent(
    categories: List<CategoryEntity>,
    onAddCategoryClick: () -> Unit,
    onEditCategoryClick: (CategoryEntity) -> Unit,
    onDeleteCategoryClick: (CategoryEntity) -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(categories, key = { it.id }) { category ->
                val categoryEnum = try {
                    com.example.expensetracker.model.TransactionCategory.valueOf(category.name.uppercase())
                } catch (e: Exception) {
                    com.example.expensetracker.model.TransactionCategory.OTHER
                }
                val (_, catColor, catSoftBg) = com.example.expensetracker.ui.components.getCategoryIconAndColor(
                    categoryEnum,
                    appColors.isDark
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.cardBackground),
                    border = BorderStroke(1.dp, appColors.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(34.dp)
                                    .background(catSoftBg, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = catColor
                                )
                            }
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.5.sp
                                ),
                                color = appColors.ink
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Soft action buttons
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                                    .clickable { onEditCategoryClick(category) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = appColors.inkMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                                    .clickable { onDeleteCategoryClick(category) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = appColors.negative,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dashed "+ Add category" block button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAddCategoryClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
            border = BorderStroke(1.dp, appColors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = appColors.ink,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add category",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = appColors.ink
                )
            }
        }
    }
}

@Composable
private fun MappingTabContent(
    mappings: List<CategoryMappingEntity>,
    categories: List<CategoryEntity>,
    onAddMappingClick: () -> Unit,
    onEditMappingClick: (CategoryMappingEntity) -> Unit,
    onDeleteMappingClick: (CategoryMappingEntity) -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(mappings, key = { it.id }) { mapping ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.cardBackground),
                    border = BorderStroke(1.dp, appColors.cardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Monospace Pattern Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = mapping.pattern,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.5.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                color = appColors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "→ ",
                                    fontSize = 12.sp,
                                    color = appColors.inkFaint
                                )
                                Box(
                                    modifier = Modifier
                                        .background(appColors.accentSoft, RoundedCornerShape(100.dp))
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = mapping.categoryName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = appColors.accent
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                                        .clickable { onEditMappingClick(mapping) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = appColors.inkMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                                        .clickable { onDeleteMappingClick(mapping) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = appColors.negative,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dashed "+ Add rule" block button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAddMappingClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
            border = BorderStroke(1.dp, appColors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = appColors.ink,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Add rule",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    color = appColors.ink
                )
            }
        }
    }
}

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
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim()) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
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
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
