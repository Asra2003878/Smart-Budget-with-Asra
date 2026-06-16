package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import com.example.data.model.BudgetLimit
import com.example.data.model.SavingsGoal
import com.example.ui.theme.Localization
import com.example.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetGoalScreen(
    viewModel: FinanceViewModel
) {
    val context = LocalContext.current
    val profile by viewModel.userProfile.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val budgetLimits by viewModel.budgetLimits.collectAsState()

    val lang = profile?.selectedLanguageCode ?: "en"

    // Dialog sheets
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showAddLimitDialog by remember { mutableStateOf(false) }

    // Goal creation fields
    var goalName by remember { mutableStateOf("") }
    var goalTargetAmt by remember { mutableStateOf("") }
    var goalInitialSaved by remember { mutableStateOf("") }
    var goalTargetDate by remember { mutableStateOf(System.currentTimeMillis() + 31536000000L) }

    // Budget Limit creation fields
    var limitCategory by remember { mutableStateOf("Food") }
    var limitAmount by remember { mutableStateOf("") }

    val expenseCategories = listOf("Food", "Transport", "Shopping", "Education", "Bills", "Healthcare", "Entertainment", "Rent", "Others")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("budget_goal_feed"),
        contentPadding = PaddingValues(bottom = 90.dp, start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // --- SECTION 1: MONTHLY BUDGET PLANNING ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = Localization.getString("budget_limits", lang),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Monthly expenditure caps & warnings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Button(
                    onClick = {
                        limitCategory = "Food"
                        limitAmount = ""
                        showAddLimitDialog = true
                    },
                    modifier = Modifier.testTag("configure_budget_btn"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit limits", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Configure", fontSize = 12.sp)
                }
            }
        }

        if (budgetLimits.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No category budget limits configured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(budgetLimits) { limit ->
                // Compute actual month expenditures in this category
                val currentMonthExpenses = expenses.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.date }
                    val currentM = Calendar.getInstance().get(Calendar.MONTH)
                    val currentY = Calendar.getInstance().get(Calendar.YEAR)
                    cal.get(Calendar.MONTH) == currentM && cal.get(Calendar.YEAR) == currentY &&
                    it.category.equals(limit.category, ignoreCase = true)
                }
                val spent = currentMonthExpenses.sumOf { it.amount }
                val progress = if (limit.monthlyLimit > 0) spent / limit.monthlyLimit else 0.0

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
                            Text(
                                text = Localization.getString(limit.category.lowercase(), lang),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            // Show alert warnings dynamically
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (progress >= 1.0) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Exceeded limit",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exceeded !", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else if (progress >= 0.9) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Near limit",
                                        tint = Color(0xFFFFA500),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(">90% Alert", color = Color(0xFFFFA500), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Healthy", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress linear gauge
                        LinearProgressIndicator(
                            progress = { progress.toFloat().coerceAtMost(1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = if (progress >= 1.0) MaterialTheme.colorScheme.error else if (progress >= 0.9) Color(0xFFFFA500) else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Spent: Rs. ${String.format("%,.0f", spent)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            Text(
                                text = "Budget: Rs. ${String.format("%,.0f", limit.monthlyLimit)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }


        // --- SECTION 2: SAVINGS GOAL MODULE ---
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = Localization.getString("savings_goals", lang),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Track target milestones & completion predictions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Button(
                    onClick = {
                        goalName = ""
                        goalTargetAmt = ""
                        goalInitialSaved = ""
                        goalTargetDate = System.currentTimeMillis() + 31536000000L
                        showAddGoalDialog = true
                    },
                    modifier = Modifier.testTag("add_goal_btn"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Goal", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Localization.getString("add_goal", lang), fontSize = 11.sp)
                }
            }
        }

        if (goals.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No savings goal configured yet. Set one to enable 10% auto-saving!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            items(goals) { goal ->
                val progressFraction = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount) else 0.0
                val remainingAmount = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)

                // Achievement Prediction Calculation as requested by user
                // If there's an ongoing regular transfer (mocking 10% auto-saves of Rs. 100,000 salary)
                val mockMonthlySaving = 10000.0 // 10% of Rs. 100,000 monthly salary
                val monthsRequired = if (mockMonthlySaving > 0) remainingAmount / mockMonthlySaving else 1.0

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = goal.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Deposit quick action button
                            Button(
                                onClick = { viewModel.saveToGoal(goal, 5000.0) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("+ Rs. 5,000", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress gauge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { progressFraction.toFloat().coerceAtMost(1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${String.format("%.0f", progressFraction * 100.0)}%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saved: Rs. ${String.format("%,.0f", goal.savedAmount)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            Text(
                                text = "Target: Rs. ${String.format("%,.0f", goal.targetAmount)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Smart Prediction Statement (Highlight Feature)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = "Achievement Prediction Indicator",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (remainingAmount <= 0.0) {
                                    "🎉 Goal Fully Achieved!"
                                } else {
                                    "AI Prediction: You will achieve this in ~${String.format("%.1f", monthsRequired)} months (based on Rs. 10k/mo savings)."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }


        // SECTION 3: SYSTEM AUTO SAVINGS GUIDE DETAILS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsSuggest,
                        contentDescription = "Simulation Details",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Auto Savings Automation Rule",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "When simulated banking credit deposits Salary, 10% is immediately separated and injected into your prioritized active goal savings ledger automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }


    // ADD GOAL MODAL DIALOG
    if (showAddGoalDialog) {
        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("Create Savings Goal 🎯") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        label = { Text(Localization.getString("goal_name", lang)) },
                        modifier = Modifier.fillMaxWidth().testTag("goal_name_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = goalTargetAmt,
                        onValueChange = { goalTargetAmt = it },
                        label = { Text(Localization.getString("target_amount", lang) + " (Rs.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("goal_target_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = goalInitialSaved,
                        onValueChange = { goalInitialSaved = it },
                        label = { Text("Initial Saved Amount (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("goal_saved_input"),
                        singleLine = true
                    )

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
                                    goalTargetDate = cal.timeInMillis
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
                        Text(sdf.format(Date(goalTargetDate)))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = goalTargetAmt.toDoubleOrNull() ?: 0.0
                        val initial = goalInitialSaved.toDoubleOrNull() ?: 0.0
                        if (goalName.isNotBlank() && target > 0.0) {
                            viewModel.addGoal(goalName, target, initial, goalTargetDate)
                            showAddGoalDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_goal_btn")
                ) {
                    Text(Localization.getString("save", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGoalDialog = false }) {
                    Text(Localization.getString("cancel", lang))
                }
            }
        )
    }

    // CONFIGURE BUDGET LIMIT MODAL DIALOG
    if (showAddLimitDialog) {
        AlertDialog(
            onDismissRequest = { showAddLimitDialog = false },
            title = { Text("Configure Category Monthly Limit 🛠️") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select Category",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Categorized row chooser
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(expenseCategories) { cat ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (limitCategory == cat) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { limitCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = Localization.getString(cat.lowercase(), lang),
                                    color = if (limitCategory == cat) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = limitAmount,
                        onValueChange = { limitAmount = it },
                        label = { Text("Monthly Expenditure Cap Limit (Rs.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("limit_amount_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val limitAmt = limitAmount.toDoubleOrNull() ?: 0.0
                        if (limitAmt > 0.0 && limitCategory.isNotBlank()) {
                            viewModel.updateBudgetLimit(limitCategory, limitAmt)
                            showAddLimitDialog = false
                        }
                    },
                    modifier = Modifier.testTag("submit_limit_btn")
                ) {
                    Text(Localization.getString("save", lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLimitDialog = false }) {
                    Text(Localization.getString("cancel", lang))
                }
            }
        )
    }
}
