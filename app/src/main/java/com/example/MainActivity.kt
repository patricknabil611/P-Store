package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

enum class Screen {
    MAIN,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PStoreApp()
                }
            }
        }
    }
}

@Composable
fun PStoreApp(
    viewModel: MainViewModel = viewModel()
) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    when (currentScreen) {
        Screen.MAIN -> {
            MainScreen(
                viewModel = viewModel,
                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
            )
        }
        Screen.SETTINGS -> {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = Screen.MAIN }
            )
        }
    }
}
