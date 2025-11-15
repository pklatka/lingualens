package com.lingualens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController

// TODO: Replace this with your actual ML Kit translation logic
private fun translateText(text: String, targetLanguage: String): String {
    return when (targetLanguage) {
        "Spanish" -> "$text (Spanish)"
        "French" -> "$text (French)"
        "German" -> "$text (German)"
        else -> text
    }
}

// TODO: Replace this with your real DetectedObject from ML Kit
data class SimulatedDetection(
    val id: Int,
    val label: String,
    val boundingBox: Rect // Use Compose Rect
)

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasCameraPermission = isGranted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedLanguage by remember { mutableStateOf("Spanish") }

    Scaffold(
        bottomBar = {
            LanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageChange = { selectedLanguage = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasCameraPermission) {
                // TODO: 1. Replace this Box with your CameraX Preview
                // Use androidx.camera.compose.PreviewView
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera Feed Would Be Here", color = Color.White)
                }

                // TODO: 2. This is simulated data.
                // Replace this with the live output from your ML Kit ObjectDetector
                val simulatedDetections = listOf(
                    SimulatedDetection(
                        id = 1,
                        label = "Laptop",
                        boundingBox = Rect(
                            Offset(100f, 300f),
                            Size(400f, 250f)
                        )
                    ),
                    SimulatedDetection(
                        id = 2,
                        label = "Mug",
                        boundingBox = Rect(
                            Offset(50f, 600f),
                            Size(200f, 150f)
                        )
                    )
                )

                // TODO: 3. This Canvas will draw overlays
                // Pass the real-time detection list here
                DetectionOverlay(
                    detections = simulatedDetections,
                    targetLanguage = selectedLanguage,
                    onBoxClicked = { original, translated ->
                        navController.navigate(
                            Screen.Save.createRoute(original, translated)
                        )
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission is required.")
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionOverlay(
    detections: List<SimulatedDetection>,
    targetLanguage: String,
    onBoxClicked: (originalLabel: String, translatedLabel: String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        detections.forEach { detection ->
            val translatedLabel = translateText(detection.label, targetLanguage)

            // A Box for each detection, containing the border and the label
            Column(
                modifier = Modifier
                    .offset(
                        x = detection.boundingBox.left.dp,
                        y = detection.boundingBox.top.dp
                    )
                    .size(
                        width = detection.boundingBox.size.width.dp,
                        height = detection.boundingBox.size.height.dp
                    )
                    .border(2.dp, Color.Yellow) // Draw the border
                    .clickable {
                        onBoxClicked(detection.label, translatedLabel)
                    }
            ) {
                // Label with a semi-transparent background at the top of the box
                Text(
                    text = translatedLabel,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}


@Composable
private fun LanguageSelector(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val languages = listOf("Spanish", "French", "German")

    BottomAppBar(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Translate to:", modifier = Modifier.padding(end = 8.dp))
            Box {
                Button(onClick = { showMenu = true }) {
                    Text(selectedLanguage)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang) },
                            onClick = {
                                onLanguageChange(lang)
                                showMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.Translate, contentDescription = "Translate")
        }
    }
}