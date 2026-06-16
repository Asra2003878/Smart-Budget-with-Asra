package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "Suresh Kumar",
    val email: String = "suresh@example.com",
    val pin: String = "1234",
    val isPinEnabled: Boolean = true,
    val isBiometricEnabled: Boolean = false,
    val currency: String = "LKR",
    val selectedLanguageCode: String = "en", // "en", "ta", "si"
    val isDarkMode: Boolean = false,
    val isBankConnected: Boolean = false,
    val bankName: String = "Mock Bank of Ceylon",
    val simulatedAccountBalance: Double = 100000.0
) : Serializable

@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val date: Long,
    val category: String, // Salary, Business, Freelancing, Gifts, Other
    val note: String
) : Serializable

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val date: Long,
    val category: String, // Food, Transport, Shopping, Education, Bills, Healthcare, Entertainment, Rent, Others
    val note: String
) : Serializable

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double,
    val targetDate: Long
) : Serializable

@Entity(tableName = "budget_limits")
data class BudgetLimit(
    @PrimaryKey val category: String, // Food, Transport, etc.
    val monthlyLimit: Double
) : Serializable

@Entity(tableName = "notification_alerts")
data class NotificationAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false
) : Serializable
