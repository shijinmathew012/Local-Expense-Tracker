package com.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "MANUAL", // "MANUAL" or "SMS"
    val merchant: String? = null,
    val isCategorized: Boolean = true,
    val smsRefId: String? = null,
    val transactionType: String = "EXPENSE", // "EXPENSE" or "INCOME"
    val accountType: String = "BANK_ACCOUNT", // "BANK_ACCOUNT", "CREDIT_CARD", "UPI", "CASH"
    val accountName: String? = null
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))
}
