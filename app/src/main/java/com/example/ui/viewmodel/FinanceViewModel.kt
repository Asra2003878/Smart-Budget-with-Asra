package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiApiClient
import com.example.data.model.BudgetLimit
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.NotificationAlert
import com.example.data.model.SavingsGoal
import com.example.data.model.UserProfile
import com.example.data.repository.FinanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FinanceRepository(application)

    // Direct Room Flows
    val userProfile: StateFlow<UserProfile?> = repository.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val incomes: StateFlow<List<Income>> = repository.incomes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val expenses: StateFlow<List<Expense>> = repository.expenses.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val goals: StateFlow<List<SavingsGoal>> = repository.goals.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val budgetLimits: StateFlow<List<BudgetLimit>> = repository.budgetLimits.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notifications: StateFlow<List<NotificationAlert>> = repository.notifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Dynamic UI State ---
    private val _isAppLocked = MutableStateFlow(true)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _aiInsights = MutableStateFlow("")
    val aiInsights: StateFlow<String> = _aiInsights.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Transaction search and filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterCategory = MutableStateFlow("All")
    val filterCategory: StateFlow<String> = _filterCategory.asStateFlow()

    private val _filterType = MutableStateFlow("All") // "All", "Income", "Expense"
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private val _filterDate = MutableStateFlow<Long?>(null) // null means no date filter
    val filterDate: StateFlow<Long?> = _filterDate.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureProfileExists()
            // If PIN security is disabled, auto unlock
            val profile = repository.userProfile.stateIn(viewModelScope).value
            if (profile != null && !profile.isPinEnabled) {
                _isAppLocked.value = false
            }
        }
    }

    // Unlock App
    fun attemptUnlock(enteredPin: String): Boolean {
        val currentProfile = userProfile.value ?: return false
        if (!currentProfile.isPinEnabled || currentProfile.pin == enteredPin) {
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun lockApp() {
        if (userProfile.value?.isPinEnabled == true) {
            _isAppLocked.value = true
        }
    }

    // --- Smart Budget Score Calculations (0 - 100) ---
    val budgetScoreMetrics: StateFlow<BudgetScore> = combine(
        incomes, expenses, budgetLimits, goals
    ) { inc, exp, lims, activeGoals ->
        val monthExpenses = filterCurrentMonthExpenses(exp)
        val monthIncomes = filterCurrentMonthIncomes(inc)

        val totalMonthIncome = monthIncomes.sumOf { it.amount }
        val totalMonthExpense = monthExpenses.sumOf { it.amount }

        // 1. Spending Control (30 marks) - decreases if expenses exceed 70% of incomes
        val expenseRatio = if (totalMonthIncome > 0) totalMonthExpense / totalMonthIncome else 0.5
        val spendingControlScore = when {
            totalMonthExpense == 0.0 -> 30
            expenseRatio <= 0.3 -> 30
            expenseRatio <= 0.5 -> 25
            expenseRatio <= 0.7 -> 20
            expenseRatio <= 0.9 -> 10
            else -> 5
        }

        // 2. Savings Habit (30 marks) - based on goals setup and active savings
        var savingsHabitScore = 10 // base mark for setting up
        if (activeGoals.isNotEmpty()) {
            savingsHabitScore += 10
            val averageProgress = activeGoals.map { it.savedAmount / it.targetAmount }.average()
            if (averageProgress > 0.5) {
                savingsHabitScore += 10
            } else if (averageProgress > 0.2) {
                savingsHabitScore += 5
            }
        }

        // 3. Budget Discipline (40 marks) - deduct 10 marks per category limit exceeded
        var budgetDisciplineScore = 40
        lims.forEach { limit ->
            val spentInCat = monthExpenses.filter { it.category.equals(limit.category, ignoreCase = true) }.sumOf { it.amount }
            if (spentInCat > limit.monthlyLimit) {
                budgetDisciplineScore = (budgetDisciplineScore - 10).coerceAtLeast(0)
            }
        }

        val finalScore = spendingControlScore + savingsHabitScore + budgetDisciplineScore

        BudgetScore(
            spendingControl = spendingControlScore,
            savingsHabit = savingsHabitScore,
            budgetDiscipline = budgetDisciplineScore,
            totalScore = finalScore
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetScore(25, 15, 35, 75)
    )

    // Helper functions for filtering current month
    private fun filterCurrentMonthExpenses(list: List<Expense>): List<Expense> {
        val calendar = Calendar.getInstance()
        val m = calendar.get(Calendar.MONTH)
        val y = calendar.get(Calendar.YEAR)
        return list.filter {
            val tc = Calendar.getInstance().apply { timeInMillis = it.date }
            tc.get(Calendar.MONTH) == m && tc.get(Calendar.YEAR) == y
        }
    }

    private fun filterCurrentMonthIncomes(list: List<Income>): List<Income> {
        val calendar = Calendar.getInstance()
        val m = calendar.get(Calendar.MONTH)
        val y = calendar.get(Calendar.YEAR)
        return list.filter {
            val tc = Calendar.getInstance().apply { timeInMillis = it.date }
            tc.get(Calendar.MONTH) == m && tc.get(Calendar.YEAR) == y
        }
    }

    // --- Profile Management ---
    fun updateLanguage(langCode: String) {
        viewModelScope.launch {
            val current = userProfile.value ?: return@launch
            repository.updateProfile(current.copy(selectedLanguageCode = langCode))
        }
    }

    fun updateTheme(isDark: Boolean) {
        viewModelScope.launch {
            val current = userProfile.value ?: return@launch
            repository.updateProfile(current.copy(isDarkMode = isDark))
        }
    }

    fun updatePINSecurity(enabled: Boolean, newPin: String) {
        viewModelScope.launch {
            val current = userProfile.value ?: return@launch
            repository.updateProfile(current.copy(isPinEnabled = enabled, pin = newPin))
        }
    }

    fun updateBiometricSecurity(enabled: Boolean) {
        viewModelScope.launch {
            val current = userProfile.value ?: return@launch
            repository.updateProfile(current.copy(isBiometricEnabled = enabled))
        }
    }

    // --- Bank Sim & Salary Auto-Save ---
    fun toggleMockBankConnection(connected: Boolean) {
        viewModelScope.launch {
            val current = userProfile.value ?: return@launch
            val updated = current.copy(
                isBankConnected = connected,
                simulatedAccountBalance = if (connected) 150000.0 else 0.0
            )
            repository.updateProfile(updated)
            if (connected) {
                repository.insertNotification(
                    title = "Mock Bank Connected 🏦",
                    message = "Your simulated Bank of Ceylon account has been securely linked! We will listen for Salary deposits to apply the 10% Auto Savings rule."
                )
            }
        }
    }

    fun triggerMockSalaryDeposit() {
        viewModelScope.launch {
            val current = userProfile.value ?: return@launch
            if (!current.isBankConnected) {
                repository.insertNotification(
                    title = "Bank not connected",
                    message = "Connect your mock bank account in Settings to receive simulated salary credit transactions."
                )
                return@launch
            }

            // Salary amount
            val salaryAmount = 100000.0
            
            // Add custom transaction
            val salaryIncome = Income(
                amount = salaryAmount,
                date = System.currentTimeMillis(),
                category = "Salary",
                note = "Simulated bank account salary deposit credit"
            )
            repository.insertIncome(salaryIncome)

            // Update bank balance
            repository.updateProfile(current.copy(simulatedAccountBalance = current.simulatedAccountBalance + salaryAmount))
        }
    }

    // --- Crud Actions ---
    fun addIncome(amount: Double, category: String, note: String, date: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.insertIncome(Income(amount = amount, category = category, note = note, date = date))
        }
    }

    fun deleteIncome(id: Int) {
        viewModelScope.launch {
            repository.deleteIncome(id)
        }
    }

    fun addExpense(amount: Double, category: String, note: String, date: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.insertExpense(Expense(amount = amount, category = category, note = note, date = date))
        }
    }

    fun deleteExpense(id: Int) {
        viewModelScope.launch {
            repository.deleteExpense(id)
        }
    }

    fun addGoal(name: String, targetAmount: Double, initialSaved: Double = 0.0, targetDate: Long = System.currentTimeMillis() + 31536000000L) {
        viewModelScope.launch {
            repository.insertGoal(SavingsGoal(name = name, targetAmount = targetAmount, savedAmount = initialSaved, targetDate = targetDate))
        }
    }

    fun saveToGoal(goal: SavingsGoal, progress: Double) {
        viewModelScope.launch {
            val updated = goal.copy(savedAmount = (goal.savedAmount + progress).coerceAtMost(goal.targetAmount))
            repository.updateGoal(updated)
            
            val remainingAmt = (updated.targetAmount - updated.savedAmount).coerceAtLeast(0.0)
            val percent = (updated.savedAmount / updated.targetAmount) * 100.0
            repository.insertNotification(
                title = "Saved towards Goal: ${goal.name}",
                message = "Progress updated to ${String.format("%.0f", percent)}%. Remaining saved amount to Goal was Rs. ${String.format("%.0f", remainingAmt)}."
            )
        }
    }

    fun deleteGoal(id: Int) {
        viewModelScope.launch {
            repository.deleteGoal(id)
        }
    }

    fun updateBudgetLimit(category: String, limit: Double) {
        viewModelScope.launch {
            repository.insertLimit(BudgetLimit(category, limit))
        }
    }

    // --- Gemini AI Analytical Generation ---
    fun runGeminiSpendingAnalysis() {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiInsights.value = ""

            val totalInc = incomes.value.sumOf { it.amount }
            val totalExp = expenses.value.sumOf { it.amount }
            val currentMonthList = filterCurrentMonthExpenses(expenses.value)
            
            val categorySumMap = currentMonthList.groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }

            val categoriesPrompt = categorySumMap.entries.joinToString(", ") { "${it.key}: Rs. ${it.value}" }
            
            // Structured elegant prompt
            val prompt = """
                You are a Smart Budgeting Financial AI Companion named "Smart Budget AI Officer".
                Formulate a brief, actionable financial report and friendly suggestions in 3 bullets:
                1. AI Spending Analysis (e.g. summarizing major category spends based on these user spends: $categoriesPrompt).
                2. Smart Saving Suggestions (e.g., suggesting practical monthly reductions specific to these items).
                3. Friendly encouragement based on their status (Total Monthly Income: Rs. $totalInc, Total Monthly Expense: Rs. $totalExp).
                Note: Keep your layout clean, structured, and short (max 4 lines of output). No developer jargon.
            """.trimIndent()

            val response = GeminiApiClient.getFinancialInsights(prompt)
            _aiInsights.value = response
            _isAiLoading.value = false
        }
    }

    // --- Cloud Backup simulation ---
    fun runMockCloudBackup() {
        viewModelScope.launch {
            repository.insertNotification(
                title = "Cloud Backup Complete 🔥",
                message = "All records synchronized securely with Simulated Firebase Cloud storage! 100% of local transaction ledgers uploaded."
            )
        }
    }

    fun runMockCloudRestore() {
        viewModelScope.launch {
            repository.insertNotification(
                title = "Cloud Restore Successful ✅",
                message = "Ledger databases synchronized. All budget goal profiles and historic transactions restored successfully from Google Cloud Storage."
            )
        }
    }

    // --- Share Financial Report (CSV Export) ---
    fun shareCSVReport(context: Context) {
        val allIncomes = incomes.value
        val allExpenses = expenses.value

        val csvBuilder = StringBuilder()
        csvBuilder.append("Type,Amount,Date,Category,Note\n")
        
        allIncomes.forEach {
            val calendar = Calendar.getInstance().apply { timeInMillis = it.date }
            val dateStr = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
            csvBuilder.append("Income,${it.amount},$dateStr,${it.category},${it.note.replace(",", " ")}\n")
        }

        allExpenses.forEach {
            val calendar = Calendar.getInstance().apply { timeInMillis = it.date }
            val dateStr = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
            csvBuilder.append("Expense,${it.amount},$dateStr,${it.category},${it.note.replace(",", " ")}\n")
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Smart Budget Financial Report")
                putExtra(Intent.EXTRA_TEXT, "Detailed CSV Export:\n\n$csvBuilder")
            }
            context.startActivity(Intent.createChooser(intent, "Share Transaction Report via"))
        } catch (e: Exception) {
            // Backup share
        }
    }

    // Search filters
    fun setQuery(q: String) { _searchQuery.value = q }
    fun setFilterCategory(c: String) { _filterCategory.value = c }
    fun setFilterType(t: String) { _filterType.value = t }
    fun setFilterDate(d: Long?) { _filterDate.value = d }

    // Read Notification
    fun readNotification(id: Int) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }
}

// Data holder for Smart Budget Score
data class BudgetScore(
    val spendingControl: Int,
    val savingsHabit: Int,
    val budgetDiscipline: Int,
    val totalScore: Int
)

class FinanceViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
