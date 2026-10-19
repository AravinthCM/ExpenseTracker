package com.example.expensetracker

import com.example.expensetracker.data.local.TransactionEntity
import com.example.expensetracker.data.local.toDomainModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionCategoryPersistenceTest {

    private fun entity(category: String) = TransactionEntity(
        id = "tx-$category",
        title = category,
        amount = 1.0,
        type = "EXPENSE",
        category = category,
        date = "2026-10-19",
        source = "MANUAL",
        timestamp = 1L
    )

    @Test
    fun builtInCategoryNameClassifiesCaseInsensitivelyAndPreservesDisplayName() {
        val transaction = entity("Food").toDomainModel()

        assertEquals("FOOD", transaction.category.name)
        assertEquals("Food", transaction.displayCategoryName)
    }

    @Test
    fun customCategoryNameDoesNotBecomeOtherInDisplayPath() {
        val transaction = entity("Repairs").toDomainModel()

        assertEquals("OTHER", transaction.category.name)
        assertEquals("Repairs", transaction.displayCategoryName)
    }
}
