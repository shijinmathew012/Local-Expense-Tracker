package com.expensetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.ai.LocalExpenseParser
import com.expensetracker.ai.LocalLlmManager
import com.expensetracker.ai.ModelDownloadState
import com.expensetracker.data.AppDatabase
import com.expensetracker.data.BillEntity
import com.expensetracker.data.ExpenseEntity
import com.expensetracker.reminder.ReminderScheduler
import com.expensetracker.sms.RawSmsTransaction
import com.expensetracker.sms.SmsReconciler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class ChatMessage(
    val sender: String,
    val text: String,
    val isSystem: Boolean = false,
    val pendingSms: RawSmsTransaction? = null,
    val actionType: String? = null
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val expenseDao = db.expenseDao()
    private val billDao = db.billDao()
    private val parser = LocalExpenseParser(expenseDao)
    private val smsReconciler = SmsReconciler(application, expenseDao, billDao)
    val localLlmManager = LocalLlmManager(application)

    val isLlmEnabled: StateFlow<Boolean> = localLlmManager.isLlmEnabled
    val llmDownloadState: StateFlow<ModelDownloadState> = localLlmManager.downloadState

    fun toggleLlm(enabled: Boolean) {
        localLlmManager.setLlmEnabled(enabled)
    }

    fun retryLlmDownload() {
        localLlmManager.downloadModelInBackground()
    }

    val expenses: StateFlow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSpent: StateFlow<Double?> = expenseDao.getTotalSpent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalIncome: StateFlow<Double?> = expenseDao.getTotalIncome()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val prefs = application.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
    private val _currencySymbol = MutableStateFlow(prefs.getString("selected_currency", "₹") ?: "₹")
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    fun setCurrency(symbol: String) {
        prefs.edit().putString("selected_currency", symbol).apply()
        _currencySymbol.value = symbol
    }

    private val _smsLookbackDays = MutableStateFlow(prefs.getInt("sms_lookback_days", 7))
    val smsLookbackDays: StateFlow<Int> = _smsLookbackDays.asStateFlow()

    fun setSmsLookbackDays(days: Int) {
        prefs.edit().putInt("sms_lookback_days", days).apply()
        _smsLookbackDays.value = days
    }

    val bills: StateFlow<List<BillEntity>> = billDao.getAllBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _pendingSmsQueue = mutableListOf<RawSmsTransaction>()

    init {
        // Schedule daily 9:00 PM reminder
        ReminderScheduler.scheduleDailyReminder(application, hour = 21, minute = 0)

        // Check if on-device Local LLM needs first-launch download to app directory
        localLlmManager.checkFirstLaunchDownload()

        // Seed demo bills if empty
        viewModelScope.launch {
            seedInitialBillsIfEmpty()
            runDailyBriefing(lookbackDays = 2)
        }
    }

    private suspend fun seedInitialBillsIfEmpty() {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        // Seed standard bills: Electricity, Credit Card, Mobile
        val existing = billDao.findPendingBillByKeyword("Electricity")
        if (existing == null) {
            billDao.insert(BillEntity(name = "KSEB Electricity", category = "Electricity", amountDue = 1450.0, dueDateTimestamp = now + (3 * oneDay)))
            billDao.insert(BillEntity(name = "HDFC Credit Card", category = "Credit Card", amountDue = 8200.0, dueDateTimestamp = now + (7 * oneDay)))
            billDao.insert(BillEntity(name = "Jio Fiber Internet", category = "Internet", amountDue = 470.0, dueDateTimestamp = now + (10 * oneDay)))
        }
    }

    fun runDailyBriefing(lookbackDays: Int = 2) {
        viewModelScope.launch {
            val detectedSms = smsReconciler.scanRecentSms(lookbackDays)
            val messages = mutableListOf<ChatMessage>()

            // 1. Initial Personal Finance Greeting
            messages.add(ChatMessage("Assistant", "👋 Welcome back! Scanning for pending bills and SMS payments from the last $lookbackDays days..."))

            // 2. Reconcile matching bills automatically
            var autoReconciledCount = 0
            val unclassified = mutableListOf<RawSmsTransaction>()

            for (tx in detectedSms) {
                if (tx.matchedBill != null) {
                    smsReconciler.autoReconcileBill(tx, tx.matchedBill)
                    messages.add(ChatMessage("Assistant", "⚡ Automatically matched SMS payment of ₹${tx.amount} to your ${tx.matchedBill.name}. Marked as Paid & added to expenses!"))
                    autoReconciledCount++
                } else {
                    unclassified.add(tx)
                }
            }

            _pendingSmsQueue.clear()
            _pendingSmsQueue.addAll(unclassified)

            if (_pendingSmsQueue.isNotEmpty()) {
                val next = _pendingSmsQueue.removeAt(0)
                messages.add(
                    ChatMessage(
                        sender = "Assistant",
                        text = "💳 Found an unlisted debit of ₹${next.amount} at \"${next.merchant}\". What was this expense for?",
                        pendingSms = next
                    )
                )
            } else if (autoReconciledCount == 0) {
                messages.add(ChatMessage("Assistant", "✨ All up to date! Speak or type naturally to log an expense (e.g. \"Spent 150 on coffee\")."))
            }

            _chatMessages.value = messages
        }
    }

    fun processInput(text: String) = processUserInput(text)

    fun processUserInput(text: String) {
        if (text.isBlank()) return

        val currentList = _chatMessages.value.toMutableList()
        currentList.add(ChatMessage("You", text))
        _chatMessages.value = currentList

        viewModelScope.launch {
            val lastAssistantMsg = _chatMessages.value.findLast { it.sender == "Assistant" && it.pendingSms != null }
            if (lastAssistantMsg != null && lastAssistantMsg.pendingSms != null) {
                // User is answering what the SMS was for
                val tx = lastAssistantMsg.pendingSms
                val parsed = parser.parse(text)
                val category = if (parsed.category != "General") parsed.category else "Other"
                val title = if (parsed.description.isNotBlank()) parsed.description else tx.merchant

                val newExpense = ExpenseEntity(
                    title = title,
                    amount = tx.amount,
                    category = category,
                    timestamp = tx.timestamp,
                    source = "SMS",
                    merchant = tx.merchant,
                    isCategorized = true
                )
                expenseDao.insert(newExpense)

                val updatedList = _chatMessages.value.toMutableList()
                updatedList.add(ChatMessage("Assistant", "Got it! Recorded ₹${tx.amount} for \"$title\" under $category."))

                // Prompt next if queue has more
                if (_pendingSmsQueue.isNotEmpty()) {
                    val next = _pendingSmsQueue.removeAt(0)
                    updatedList.add(
                        ChatMessage(
                            sender = "Assistant",
                            text = "Next: found a debit of ₹${next.amount} to \"${next.merchant}\". What was this for?",
                            pendingSms = next
                        )
                    )
                } else {
                    updatedList.add(ChatMessage("Assistant", "🎉 All pending transactions categorized! Your weekly & monthly summaries are updated."))
                }
                _chatMessages.value = updatedList
                return@launch
            }

            // Expense parsing: either with on-device Local LLM (if enabled) or standard parser
            val parsed = if (localLlmManager.isLlmEnabled.value && localLlmManager.isModelReady()) {
                localLlmManager.parseWithLlm(text)
            } else {
                parser.parse(text)
            }
            val resultMessage = parser.execute(parsed)

            val updated = _chatMessages.value.toMutableList()
            updated.add(ChatMessage("Assistant", resultMessage))
            _chatMessages.value = updated
        }
    }

    fun categorizePendingSms(tx: RawSmsTransaction, category: String) {
        processUserInput(category)
    }

    fun markBillPaid(bill: BillEntity) {
        viewModelScope.launch {
            billDao.markAsPaid(bill.id)
            val expense = ExpenseEntity(
                title = bill.name,
                amount = bill.amountDue,
                category = "Bills & Utilities",
                source = "MANUAL",
                isCategorized = true
            )
            expenseDao.insert(expense)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            expenseDao.delete(expense)
        }
    }

    // Weekly & Monthly calculations
    fun getWeeklySpend(expenseList: List<ExpenseEntity>): Double {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -7)
        }
        val weekAgo = cal.timeInMillis
        return expenseList.filter { it.timestamp >= weekAgo }.sumOf { it.amount }
    }

    val lentExpenses: StateFlow<List<ExpenseEntity>> = expenseDao.getLentTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalLent: StateFlow<Double?> = expenseDao.getTotalLent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun recordLentAmount(personName: String, amount: Double, note: String = "Lent") {
        viewModelScope.launch {
            val lent = ExpenseEntity(
                title = "Lent to $personName",
                amount = amount,
                category = "Receivables / Lent",
                merchant = personName,
                transactionType = "LENT",
                accountType = "BANK_ACCOUNT",
                accountName = "Primary Account"
            )
            expenseDao.insert(lent)
        }
    }

    fun markLentSettled(expense: ExpenseEntity) {
        viewModelScope.launch {
            expenseDao.delete(expense)
        }
    }

    fun exportToCsv(context: android.content.Context): java.io.File {
        val file = java.io.File(context.cacheDir, "ExpenseTracker_Export_${System.currentTimeMillis()}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("ID,Date & Time,Type,Title,Amount,Category,Account,Merchant\n")
            for (item in expenses.value) {
                val line = "${item.id},\"${item.formattedDate}\",\"${item.transactionType}\",\"${item.title}\",${item.amount},\"${item.category}\",\"${item.accountName ?: item.accountType}\",\"${item.merchant ?: ""}\"\n"
                writer.write(line)
            }
        }
        return file
    }

    fun getWeeklyCategoryBreakdown(expenseList: List<ExpenseEntity>): Map<String, Double> {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val weekAgo = cal.timeInMillis
        return expenseList
            .filter { it.timestamp >= weekAgo && it.transactionType == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    fun getMonthlyCategoryBreakdown(expenseList: List<ExpenseEntity>): Map<String, Double> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
        }
        val monthStart = cal.timeInMillis
        return expenseList
            .filter { it.timestamp >= monthStart && it.transactionType == "EXPENSE" }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    fun getMonthlyIncome(expenseList: List<ExpenseEntity>): Double {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
        }
        val monthStart = cal.timeInMillis
        return expenseList
            .filter { it.timestamp >= monthStart && it.transactionType == "INCOME" }
            .sumOf { it.amount }
    }
}
