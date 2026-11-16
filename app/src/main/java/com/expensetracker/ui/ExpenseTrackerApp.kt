package com.expensetracker.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.data.BillEntity
import com.expensetracker.data.ExpenseEntity
import com.expensetracker.ai.ModelDownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(viewModel: ExpenseViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isLlmEnabled by viewModel.isLlmEnabled.collectAsState()
    val llmDownloadState by viewModel.llmDownloadState.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Chat & Overview, 1: Bills & Reminders

    val weeklySpend = remember(expenses) { viewModel.getWeeklySpend(expenses) }
    val monthlySpend = remember(expenses) { viewModel.getMonthlySpend(expenses) }

    // Speech Recognizer
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.processInput(spokenText)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🤖 Financial AI Assistant", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text("100% Local", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.height(60.dp)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat & Expenses") },
                    label = { Text("Assistant & Chat", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Bills") },
                    label = { Text("Bills & Reminders", fontSize = 11.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: Conversational Assistant with Live Visual Analytics
                    ChatAndOverviewScreen(
                        expenses = expenses,
                        weeklySpend = weeklySpend,
                        monthlySpend = monthlySpend,
                        chatMessages = chatMessages,
                        inputText = inputText,
                        onInputTextChanged = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.processInput(inputText)
                                inputText = ""
                            }
                        },
                        onMicClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak expense (e.g. 'Spent 150 on coffee')")
                            }
                            speechLauncher.launch(intent)
                        },
                        onQuickCategorySelect = { tx, cat ->
                            viewModel.categorizePendingSms(tx, cat)
                        }
                    )
                }
                1 -> {
                    // TAB 1: Bills & Reminders Screen
                    BillsScreen(
                        bills = bills,
                        onMarkPaid = { viewModel.markBillPaid(it) },
                        onRescanSms = { viewModel.runDailyBriefing(lookbackDays = 7) }
                    )
                }
                2 -> {
                    // TAB 2: Dedicated Settings & Configuration
                    SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun ChatAndOverviewScreen(
    expenses: List<ExpenseEntity>,
    weeklySpend: Double,
    monthlySpend: Double,
    chatMessages: List<ChatMessage>,
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onQuickCategorySelect: (com.expensetracker.sms.RawSmsTransaction, String) -> Unit
) {
    var showMetrics by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Collapsible Visual Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { showMetrics = !showMetrics },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Live Spending Overview", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        Text(
                            text = "₹${String.format(Locale.ENGLISH, "%.2f", monthlySpend)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text("Total This Month", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "7-Day: ₹${String.format(Locale.ENGLISH, "%.0f", weeklySpend)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Conversational Chat Feed (Main Experience)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            reverseLayout = true
        ) {
            items(chatMessages.reversed()) { msg ->
                ChatBubble(msg = msg, onCategoryClick = { cat ->
                    if (msg.pendingSms != null) {
                        onQuickCategorySelect(msg.pendingSms, cat)
                    }
                })
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // Voice & Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Mic", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChanged,
                placeholder = { Text("Chat or log: 'Spent 200 on fuel'...", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, onCategoryClick: (String) -> Unit) {
    val isUser = msg.sender == "You"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isUser) {
                    Text("🤖 AI Assistant", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = msg.text,
                    fontSize = 13.sp,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // If message is asking for SMS categorization, show one-tap category chips!
                if (msg.pendingSms != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap to classify:", fontSize = 10.sp, color = Color.Gray)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val categories = listOf("Food & Dining", "Transportation", "Groceries", "Bills", "Shopping", "Personal")
                        items(categories) { cat ->
                            SuggestionChip(
                                onClick = { onCategoryClick(cat) },
                                label = { Text(cat, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BillsScreen(
    bills: List<BillEntity>,
    onMarkPaid: (BillEntity) -> Unit,
    onRescanSms: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Bills & Due Dates", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Daily 9:00 PM reminder active", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton(onClick = onRescanSms, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text("Scan 7 Days SMS", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bills, key = { it.id }) { bill ->
                BillCard(bill = bill, onMarkPaid = { onMarkPaid(bill) })
            }
        }
    }
}

@Composable
fun BillCard(bill: BillEntity, onMarkPaid: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bill.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Due: ${bill.formattedDueDate}", fontSize = 11.sp, color = if (bill.isPaid) Color.Gray else MaterialTheme.colorScheme.error)
                    if (!bill.isPaid) {
                        Text("(${bill.daysRemaining} days left)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("₹${String.format(Locale.ENGLISH, "%.2f", bill.amountDue)}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                if (bill.isPaid) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("PAID ✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                } else {
                    OutlinedButton(onClick = onMarkPaid, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Mark Paid", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: ExpenseViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLlmEnabled by viewModel.isLlmEnabled.collectAsState()
    val llmDownloadState by viewModel.llmDownloadState.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val smsLookbackDays by viewModel.smsLookbackDays.collectAsState()
    val totalLent by viewModel.totalLent.collectAsState()
    val lentExpenses by viewModel.lentExpenses.collectAsState()

    var lendPersonName by remember { mutableStateOf("") }
    var lendAmountText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("⚙️ Settings & Financial Control", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Configure AI model, currencies, accounts, and export data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Section 1: AI & Parsing Engine
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🤖 AI & Expense Parser", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Experimental On-Device LLM", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(
                                if (isLlmEnabled) "Local model active (saved in app files)" else "Standard heuristic & regex parser active",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isLlmEnabled,
                            onCheckedChange = { viewModel.toggleLlm(it) }
                        )
                    }

                    if (isLlmEnabled) {
                        when (val state = llmDownloadState) {
                            is ModelDownloadState.Downloading -> {
                                Text("Downloading model into app files: ${state.progressPercent}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is ModelDownloadState.Ready -> {
                                Text("✓ Local LLM downloaded and ready in app storage", fontSize = 11.sp, color = Color(0xFF2E7D32))
                            }
                            is ModelDownloadState.Error -> {
                                Text(state.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { viewModel.retryLlmDownload() }) {
                                    Text("Retry Download", fontSize = 11.sp)
                                }
                            }
                            is ModelDownloadState.Idle -> {
                                TextButton(onClick = { viewModel.retryLlmDownload() }) {
                                    Text("Download Local Model (~350MB)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Multi-Currency Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("💱 Currency Selection", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Select default currency symbol for your transactions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val currencies = listOf("₹" to "INR (₹)", "$" to "USD ($)", "€" to "EUR (€)", "£" to "GBP (£)", "AED" to "AED (د.إ)")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(currencies) { (sym, label) ->
                            FilterChip(
                                selected = currencySymbol == sym,
                                onClick = { viewModel.setCurrency(sym) },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Section 3: SMS Lookback Days
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📩 SMS Sync Lookback Days", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Select how many past days of bank/UPI SMS to scan", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val daysOptions = listOf(2, 7, 14, 30)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (days in daysOptions) {
                            FilterChip(
                                selected = smsLookbackDays == days,
                                onClick = {
                                    viewModel.setSmsLookbackDays(days)
                                    viewModel.runDailyBriefing(lookbackDays = days)
                                },
                                label = { Text("$days Days", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Receivables & Lending (Khaata / Lent Money)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤝 Receivables / Lent Money", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("$currencySymbol${String.format(java.util.Locale.ENGLISH, "%.2f", totalLent ?: 0.0)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Track money lent to friends or pending reimbursements", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Add quick lent entry
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lendPersonName,
                            onValueChange = { lendPersonName = it },
                            label = { Text("Person", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lendAmountText,
                            onValueChange = { lendAmountText = it },
                            label = { Text("Amount", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val amt = lendAmountText.toDoubleOrNull()
                                if (lendPersonName.isNotBlank() && amt != null && amt > 0) {
                                    viewModel.recordLentAmount(lendPersonName, amt)
                                    lendPersonName = ""
                                    lendAmountText = ""
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text("Add", fontSize = 11.sp)
                        }
                    }

                    if (lentExpenses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        for (item in lentExpenses) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(item.formattedDate, fontSize = 10.sp, color = Color.Gray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("$currencySymbol${item.amount}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    TextButton(onClick = { viewModel.markLentSettled(item) }) {
                                        Text("Settled ✓", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Data Export (CSV / Excel)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📊 Data Export & Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Export all transactions to CSV for viewing in Microsoft Excel or Google Sheets (0 KB APK overhead)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = {
                            val file = viewModel.exportToCsv(context)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Transactions (Excel/Sheets)"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export to CSV (Excel Compatible)")
                    }
                }
            }
        }
    }
}
