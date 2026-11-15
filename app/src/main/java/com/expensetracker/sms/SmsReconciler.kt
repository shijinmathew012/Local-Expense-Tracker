package com.expensetracker.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.expensetracker.data.BillDao
import com.expensetracker.data.BillEntity
import com.expensetracker.data.ExpenseDao
import com.expensetracker.data.ExpenseEntity
import java.util.Locale

data class RawSmsTransaction(
    val amount: Double,
    val merchant: String,
    val timestamp: Long,
    val rawBody: String,
    val matchedBill: BillEntity? = null
)

class SmsReconciler(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val billDao: BillDao
) {

    suspend fun scanRecentSms(lookbackDays: Int = 2): List<RawSmsTransaction> {
        val detected = mutableListOf<RawSmsTransaction>()
        val cutoffTime = System.currentTimeMillis() - (lookbackDays.toLong() * 24 * 60 * 60 * 1000)

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoffTime.toString())

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val bodyIndex = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val body = it.getString(bodyIndex) ?: continue
                    val date = it.getLong(dateIndex)

                    val parsed = parseDebitSms(body, date)
                    if (parsed != null) {
                        // Check if it matches an existing pending bill
                        val matchedBill = billDao.findPendingBillByKeyword(parsed.merchant)
                        detected.add(parsed.copy(matchedBill = matchedBill))
                    }
                }
            }
        } catch (e: SecurityException) {
            // READ_SMS permission not granted yet
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return detected
    }

    private fun parseDebitSms(body: String, timestamp: Long): RawSmsTransaction? {
        val lower = body.lowercase(Locale.ENGLISH)

        // Must indicate a payment or debit
        val isDebit = lower.contains("debited") || lower.contains("spent") || 
                      lower.contains("paid") || lower.contains("sent") ||
                      lower.contains("vpa") || lower.contains("upi")

        if (!isDebit || lower.contains("failed") || lower.contains("declined")) {
            return null
        }

        // Amount Regex: Rs. 450, INR 450, ₹ 450
        val amountRegex = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(body) ?: return null
        val amountStr = amountMatch.groupValues[1].replace(",", "")
        val amount = amountStr.toDoubleOrNull() ?: return null

        // Merchant / Beneficiary extraction
        val merchantRegex = Regex("""(?:to|at|vpa|info)\s+([A-Za-z0-9\._@\-]+)""", RegexOption.IGNORE_CASE)
        val merchantMatch = merchantRegex.find(body)
        val merchant = merchantMatch?.groupValues?.get(1)?.trim() ?: "Merchant"

        return RawSmsTransaction(
            amount = amount,
            merchant = merchant,
            timestamp = timestamp,
            rawBody = body
        )
    }

    suspend fun autoReconcileBill(tx: RawSmsTransaction, bill: BillEntity): ExpenseEntity {
        // Mark bill as paid
        billDao.markAsPaid(bill.id, tx.timestamp)

        // Log expense
        val expense = ExpenseEntity(
            title = bill.name,
            amount = tx.amount,
            category = "Bills & Utilities",
            timestamp = tx.timestamp,
            source = "SMS",
            merchant = tx.merchant,
            isCategorized = true
        )
        val id = expenseDao.insert(expense)
        return expense.copy(id = id)
    }
}
