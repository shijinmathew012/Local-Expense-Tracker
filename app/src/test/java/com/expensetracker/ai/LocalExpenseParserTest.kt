package com.expensetracker.ai

import com.expensetracker.data.ExpenseDao
import com.expensetracker.data.ExpenseEntity
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class LocalExpenseParserTest {

    private lateinit var parser: LocalExpenseParser

    // Minimal mock DAO for unit testing without Android runtime
    private val mockDao = object : ExpenseDao {
        override fun getAllExpenses() = flowOf(emptyList<ExpenseEntity>())
        override fun getTotalSpent() = flowOf(0.0)
        override fun getTotalIncome() = flowOf(0.0)
        override fun getLentTransactions() = flowOf(emptyList<ExpenseEntity>())
        override fun getTotalLent() = flowOf(0.0)
        override suspend fun findFirstMatching(query: String): ExpenseEntity? = null
        override suspend fun insert(expense: ExpenseEntity): Long = 1L
        override suspend fun update(expense: ExpenseEntity) {}
        override suspend fun delete(expense: ExpenseEntity) {}
        override suspend fun clearAll() {}
    }

    @Before
    fun setUp() {
        parser = LocalExpenseParser(mockDao)
    }

    @Test
    fun parse_coffeeExpense_correctlyCategorizesFood() {
        val parsed = parser.parse("Spent 150 on coffee")
        assertEquals(ActionType.ADD, parsed.action)
        assertEquals(150.0, parsed.amount)
        assertEquals("Food & Dining", parsed.category)
        assertEquals("Coffee", parsed.description)
    }

    @Test
    fun parse_uberTaxi_correctlyCategorizesTransportation() {
        val parsed = parser.parse("Paid 320 for uber taxi")
        assertEquals(ActionType.ADD, parsed.action)
        assertEquals(320.0, parsed.amount)
        assertEquals("Transportation", parsed.category)
    }

    @Test
    fun parse_supermarketMilk_correctlyCategorizesGroceries() {
        val parsed = parser.parse("Bought milk for 55")
        assertEquals(ActionType.ADD, parsed.action)
        assertEquals(55.0, parsed.amount)
        assertEquals("Groceries", parsed.category)
    }

    @Test
    fun parse_deleteCommand_detectsDeleteAction() {
        val parsed = parser.parse("Delete lunch")
        assertEquals(ActionType.DELETE, parsed.action)
        assertEquals("Lunch", parsed.description)
    }
}
