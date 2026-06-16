package com.example.data.repository

import android.content.Context
import androidx.room.Room
import com.example.data.db.AppDatabase
import com.example.data.db.FinanceDao
import com.example.data.model.BudgetLimit
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.NotificationAlert
import com.example.data.model.SavingsGoal
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class FinanceRepository(private val context: Context) {

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "smart_budget_tracking.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val financeDao: FinanceDao by lazy { database.financeDao() }

    // Exposure of direct flows
    val userProfile: Flow<UserProfile?> = financeDao.getUserProfileFlow()
    val incomes: Flow<List<Income>> = financeDao.getAllIncomesFlow()
    val expenses: Flow<List<Expense>> = financeDao.getAllExpensesFlow()
    val goals: Flow<List<SavingsGoal>> = financeDao.getAllGoalsFlow()
    val budgetLimits: Flow<List<BudgetLimit>> = financeDao.getAllBudgetLimitsFlow()
    val notifications: Flow<List<NotificationAlert>> = financeDao.getAllNotificationsFlow()

    // Ensure Profile exists
    suspend fun ensureProfileExists() {
        val existing = financeDao.getUserProfileDirect()
        if (existing == null) {
            val defaultProfile = UserProfile()
            financeDao.insertUserProfile(defaultProfile)
            
            // Insert initial default limits
            financeDao.insertBudgetLimit(BudgetLimit("Food", 20000.0))
            financeDao.insertBudgetLimit(BudgetLimit("Transport", 10000.0))
            financeDao.insertBudgetLimit(BudgetLimit("Shopping", 15000.0))
            financeDao.insertBudgetLimit(BudgetLimit("Education", 30000.0))
            financeDao.insertBudgetLimit(BudgetLimit("Bills", 15000.0))

            // Insert initial system notification
            insertNotification(
                title = "Welcome to Smart Budget Tracker",
                message = "Achieve your savings goals and manage daily budget with ease. Standard translation in English, Tamil & Sinhala."
            )
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        financeDao.insertUserProfile(profile)
    }

    // --- Incomes ---
    suspend fun insertIncome(income: Income) {
        financeDao.insertIncome(income)

        // Rule 1: Salary Detection & Auto Savings Rule
        // If the income category is "Salary", trigger 10% auto-transfer to Savings Goal if any goals exist!
        if (income.category.lowercase() == "salary") {
            val activeGoals = financeDao.getAllGoalsFlow().firstOrNull() ?: emptyList()
            val tenPercent = income.amount * 0.10
            
            if (activeGoals.isNotEmpty()) {
                // Transfer 10% to the first goal
                val targetGoal = activeGoals.first()
                val updatedSaved = targetGoal.savedAmount + tenPercent
                financeDao.updateGoal(targetGoal.copy(savedAmount = updatedSaved))

                // Insert Auto-Saved Notification message
                val percentageStr = String.format("%.0f%%", (updatedSaved / targetGoal.targetAmount) * 100.0)
                val remainingStr = String.format("%.0f", (targetGoal.targetAmount - updatedSaved).coerceAtLeast(0.0))
                
                insertNotification(
                    title = "Salary Received & Saved!",
                    message = "Simulated bank credit detected! 10% (Rs. ${String.format("%.2f", tenPercent)}) of your salary automatically transferred to Goal: ${targetGoal.name}. Goal progress updated to $percentageStr. Rs. $remainingStr remaining."
                )
            } else {
                insertNotification(
                    title = "Salary Received!",
                    message = "Simulated bank credit of Rs. ${String.format("%.2f", income.amount)} detected! Set up a Savings Goal to auto-save 10% next time."
                )
            }
        }
    }

    suspend fun deleteIncome(id: Int) {
        financeDao.deleteIncomeById(id)
    }

    // --- Expenses ---
    suspend fun insertExpense(expense: Expense) {
        financeDao.insertExpense(expense)

        // Check if category budget is exceeded or > 90%
        val limits = financeDao.getAllBudgetLimitsFlow().first()
        val catLimit = limits.find { it.category.equals(expense.category, ignoreCase = true) }
        
        if (catLimit != null) {
            val currentMonthExpenses = getExpensesForMonthAndCategory(expense.category)
            val totalExpenseInCat = currentMonthExpenses.sumOf { it.amount }
            
            if (totalExpenseInCat >= catLimit.monthlyLimit) {
                insertNotification(
                    title = "Budget Exceeded Alert 🚨",
                    message = "Your actual expenses for ${expense.category} (Rs. ${String.format("%.0f", totalExpenseInCat)}) have exceeded the configured monthly budget of Rs. ${String.format("%.0f", catLimit.monthlyLimit)}!"
                )
            } else if (totalExpenseInCat >= catLimit.monthlyLimit * 0.90) {
                insertNotification(
                    title = "Budget Exceeded Warning 🔔",
                    message = "Your ${expense.category} budget of Rs. ${String.format("%.0f", catLimit.monthlyLimit)} has exceeded 90%. Current spend: Rs. ${String.format("%.0f", totalExpenseInCat)}."
                )
            }
        }
    }

    suspend fun deleteExpense(id: Int) {
        financeDao.deleteExpenseById(id)
    }

    private suspend fun getExpensesForMonthAndCategory(category: String): List<Expense> {
        val allExp = financeDao.getAllExpensesFlow().first()
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        
        return allExp.filter {
            val cellCal = Calendar.getInstance().apply { timeInMillis = it.date }
            cellCal.get(Calendar.MONTH) == currentMonth &&
            cellCal.get(Calendar.YEAR) == currentYear &&
            it.category.equals(category, ignoreCase = true)
        }
    }

    // --- Savings Goals ---
    suspend fun insertGoal(goal: SavingsGoal) {
        financeDao.insertGoal(goal)
    }

    suspend fun updateGoal(goal: SavingsGoal) {
        financeDao.updateGoal(goal)
    }

    suspend fun deleteGoal(id: Int) {
        financeDao.deleteGoalById(id)
    }

    // --- Budget Limits ---
    suspend fun insertLimit(limit: BudgetLimit) {
        financeDao.insertBudgetLimit(limit)
    }

    suspend fun deleteLimit(category: String) {
        financeDao.deleteBudgetLimit(category)
    }

    // --- Notifications ---
    suspend fun insertNotification(title: String, message: String) {
        val notif = NotificationAlert(
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        financeDao.insertNotification(notif)
    }

    suspend fun markNotificationAsRead(id: Int) {
        financeDao.markNotificationAsRead(id)
    }

    suspend fun clearAllNotifications() {
        financeDao.clearAllNotifications()
    }
}
