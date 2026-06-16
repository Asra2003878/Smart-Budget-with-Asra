package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.*
import com.example.ui.theme.Localization
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FinanceViewModel
import com.example.ui.viewmodel.FinanceViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: FinanceViewModel = ViewModelProvider(
                this,
                FinanceViewModelFactory(application)
            )[FinanceViewModel::class.java]

            val profile by viewModel.userProfile.collectAsState()
            val isLocked by viewModel.isAppLocked.collectAsState()

            val darkTheme = profile?.isDarkMode ?: isSystemInDarkTheme()
            val lang = profile?.selectedLanguageCode ?: "en"

            MyApplicationTheme(darkTheme = darkTheme, dynamicColor = false) {
                if (isLocked && profile?.isPinEnabled == true && !profile?.pin.isNullOrBlank()) {
                    AuthScreen(
                        viewModel = viewModel,
                        onSuccess = { /* Decoupled automatic state listener will trigger unlock */ }
                    )
                } else {
                    MainScreenContent(viewModel = viewModel, lang = lang)
                }
            }
        }
    }
}

sealed class ScreenNav(val labelKey: String, val icon: ImageVector) {
    object Home : ScreenNav("dashboard", Icons.Default.Dashboard)
    object Transactions : ScreenNav("transactions", Icons.Default.ReceiptLong)
    object BudgetAndGoals : ScreenNav("budget", Icons.Default.TrendingUp)
    object Reports : ScreenNav("reports", Icons.Default.Assessment)
    object Settings : ScreenNav("settings", Icons.Default.Settings)
}

@Composable
fun MainScreenContent(
    viewModel: FinanceViewModel,
    lang: String
) {
    var activeTab by remember { mutableStateOf<ScreenNav>(ScreenNav.Home) }

    val navigationItems = listOf(
        ScreenNav.Home,
        ScreenNav.Transactions,
        ScreenNav.BudgetAndGoals,
        ScreenNav.Reports,
        ScreenNav.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("app_navigation_bar"),
                tonalElevation = 8.dp
            ) {
                navigationItems.forEach { item ->
                    val isSelected = activeTab == item
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = item },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = Localization.getString(item.labelKey, lang)
                            )
                        },
                        label = {
                            Text(
                                text = Localization.getString(item.labelKey, lang),
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_${item.labelKey}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                ScreenNav.Home -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToTransactions = { activeTab = ScreenNav.Transactions },
                    onNavigateToGoals = { activeTab = ScreenNav.BudgetAndGoals }
                )
                ScreenNav.Transactions -> TransactionScreen(
                    viewModel = viewModel
                )
                ScreenNav.BudgetAndGoals -> BudgetGoalScreen(
                    viewModel = viewModel
                )
                ScreenNav.Reports -> ReportsScreen(
                    viewModel = viewModel
                )
                ScreenNav.Settings -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
