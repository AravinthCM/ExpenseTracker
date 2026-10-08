package com.example.expensetracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.LocalAppColors

@Composable
fun FilterBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    selectedSource: String,
    onSourceSelected: (String) -> Unit,
    selectedDateOption: String,
    onDateOptionSelected: (String) -> Unit,
    sortOption: String,
    onSortOptionSelected: (String) -> Unit,
    isFilterActive: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var typeDropdownExpanded by remember { mutableStateOf(false) }
    var sourceDropdownExpanded by remember { mutableStateOf(false) }
    var dateDropdownExpanded by remember { mutableStateOf(false) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search transactions...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors.inkFaint
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = appColors.inkFaint,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = appColors.inkFaint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = appColors.surface,
                unfocusedContainerColor = appColors.surface,
                focusedBorderColor = appColors.accent,
                unfocusedBorderColor = appColors.border,
                focusedTextColor = appColors.ink,
                unfocusedTextColor = appColors.ink
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Horizontally Scrollable Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Filter
            FilterChipWithDropdown(
                label = if (selectedCategory == "All") "Category" else "Cat: $selectedCategory",
                selected = selectedCategory != "All",
                onClick = { categoryDropdownExpanded = true },
                expanded = categoryDropdownExpanded,
                onDismiss = { categoryDropdownExpanded = false },
                options = listOf("All") + categories,
                onOptionSelected = onCategorySelected
            )

            // Type Filter
            FilterChipWithDropdown(
                label = if (selectedType == "All") "Type" else "Type: $selectedType",
                selected = selectedType != "All",
                onClick = { typeDropdownExpanded = true },
                expanded = typeDropdownExpanded,
                onDismiss = { typeDropdownExpanded = false },
                options = listOf("All", "Expense", "Income", "Transfer"),
                onOptionSelected = onTypeSelected
            )

            // Source Filter
            FilterChipWithDropdown(
                label = if (selectedSource == "All") "Source" else "Source: $selectedSource",
                selected = selectedSource != "All",
                onClick = { sourceDropdownExpanded = true },
                expanded = sourceDropdownExpanded,
                onDismiss = { sourceDropdownExpanded = false },
                options = listOf("All", "Manual", "GPay", "Bank SMS", "Bank Email"),
                onOptionSelected = onSourceSelected
            )

            // Date Filter
            FilterChipWithDropdown(
                label = if (selectedDateOption == "All time") "Date" else "Date: $selectedDateOption",
                selected = selectedDateOption != "All time",
                onClick = { dateDropdownExpanded = true },
                expanded = dateDropdownExpanded,
                onDismiss = { dateDropdownExpanded = false },
                options = listOf("All time", "Today", "This week", "This month", "Custom"),
                onOptionSelected = onDateOptionSelected
            )

            // Sort Filter
            FilterChipWithDropdown(
                label = "Sort: $sortOption",
                selected = sortOption != "Newest first",
                onClick = { sortDropdownExpanded = true },
                expanded = sortDropdownExpanded,
                onDismiss = { sortDropdownExpanded = false },
                options = listOf("Newest first", "Oldest first"),
                onOptionSelected = onSortOptionSelected
            )

            // Clear Filters Button
            if (isFilterActive) {
                TextButton(onClick = onClearFilters) {
                    Text(
                        text = "Clear filters",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = appColors.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipWithDropdown(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    val appColors = LocalAppColors.current

    Box {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp)
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (selected) appColors.accent else appColors.inkFaint
                )
            },
            shape = RoundedCornerShape(100.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = appColors.accentSoft,
                selectedLabelColor = appColors.accent,
                containerColor = appColors.surface,
                labelColor = appColors.ink
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = appColors.border,
                selectedBorderColor = appColors.accent,
                borderWidth = 1.dp,
                selectedBorderWidth = 1.dp
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors.ink
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        onDismiss()
                    }
                )
            }
        }
    }
}
