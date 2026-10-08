package com.example.expensetracker.service

import com.example.expensetracker.data.local.CategoryMappingEntity

object CategoryMapper {

    fun matchCategory(
        textToMatch: String,
        mappings: List<CategoryMappingEntity>,
        fallbackCategory: String = "Other"
    ): String {
        if (textToMatch.isBlank() || mappings.isEmpty()) return fallbackCategory

        for (mapping in mappings) {
            val pattern = mapping.pattern.trim()
            if (pattern.isBlank()) continue

            try {
                val regex = Regex(pattern)
                if (regex.containsMatchIn(textToMatch)) {
                    return mapping.categoryName
                }
            } catch (e: Exception) {
                // Ignore invalid regex safely during processing
            }
        }

        return fallbackCategory
    }

    fun isValidRegex(pattern: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            Regex(pattern)
            true
        } catch (e: Exception) {
            false
        }
    }
}
