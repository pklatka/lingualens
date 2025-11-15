package com.lingualens.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.lingualens.ui.theme.Black
import com.lingualens.ui.theme.BrandCyan
import com.lingualens.utils.ObjectDetectionAnalyzer
import com.lingualens.utils.TranslationManager
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

data class DetectionData(
    val id: Int,
    val label: String,
    val translatedLabel: String,
    val boundingBox: androidx.compose.ui.geometry.Rect,
    val confidence: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var selectedLanguage by remember { mutableStateOf("English") }
    var detections by remember { mutableStateOf<List<DetectionData>>(emptyList()) }
    var isModelLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val translationManager = remember { TranslationManager() }
    val analyzer = remember {
        ObjectDetectionAnalyzer(
            onObjectsDetected = { objects ->
                scope.launch {
                    val newDetections = objects.mapIndexed { index, obj ->
                        val label = obj.labels.firstOrNull()?.text ?: "Unknown"
                        val translated = translationManager.translate(label)

                        DetectionData(
                            id = index,
                            label = label,
                            translatedLabel = translated,
                            boundingBox = obj.boundingBox.toComposeRect(),
                            confidence = obj.labels.firstOrNull()?.confidence ?: 0f
                        )
                    }
                    detections = newDetections
                }
            },
            onError = { e ->
                println("Detection error: ${e.message}")
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            translationManager.close()
        }
    }

    LaunchedEffect(selectedLanguage) {
        isModelLoading = true
        val languageCode = TranslationManager.Companion.getLanguageCode(selectedLanguage)
        translationManager.downloadModel(languageCode) { message ->
            loadingMessage = message
        }
        isModelLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LinguaLens") },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(_root_ide_package_.com.lingualens.Screen.Favorites.route)
                    }) {
                        Icon(Icons.Default.Favorite, "Favorites")
                    }
                }
            )
        },
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
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { view ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val provider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = view.surfaceProvider
                                }

                                val imageCaptureBuilder = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .setTargetRotation(view.display.rotation)
                                imageCapture = imageCaptureBuilder.build()

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer) }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageCapture,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    println("Camera binding failed: ${e.message}")
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                DetectionOverlay(
                    detections = detections,
                    onBoxClicked = { detection ->
                        imageCapture?.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = imageProxyToBitmap(image)
                                    // Rotate bitmap based on image rotation
                                    val rotatedBitmap = rotateBitmap(bitmap, image.imageInfo.rotationDegrees.toFloat())
                                    image.close()

                                    // Store bitmap globally (not ideal but simplest for now)
                                    _root_ide_package_.com.lingualens.capturedBitmap = rotatedBitmap

                                    navController.navigate(
                                        _root_ide_package_.com.lingualens.Screen.Save.createRoute(
                                            detection.label,
                                            detection.translatedLabel
                                        )
                                    )
                                }
                            }
                        )
                    }
                )

                if (isModelLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(loadingMessage)
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Camera permission required")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
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
    detections: List<DetectionData>,
    onBoxClicked: (DetectionData) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        detections.forEach { detection ->
            Column(
                modifier = Modifier
                    .offset(
                        x = detection.boundingBox.left.dp,
                        y = detection.boundingBox.top.dp
                    )
                    .size(
                        width = detection.boundingBox.width.dp,
                        height = detection.boundingBox.height.dp
                    )
                    .border(
                        width = 3.dp,
                        color = BrandCyan,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .clickable { onBoxClicked(detection) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            BrandCyan.copy(alpha = 0.9f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Show both native and translated
                    if (detection.label != detection.translatedLabel) {
                        Text(
                            text = detection.label,
                            color = Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = detection.translatedLabel,
                            color = Black.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        // If same (English selected), just show once
                        Text(
                            text = detection.label,
                            color = Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${(detection.confidence * 100).toInt()}%",
                        color = Black.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
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
    val languages = listOf("English", "Spanish", "French", "German", "Italian", "Portuguese", "Polish", "Dutch", "Japanese", "Chinese", "Korean", "Russian", "Arabic")

    BottomAppBar {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Translate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Translate to:")
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                Button(onClick = { showMenu = true }) {
                    Text(selectedLanguage)
                    Icon(Icons.Default.ArrowDropDown, null)
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
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap

    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

fun android.graphics.Rect.toComposeRect(scaleX: Float = 1f, scaleY: Float = 1f): androidx.compose.ui.geometry.Rect {
    return androidx.compose.ui.geometry.Rect(
        androidx.compose.ui.geometry.Offset(left * scaleX, top * scaleY),
        androidx.compose.ui.geometry.Size(width() * scaleX, height() * scaleY)
    )
}