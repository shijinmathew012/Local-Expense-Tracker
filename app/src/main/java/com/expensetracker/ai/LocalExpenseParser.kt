package com.expensetracker.ai

import com.expensetracker.data.ExpenseDao
import com.expensetracker.data.ExpenseEntity

enum class ActionType { ADD, UPDATE, DELETE, ASK }

data class ParsedCommand(
    val action: ActionType,
    val amount: Double?,
    val category: String,
    val description: String,
    val rawText: String,
    val question: String? = null
)

class LocalExpenseParser(private val expenseDao: ExpenseDao) {

    // Multi-turn conversational memory for missing details
    private var pendingFollowUp: Pair<String, String>? = null // (title, category)

    private val categoryMap = mapOf(
        "Food & Dining" to listOf("food", "lunch", "dinner", "breakfast", "coffee", "tea", "cafe", "restaurant", "snack", "biryani", "swiggy", "zomato"),
        "Transportation" to listOf("taxi", "cab", "uber", "ola", "auto", "bus", "train", "metro", "petrol", "diesel", "fuel", "flight"),
        "Groceries" to listOf("grocery", "groceries", "vegetables", "fruits", "milk", "bread", "supermarket"),
        "Shopping" to listOf("cloth", "clothes", "shirt", "pants", "shoes", "amazon", "flipkart", "shopping", "dress"),
        "Bills & Utilities" to listOf("recharge", "wifi", "internet", "electricity", "water", "rent", "bill", "subscription", "netflix"),
        "Entertainment" to listOf("movie", "cinema", "game", "party", "concert"),
        "Healthcare" to listOf("doctor", "medicine", "hospital", "pharmacy", "clinic")
    )

    fun parse(input: String): ParsedCommand {
        val text = input.trim().lowercase()

        // 1. Detect Action
        var action = ActionType.ADD
        if (text.startsWith("delete") || text.startsWith("remove") || text.startsWith("cancel")) {
            action = ActionType.DELETE
        } else if (text.startsWith("change") || text.startsWith("update") || text.startsWith("modify")) {
            action = ActionType.UPDATE
        }

        // 2. Extract Amount
        val amountRegex = Regex("""(?:rs\.?|inr|₹|\$)?\s*(\d+(?:\.\d+)?)\s*(?:rs\.?|rupees|bucks|roopa)?""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(text)
        val amount = amountMatch?.groupValues?.get(1)?.toDoubleOrNull()

        // 3. Infer Category
        var matchedCategory = "General"
        for ((cat, keywords) in categoryMap) {
            if (keywords.any { text.contains(it) }) {
                matchedCategory = cat
                break
            }
        }

        // 4. Clean Description
        var cleanDesc = text
            .replace(Regex("""^(add|spent|spend|paid|bought|purchase|for|delete|remove|change|update|modify)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(amountRegex, "")
            .replace(Regex("""\b(rupees|rs|for|on|at|to)\b""", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleanDesc.isEmpty()) {
            cleanDesc = if (matchedCategory != "General") matchedCategory else "Expense"
        }
        cleanDesc = cleanDesc.replaceFirstChar { it.uppercase() }

        return ParsedCommand(
            action = action,
            amount = amount,
            category = matchedCategory,
            description = cleanDesc,
            rawText = input
        )
    }

    suspend fun execute(parsed: ParsedCommand): String {
        // Handle conversational follow-up
        if (pendingFollowUp != null) {
            if (parsed.amount != null) {
                val (title, category) = pendingFollowUp!!
                pendingFollowUp = null
                val expense = ExpenseEntity(title = title, amount = parsed.amount, category = category)
                expenseDao.insert(expense)
                return "Got it! Recorded ₹${parsed.amount} for \"$title\" under $category."
            } else {
                return "I still need the price for \"${pendingFollowUp!!.first}\". How much was it?"
            }
        }

        return when (parsed.action) {
            ActionType.DELETE -> {
                val existing = expenseDao.findFirstMatching(parsed.description)
                if (existing != null) {
                    expenseDao.delete(existing)
                    "Deleted \"${existing.title}\" (₹${existing.amount})."
                } else {
                    "Could not find an expense matching \"${parsed.description}\"."
                }
            }
            ActionType.UPDATE -> {
                if (parsed.amount == null) return "Please specify the new amount (e.g., \"Change lunch to 200\")."
                val existing = expenseDao.findFirstMatching(parsed.description)
                if (existing != null) {
                    val updated = existing.copy(amount = parsed.amount)
                    expenseDao.update(updated)
                    "Updated \"${existing.title}\" to ₹${parsed.amount}."
                } else {
                    "Could not find \"${parsed.description}\" to update."
                }
            }
            ActionType.ADD -> {
                if (parsed.amount == null) {
                    if (parsed.description.length > 2) {
                        pendingFollowUp = Pair(parsed.description, parsed.category)
                        return "I noted \"${parsed.description}\" under ${parsed.category}. How much did you spend?"
                    }
                    return "Please include the amount (e.g. \"Spent 150 on coffee\")."
                }
                val expense = ExpenseEntity(
                    title = parsed.description,
                    amount = parsed.amount,
                    category = parsed.category
                )
                expenseDao.insert(expense)
                "Added ₹${expense.amount} for \"${expense.title}\" (${expense.category})."
            }
            ActionType.ASK -> parsed.question ?: "Could you clarify the expense details?"
        }
    }
}
