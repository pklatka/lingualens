package com.lingualens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lingualens.ui.theme.LinguaLensTheme
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Save : Screen("save_screen/{originalLabel}/{translatedLabel}") {
        fun createRoute(originalLabel: String, translatedLabel: String): String {
            // URL-encode arguments to handle special characters
            val encodedOriginal = URLEncoder.encode(originalLabel, "UTF-8")
            val encodedTranslated = URLEncoder.encode(translatedLabel, "UTF-8")
            return "save_screen/$encodedOriginal/$encodedTranslated"
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinguaLensTheme {
                LinguaLensApp()
            }
        }
    }
}

@Composable
fun LinguaLensApp() {
    val navController = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Camera.route,
            modifier = Modifier.fillMaxSize()
        ) {
            // Camera Screen Route
            composable(Screen.Camera.route) {
                CameraScreen(navController = navController)
            }

            // Save Screen Route
            composable(
                route = Screen.Save.route,
                arguments = listOf(
                    navArgument("originalLabel") { type = NavType.StringType },
                    navArgument("translatedLabel") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                // Decode arguments
                val originalLabel = URLDecoder.decode(
                    backStackEntry.arguments?.getString("originalLabel") ?: "", "UTF-8"
                )
                val translatedLabel = URLDecoder.decode(
                    backStackEntry.arguments?.getString("translatedLabel") ?: "", "UTF-8"
                )

                SaveScreen(
                    navController = navController,
                    originalLabel = originalLabel,
                    translatedLabel = translatedLabel
                )
            }
        }
    }
}