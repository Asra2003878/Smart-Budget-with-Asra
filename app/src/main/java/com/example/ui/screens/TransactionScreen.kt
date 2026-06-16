package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Localization
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    viewModel: FinanceViewModel
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val expenses by viewModel.expenses.collectAsState()

    val lang = profile?.selectedLanguageCode ?: "en"

    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterCategory by viewModel.filterCategory.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val filterDate by viewModel.filterDate.collectAsState()

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogIsIncome by remember { mutableStateOf(false) }

    // Forms fields
    var formAmount by remember { mutableStateOf("") }
    var formNote by remember { mutableStateOf("") }
    var formCategory by remember { mutableStateOf("") }
    var formSelectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    val incomeCategories = listOf("Salary", "Business", "Freelancing", "Gifts", "Other")
    val expenseCategories = listOf("Food", "Transport", "Shopping", "Education", "Bills", "Healthcare", "Entertainment", "Rent", "Others")

    // Filtered transaction items
    val combinedList = remember(incomes, expenses, searchQuery, filterCategory, filterType, filterDate) {
        val list = mutableListOf<CombinedTx>()
        if (filterType == "All" || filterType == "Income") {
            incomes.forEach { list.add(CombinedTx(it.id, true, it.amount, it.category, it.note, it.date)) }
        }
        if (filterType == "All" || filterType == "Expense") {
            expenses.forEach { list.add(CombinedTx(it.id, false, it.amount, it.category, it.note, it.date)) }
        }

        list.filter { tx ->
            val matchesQuery = tx.note.contains(searchQuery, ignoreCase = true) ||
                    tx.category.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = filterCategory == "All" || tx.category.equals(filterCategory, ignoreCase = true)

            val matchesDate = if (filterDate == null) true else {
                val cal1 = Calendar.getInstance().apply { timeInMillis = tx.date }
                val cal2 = Calendar.getInstance().apply { timeInMillis = filterDate!! }
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
            }

            matchesQuery && matchesCategory && matchesDate
        }.sortedByDescending { it.date }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("transaction_screen_root")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Search & Filters
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = { Text(Localization.getString("search_filter", lang)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_tx_input"),
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable filters headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type selector
                    listOf("All", "Income", "Expense").forEach { type ->
                        FilterChip(
                            selected = filterType == type,
                            onClick = { viewModel.setFilterType(type) },
                            label = { Text(type) },
                            modifier = Modifier.testTag("filter_type_$type")
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Date filter toggle button
                    IconButton(
                        onClick = {
                            val calendar = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    }
                                    viewModel.setFilterDate(cal.timeInMillis)
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.background(
                            if (filterDate != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Pick Date",
                            tint = if (filterDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (filterDate != null) {
                        IconButton(onClick = { viewModel.setFilterDate(null) }) {
                            Icon(imageVector = Icons.Default.FilterListOff, contentDescription = "Clear date")
                        }
                    }
                }

                // Category chips scroll row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = filterCategory == "All",
                            onClick = { viewModel.setFilterCategory("All") },
                            label = { Text("All Categories") }
                        )
                    }

                    val allCats = incomeCategories + expenseCategories
                    items(allCats) { cat ->
                        FilterChip(
                            selected = filterCategory == cat,
                            onClick = { viewModel.setFilterCategory(cat) },
                            label = { Text(Localization.getString(cat.lowercase(), lang)) }
                        )
                    }
                }
            }

            // Transaction Ledgers
            if (combinedList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = Localization.getString("no_transactions", lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("tx_history_list"),
                    contentPadding = PaddingValues(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(combinedList) { tx ->
                        SwipeableTxRow(
                            tx = tx,
                            lang = lang,
                            onDelete = {
                                if (tx.isIncome) {
                                    viewModel.deleteIncome(tx.id)
                                } else {
                                    viewModel.deleteExpense(tx.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Action FAB to trigger Add dialogue
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    dialogIsIncome = true
                    formCategory = "Salary"
                    formAmount = ""
                    formNote = ""
                    formSelectedDate = System.currentTimeMillis()
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.testTag("add_income_fab"),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Income")
            }

            FloatingActionButton(
                onClick = {
                    dialogIsIncome = false
                    formCategory = "Food"
                    formAmount = ""
                    formNote = ""
                    formSelectedDate = System.currentTimeMillis()
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier.testTag("add_expense_fab"),
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Add Expense")
            }
        }

        // Dialog for adding transaction
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = {
                    Text(
                        text = if (dialogIsIncome) Localization.getString("add_income", lang)
                        else Localization.getString("add_expense", lang),
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = formAmount,
                            onValueChange = { formAmount = it },
                            label = { Text(Localization.getString("amount", lang) + " (Rs.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_amount_input"),
                            singleLine = true
                        )

                        // Category Dropdown / Horizontal row
                        Text(
                            text = Localization.getString("category", lang),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        val cats = if (dialogIsIncome) incomeCategories else editExpenseCategories()
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(cats) { cat ->
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (formCategory == cat) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { formCategory = cat }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = Localization.getString(cat.lowercase(), lang),
                                        color = if (formCategory == cat) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = formNote,
                            onValueChange = { formNote = it },
                            label = { Text(Localization.getString("note", lang)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_note_input"),
                            singleLine = true
                        )

                        // Simple date picker trigger in dialog
                        Button(
                            onClick = {
                                val calendar = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val cal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                        }
                                        formSelectedDate = cal.timeInMillis
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sdf.format(Date(formSelectedDate)))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amt = formAmount.toDoubleOrNull() ?: 0.0
                            if (amt > 0.0 && formCategory.isNotBlank()) {
                                if (dialogIsIncome) {
                                    viewModel.addIncome(amt, formCategory, formNote, formSelectedDate)
                                } else {
                                    viewModel.addExpense(amt, formCategory, formNote, formSelectedDate)
                                }
                                showAddDialog = false
                            }
                        },
                        modifier = Modifier.testTag("form_submit_btn")
                    ) {
                        Text(Localization.getString("save", lang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(Localization.getString("cancel", lang))
                    }
                }
            )
        }
    }
}

// Swipeable rows or card list with Delete actions built directly in
@Composable
fun SwipeableTxRow(tx: CombinedTx, lang: String, onDelete: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (tx.isIncome) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (tx.isIncome) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = if (tx.isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = Localization.getString(tx.category.lowercase(), lang),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = dateFormat.format(Date(tx.date)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${if (tx.isIncome) "+" else "-"} Rs. ${String.format("%,.0f", tx.amount)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (tx.isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (tx.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notes,
                        contentDescription = "Note",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tx.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun editExpenseCategories() = listOf(
    "Food", "Transport", "Shopping", "Education", "Bills", "Healthcare", "Entertainment", "Rent", "Others"
)
