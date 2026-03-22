package com.example.latencycheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.latencycheck.ui.MainScreen
import com.example.latencycheck.ui.SettingsScreen
import com.example.latencycheck.ui.HistoryScreen
import com.example.latencycheck.ui.MapScreen
import com.example.latencycheck.ui.ColorSettingsScreen
import com.example.latencycheck.ui.SummaryScreen
import com.example.latencycheck.ui.DebugScreen
import com.example.latencycheck.ui.theme.LatencycheckTheme
import com.example.latencycheck.viewmodel.MainViewModel
import com.example.latencycheck.service.NetworkInfoHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var networkInfoHelper: NetworkInfoHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LatencycheckTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(networkInfoHelper = networkInfoHelper)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(networkInfoHelper: NetworkInfoHelper) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToSummary = { navController.navigate("summary") },
                onNavigateToMap = { navController.navigate("map") },
                onNavigateToDebug = { navController.navigate("debug") }
            )
        }
        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("map") {
            MapScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("summary") {
            SummaryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateToColorSettings = { navController.navigate("color_settings") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("color_settings") {
            ColorSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("debug") {
            DebugScreen(
                networkInfoHelper = networkInfoHelper,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
