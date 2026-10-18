package com.example.expensetracker

import com.example.expensetracker.data.local.CategoryMappingEntity
import com.example.expensetracker.service.CategoryMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLearningTest {
    @Test
    fun escapedMerchantRuleMatchesSameNameButNotSimilarNames() {
        val merchant = "Hari Agency (Kovai)"
        val pattern = "(?i)${Regex.escape(merchant)}"
        val mapping = CategoryMappingEntity("rule-1", pattern, "Fuel")

        assertEquals("Fuel", CategoryMapper.matchCategory("Paid to hari agency (kovai)", listOf(mapping)))
        assertEquals("Other", CategoryMapper.matchCategory("Paid to Hari Agency Kovai", listOf(mapping)))
    }

    @Test
    fun invalidRulesAreIgnoredSafely() {
        val invalid = CategoryMappingEntity("bad", "[", "Fuel")
        assertFalse(CategoryMapper.isValidRegex(invalid.pattern))
        assertEquals("Other", CategoryMapper.matchCategory("Hari Agency", listOf(invalid)))
    }

    @Test
    fun duplicatePatternCanBeUpdatedWithoutCreatingAnotherRule() {
        val pattern = "(?i)${Regex.escape("Hari Agency")}"
        val existing = CategoryMappingEntity("rule-1", pattern, "Shopping")
        val updated = existing.copy(categoryName = "Fuel")
        assertTrue(CategoryMapper.isValidRegex(updated.pattern))
        assertEquals("Fuel", CategoryMapper.matchCategory("HARI AGENCY", listOf(updated)))
    }
}