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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerApp(viewModel: ExpenseViewModel) {
    val expenses by viewModel.expenses.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()

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
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTab == 0) {
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
            } else {
                // TAB 1: Bills & Reminders Screen
                BillsScreen(
                    bills = bills,
                    onMarkPaid = { viewModel.markBillPaid(it) },
                    onRescanSms = { viewModel.runDailyBriefing(lookbackDays = 7) }
                )
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
