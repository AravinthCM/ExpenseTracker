package com.example.expensetracker.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.example.expensetracker.data.local.SplitGroupEntity
import com.example.expensetracker.data.local.SplitGroupWithMembers
import com.example.expensetracker.model.DraftMember
import com.example.expensetracker.model.SplitGroupValidator
import com.example.expensetracker.model.ValidationResult
import com.example.expensetracker.ui.theme.AppSpacing
import com.example.expensetracker.ui.theme.LocalAppColors
import java.util.UUID

@Composable
fun SplitGroupsTabContent(
    splitGroups: List<SplitGroupWithMembers>,
    onAddGroupClick: () -> Unit,
    onEditGroupClick: (SplitGroupWithMembers) -> Unit,
    onDeleteGroupClick: (SplitGroupEntity) -> Unit
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (splitGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(appColors.surfaceAlt, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = appColors.inkMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No split groups yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = appColors.ink
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Create split groups to configure members and prepare for expense splitting.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp
                        ),
                        color = appColors.inkFaint,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onAddGroupClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.accent,
                            contentColor = appColors.accentInk
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Group", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(splitGroups, key = { it.group.id }) { item ->
                    SplitGroupCard(
                        groupWithMembers = item,
                        onEditClick = { onEditGroupClick(item) },
                        onDeleteClick = { onDeleteGroupClick(item.group) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dashed "+ Add group" block button
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddGroupClick() },
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
                        text = "Add group",
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
}

@Composable
fun SplitGroupCard(
    groupWithMembers: SplitGroupWithMembers,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    val members = groupWithMembers.members

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
                            .padding(end = 10.dp)
                            .size(36.dp)
                            .background(appColors.accentSoft, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = appColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = groupWithMembers.group.name,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = appColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${members.size} ${if (members.size == 1) "member" else "members"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp
                            ),
                            color = appColors.inkFaint
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                            .clickable { onEditClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Group",
                            tint = appColors.inkMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(appColors.surfaceAlt, RoundedCornerShape(8.dp))
                            .clickable { onDeleteClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Group",
                            tint = appColors.negative,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Member Chips Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                members.take(4).forEach { member ->
                    Box(
                        modifier = Modifier
                            .background(
                                if (member.isMe) appColors.accentSoft else appColors.surfaceAlt,
                                RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (member.isMe) {
                                Text(
                                    text = "Me",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.accent
                                )
                                if (member.name.isNotBlank() && !member.name.equals("Me", ignoreCase = true)) {
                                    Text(
                                        text = " (${member.name})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = appColors.accent
                                    )
                                }
                            } else {
                                Text(
                                    text = member.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = appColors.inkMuted
                                )
                            }
                        }
                    }
                }

                if (members.size > 4) {
                    Text(
                        text = "+${members.size - 4} more",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = appColors.inkFaint,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOrEditSplitGroupScreen(
    initialGroup: SplitGroupWithMembers?,
    onSaveGroup: (groupId: String?, name: String, members: List<DraftMember>) -> ValidationResult,
    onCancel: () -> Unit
) {
    val appColors = LocalAppColors.current

    var groupName by remember(initialGroup) {
        mutableStateOf(initialGroup?.group?.name ?: "")
    }

    val membersState = remember(initialGroup) {
        mutableStateListOf<DraftMember>().apply {
            if (initialGroup != null) {
                addAll(initialGroup.members.map { DraftMember(id = it.id, name = it.name, isMe = it.isMe, openingDebtPaise = it.openingDebtPaise) })
            } else {
                // Default initial setup for new group: 2 members
                add(DraftMember(name = "Me", isMe = true))
                add(DraftMember(name = "", isMe = false))
            }
        }
    }

    var validationErrorText by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var hasAttemptedSave by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Check if user made any edits compared to initial state
    val isDirty by remember(groupName, membersState) {
        derivedStateOf {
            if (initialGroup == null) {
                groupName.isNotBlank() || membersState.any { it.name.isNotBlank() && it.name != "Me" }
            } else {
                val originalName = initialGroup.group.name
                val originalMembers = initialGroup.members.map { DraftMember(id = it.id, name = it.name, isMe = it.isMe, openingDebtPaise = it.openingDebtPaise) }
                groupName != originalName || membersState.toList() != originalMembers
            }
        }
    }

    fun handleBackOrCancel() {
        if (isDirty) {
            showDiscardConfirmDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler(enabled = true) {
        handleBackOrCancel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.bg)
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (initialGroup == null) "Create Split Group" else "Edit Split Group",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = appColors.ink
                )
            },
            navigationIcon = {
                IconButton(onClick = { handleBackOrCancel() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Cancel",
                        tint = appColors.ink
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = appColors.surface,
                titleContentColor = appColors.ink
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Group Name Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Group Name",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = appColors.ink
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val groupNameHasError = hasAttemptedSave && groupName.trim().isBlank()
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = {
                            groupName = it
                            validationErrorText = null
                        },
                        placeholder = { Text("e.g. Trip to Goa, Flatmates") },
                        singleLine = true,
                        isError = groupNameHasError,
                        supportingText = {
                            if (groupNameHasError) {
                                Text("Group name is required", color = appColors.negative)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Members Input Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Members (${membersState.size})",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = appColors.ink
                            )
                            Text(
                                text = "Designate exactly one member as 'Me'",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = appColors.inkFaint
                            )
                        }

                        TextButton(
                            onClick = {
                                membersState.add(DraftMember(name = "", isMe = false))
                                validationErrorText = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = appColors.accent
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Member", color = appColors.accent, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    membersState.forEachIndexed { index, member ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(AppSpacing.sm))
                        }

                        val trimmedName = member.name.trim()
                        val isDuplicateName = trimmedName.isNotBlank() &&
                            membersState.count { it.name.trim().equals(trimmedName, ignoreCase = true) } > 1
                        val nameHasError = hasAttemptedSave && (trimmedName.isBlank() || isDuplicateName)
                        val nameErrorMessage = when {
                            !nameHasError -> null
                            trimmedName.isBlank() -> "Name is required"
                            else -> "Name must be unique"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = appColors.surfaceAlt),
                            border = BorderStroke(1.dp, if (nameHasError) appColors.negative else appColors.border)
                        ) {
                            Column(modifier = Modifier.padding(AppSpacing.md)) {
                                // Header label identifying this member
                                Text(
                                    text = if (member.isMe) "You" else "Member ${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = appColors.inkFaint
                                )

                                Spacer(modifier = Modifier.height(AppSpacing.xs))

                                // Row 1: full-width name field
                                OutlinedTextField(
                                    value = member.name,
                                    onValueChange = { newName ->
                                        membersState[index] = member.copy(name = newName)
                                        validationErrorText = null
                                    },
                                    placeholder = { Text(if (member.isMe) "Your name (Me)" else "Member name") },
                                    singleLine = true,
                                    isError = nameHasError,
                                    supportingText = nameErrorMessage?.let { message ->
                                        { Text(message, color = appColors.negative) }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(AppSpacing.sm))

                                // Row 2: existing-owed amount, Me toggle, and remove action
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                                ) {
                                    if (!member.isMe) {
                                        OutlinedTextField(
                                            value = if (member.openingDebtPaise == 0L) "" else (member.openingDebtPaise / 100.0).toString(),
                                            onValueChange = { value ->
                                                val paise = value.toDoubleOrNull()?.let { Math.round(it * 100.0) } ?: 0L
                                                membersState[index] = member.copy(openingDebtPaise = paise.coerceAtLeast(0L))
                                                validationErrorText = null
                                            },
                                            label = { Text("Existing owed") },
                                            placeholder = { Text("₹0.00") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Text(
                                            text = "Your balance is tracked from account transactions, not entered here.",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = appColors.inkFaint,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // "Me" toggle
                                    FilterChip(
                                        selected = member.isMe,
                                        onClick = {
                                            // Set this member as Me, and all others as not Me
                                            membersState.indices.forEach { i ->
                                                membersState[i] = membersState[i].copy(isMe = (i == index))
                                            }
                                            validationErrorText = null
                                        },
                                        label = {
                                            Text("Me", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        },
                                        leadingIcon = if (member.isMe) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = appColors.accent,
                                            selectedLabelColor = appColors.accentInk,
                                            selectedLeadingIconColor = appColors.accentInk,
                                            containerColor = appColors.surface,
                                            labelColor = appColors.inkMuted
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = member.isMe,
                                            borderColor = appColors.border,
                                            selectedBorderColor = appColors.accent
                                        ),
                                        modifier = Modifier.heightIn(min = AppSpacing.minTouchTarget)
                                    )

                                    // Remove member action, visually distinct via destructive tint
                                    IconButton(
                                        onClick = {
                                            membersState.removeAt(index)
                                            validationErrorText = null
                                        },
                                        modifier = Modifier.size(AppSpacing.minTouchTarget)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove member",
                                            tint = appColors.negative,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error alert message if validation fails
            validationErrorText?.let { errorMsg ->
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(appColors.negativeSoft, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMsg,
                        color = appColors.negative,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        // Bottom Actions Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = appColors.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { handleBackOrCancel() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = appColors.ink)
                }

                Button(
                    onClick = {
                        if (isSaving) return@Button
                        hasAttemptedSave = true
                        isSaving = true
                        val result = onSaveGroup(
                            initialGroup?.group?.id,
                            groupName,
                            membersState.toList()
                        )
                        if (result.isValid) {
                            onCancel() // Close screen after successful save
                        } else {
                            validationErrorText = result.getFirstErrorMessage()
                            isSaving = false
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.accent,
                        contentColor = appColors.accentInk
                    )
                ) {
                    Text("Save Group", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Confirmation dialog for discarding unsaved changes
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text("Discard Unsaved Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        onCancel()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}
