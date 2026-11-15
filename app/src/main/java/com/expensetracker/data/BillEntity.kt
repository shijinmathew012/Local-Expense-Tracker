package com.expensetracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,             // e.g. "HDFC Credit Card", "Electricity Bill (KSEB)", "Jio Recharge"
    val category: String,         // "Credit Card", "Electricity", "Mobile Recharge", "Internet", "Water"
    val amountDue: Double,
    val dueDateTimestamp: Long,
    val isPaid: Boolean = false,
    val paidTimestamp: Long? = null
) {
    val formattedDueDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dueDateTimestamp))

    val daysRemaining: Long
        get() {
            val diff = dueDateTimestamp - System.currentTimeMillis()
            return (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
        }
}
