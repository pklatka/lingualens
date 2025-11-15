package com.lingualens

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lingualens.ui.theme.LinguaLensTheme
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Camera : Screen("camera")
    data object Favorites : Screen("favorites")

    data object Save : Screen("save_screen/{originalLabel}/{translatedLabel}") {
        fun createRoute(originalLabel: String, translatedLabel: String, bitmap: Bitmap? = null): String {
            val encodedOriginal = URLEncoder.encode(originalLabel, "UTF-8")
            val encodedTranslated = URLEncoder.encode(translatedLabel, "UTF-8")
            return "save_screen/$encodedOriginal/$encodedTranslated"
        }
    }
}

// Global variable to temporarily hold bitmap (not ideal but works for demo)
var capturedBitmap: Bitmap? = null

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
            composable(Screen.Camera.route) {
                CameraScreen(navController = navController)
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(navController = navController)
            }

            composable(
                route = Screen.Save.route,
                arguments = listOf(
                    navArgument("originalLabel") { type = NavType.StringType },
                    navArgument("translatedLabel") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val originalLabel = URLDecoder.decode(
                    backStackEntry.arguments?.getString("originalLabel") ?: "",
                    "UTF-8"
                )
                val translatedLabel = URLDecoder.decode(
                    backStackEntry.arguments?.getString("translatedLabel") ?: "",
                    "UTF-8"
                )

                SaveScreen(
                    navController = navController,
                    originalLabel = originalLabel,
                    translatedLabel = translatedLabel,
                    bitmap = capturedBitmap
                )
            }
        }
    }
}