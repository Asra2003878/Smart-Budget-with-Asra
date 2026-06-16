package com.example.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.BudgetLimit
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.NotificationAlert
import com.example.data.model.SavingsGoal
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    // --- User Profile ---
    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileDirect(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Update
    suspend fun updateUserProfile(profile: UserProfile)


    // --- Incomes ---
    @Query("SELECT * FROM incomes ORDER BY date DESC")
    fun getAllIncomesFlow(): Flow<List<Income>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncome(income: Income)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteIncomeById(id: Int)


    // --- Expenses ---
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpensesFlow(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Int)


    // --- Savings Goals ---
    @Query("SELECT * FROM savings_goals ORDER BY id DESC")
    fun getAllGoalsFlow(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: Int): SavingsGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingsGoal)

    @Update
    suspend fun updateGoal(goal: SavingsGoal)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoalById(id: Int)


    // --- Budget Limits ---
    @Query("SELECT * FROM budget_limits")
    fun getAllBudgetLimitsFlow(): Flow<List<BudgetLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetLimit(limit: BudgetLimit)

    @Query("DELETE FROM budget_limits WHERE category = :categoryName")
    suspend fun deleteBudgetLimit(categoryName: String)


    // --- Notifications ---
    @Query("SELECT * FROM notification_alerts ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationAlert)

    @Query("UPDATE notification_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)

    @Query("DELETE FROM notification_alerts WHERE id = :id")
    suspend fun deleteNotificationById(id: Int)

    @Query("DELETE FROM notification_alerts")
    suspend fun clearAllNotifications()
}

@Database(
    entities = [
        UserProfile::class,
        Income::class,
        Expense::class,
        SavingsGoal::class,
        BudgetLimit::class,
        NotificationAlert::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao
}
