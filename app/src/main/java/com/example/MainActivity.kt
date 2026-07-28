package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.FloatingBubbleOverlay
import com.example.ui.screens.AddSecretDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SavedKeysScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.TwoFAGenerateTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialAction = intent?.getStringExtra("LAUNCH_ACTION")

        setContent {
            val isDarkMode by mainViewModel.isDarkMode.collectAsState()

            TwoFAGenerateTheme(darkTheme = isDarkMode) {
                MainAppNavHost(
                    viewModel = mainViewModel,
                    initialAction = initialAction
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("LAUNCH_ACTION")?.let { action ->
            if (action == "ADD_KEY") {
                mainViewModel.openAddDialogRequested()
            }
        }
    }
}

@Composable
fun MainAppNavHost(
    viewModel: MainViewModel,
    initialAction: String? = null
) {
    val navController = rememberNavController()
    var showAddSecretDialog by remember { mutableStateOf(initialAction == "ADD_KEY") }

    val externalAddRequested by viewModel.externalAddRequested.collectAsState()

    LaunchedEffect(externalAddRequested) {
        if (externalAddRequested) {
            showAddSecretDialog = true
            viewModel.consumeExternalAddRequest()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = "splash",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("splash") {
                    SplashScreen(
                        onTimeout = {
                            if (viewModel.prefs.isFirstLaunch) {
                                viewModel.prefs.isFirstLaunch = false
                                navController.navigate("welcome") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            } else {
                                navController.navigate("home") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        }
                    )
                }

                composable("welcome") {
                    WelcomeScreen(
                        onGetStarted = {
                            navController.navigate("home") {
                                popUpTo("welcome") { inclusive = true }
                            }
                        }
                    )
                }

                composable("auth") {
                    AuthScreen(
                        viewModel = viewModel,
                        onAuthSuccess = {
                            navController.navigate("home") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                    )
                }

                composable("home") {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateSavedKeys = { navController.navigate("saved_keys") },
                        onNavigateSettings = { navController.navigate("settings") },
                        onOpenAddSecret = { showAddSecretDialog = true }
                    )
                }

                composable("saved_keys") {
                    SavedKeysScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenAddSecret = { showAddSecretDialog = true }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onLogout = {
                            navController.navigate("auth") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }

            // Floating Bubble Overlay Menu (Active inside app when 2FA ON is enabled)
            FloatingBubbleOverlay(
                viewModel = viewModel,
                onAddClicked = { showAddSecretDialog = true }
            )

            // Add Secret Dialog Modal
            if (showAddSecretDialog) {
                AddSecretDialog(
                    viewModel = viewModel,
                    onDismiss = { showAddSecretDialog = false }
                )
            }
        }
    }
}

