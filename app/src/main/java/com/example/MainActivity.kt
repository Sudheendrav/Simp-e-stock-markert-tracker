package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.SettingsRepository
import com.example.data.StockDatabase
import com.example.data.StockRepository
import com.example.ui.StockTrackerViewModel
import com.example.ui.ViewModelFactory
import com.example.ui.screens.GroupDetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.StockDetailScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Initialize Local Storage & Repositories
        val database = StockDatabase.getDatabase(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)
        val stockRepository = StockRepository(database.stockDao(), settingsRepository)

        // Spin off active real-time ticker mock fluctuations
        stockRepository.startSimulation(lifecycleScope, applicationContext)

        // Instantiate core ViewModel
        val factory = ViewModelFactory(stockRepository, settingsRepository)
        val viewModel = ViewModelProvider(this, factory)[StockTrackerViewModel::class.java]

        setContent {
            // Observe the user's selected Dark Mode preference
            val isDarkModeSettings by viewModel.isDarkMode.collectAsState()
            val darkTheme = isDarkModeSettings ?: androidx.compose.foundation.isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        // Home Watchlists & Portfolio hub
                        composable("home") {
                            HomeScreen(
                                viewModel = viewModel,
                                onNavigateToStockDetail = { symbol ->
                                    navController.navigate("stock_detail/$symbol")
                                },
                                onNavigateToGroupDetail = { id, _ ->
                                    navController.navigate("group_detail/$id")
                                }
                            )
                        }

                        // Indepth stock details & custom rules dashboard
                        composable(
                            route = "stock_detail/{symbol}",
                            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val symbol = backStackEntry.arguments?.getString("symbol") ?: ""
                            StockDetailScreen(
                                symbol = symbol,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToStockDetail = { suggestedSymbol ->
                                    navController.navigate("stock_detail/$suggestedSymbol") {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        // Specific group items list & alerts configuring
                        composable(
                            route = "group_detail/{groupId}",
                            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val groupId = backStackEntry.arguments?.getInt("groupId") ?: 0
                            val groupsList by viewModel.groups.collectAsState()
                            val groupName = groupsList.find { it.id == groupId }?.name ?: "Watchlist"

                            GroupDetailScreen(
                                groupId = groupId,
                                groupName = groupName,
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToStockDetail = { symbol ->
                                    navController.navigate("stock_detail/$symbol")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
