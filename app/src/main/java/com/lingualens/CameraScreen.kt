package com.lingualens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
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
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max

data class DetectionResult(
    val id: Int,
    val label: String,
    val translatedLabel: String,
    val boundingBox: Rect,
    val bitmap: Bitmap?
)

// Language mapping
data class LanguageOption(val displayName: String, val mlkitCode: String)

val availableLanguages = listOf(
    LanguageOption("Spanish", TranslateLanguage.SPANISH),
    LanguageOption("French", TranslateLanguage.FRENCH),
    LanguageOption("German", TranslateLanguage.GERMAN),
    LanguageOption("Italian", TranslateLanguage.ITALIAN),
    LanguageOption("Polish", TranslateLanguage.POLISH),
    LanguageOption("Portuguese", TranslateLanguage.PORTUGUESE),
    LanguageOption("Chinese", TranslateLanguage.CHINESE),
    LanguageOption("Japanese", TranslateLanguage.JAPANESE),
    LanguageOption("Korean", TranslateLanguage.KOREAN)
)

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    var selectedLanguage by remember { mutableStateOf(availableLanguages[0]) }
    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var isTranslatorReady by remember { mutableStateOf(false) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    // Object detector
    val objectDetector = remember {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    // Translator
    var translator by remember { mutableStateOf<Translator?>(null) }

    // Download and prepare translator when language changes
    LaunchedEffect(selectedLanguage) {
        isTranslatorReady = false
        translator?.close()

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(selectedLanguage.mlkitCode)
            .build()

        val newTranslator = Translation.getClient(options)
        translator = newTranslator

        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()

        newTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                isTranslatorReady = true
                Log.d("Translation", "Model downloaded for ${selectedLanguage.displayName}")
            }
            .addOnFailureListener { exception ->
                Log.e("Translation", "Model download failed", exception)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            objectDetector.close()
            translator?.close()
        }
    }

    Scaffold(
        bottomBar = {
            LanguageSelector(
                selectedLanguage = selectedLanguage.displayName,
                availableLanguages = availableLanguages.map { it.displayName },
                onLanguageChange = { languageName ->
                    selectedLanguage = availableLanguages.first { it.displayName == languageName }
                },
                isTranslatorReady = isTranslatorReady
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (hasCameraPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onPreviewViewCreated = { view ->
                        previewView = view
                    },
                    onImageCaptureCreated = { capture ->
                        imageCapture = capture
                    },
                    lifecycleOwner = lifecycleOwner,
                    objectDetector = objectDetector,
                    translator = translator,
                    isTranslatorReady = isTranslatorReady,
                    onDetectionsUpdate = { newDetections ->
                        detections = newDetections
                    }
                )

                DetectionOverlay(
                    detections = detections,
                    onBoxClicked = { detection ->
                        navController.navigate(
                            Screen.Save.createRoute(
                                detection.label,
                                detection.translatedLabel,
                                detection.bitmap
                            )
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
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onPreviewViewCreated: (PreviewView) -> Unit,
    onImageCaptureCreated: (ImageCapture) -> Unit,
    lifecycleOwner: LifecycleOwner,
    objectDetector: ObjectDetector,
    translator: Translator?,
    isTranslatorReady: Boolean,
    onDetectionsUpdate: (List<DetectionResult>) -> Unit
) {
    val context = LocalContext.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            onPreviewViewCreated(previewView)

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                onImageCaptureCreated(imageCapture)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(
                        imageProxy,
                        objectDetector,
                        translator,
                        isTranslatorReady,
                        onDetectionsUpdate
                    )
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

@androidx.camera.core.ExperimentalGetImage
private fun processImageProxy(
    imageProxy: ImageProxy,
    objectDetector: ObjectDetector,
    translator: Translator?,
    isTranslatorReady: Boolean,
    onDetectionsUpdate: (List<DetectionResult>) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        objectDetector.process(image)
            .addOnSuccessListener { detectedObjects ->
                if (detectedObjects.isNotEmpty() && isTranslatorReady && translator != null) {
                    processDetections(
                        detectedObjects,
                        imageProxy,
                        translator,
                        onDetectionsUpdate
                    )
                } else {
                    onDetectionsUpdate(emptyList())
                    imageProxy.close()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ObjectDetection", "Detection failed", e)
                onDetectionsUpdate(emptyList())
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@androidx.camera.core.ExperimentalGetImage
private fun processDetections(
    detectedObjects: List<DetectedObject>,
    imageProxy: ImageProxy,
    translator: Translator,
    onDetectionsUpdate: (List<DetectionResult>) -> Unit
) {
    val results = mutableListOf<DetectionResult>()
    var processedCount = 0

    // Convert ImageProxy to Bitmap for cropping
    val bitmap = imageProxy.toBitmap()
    val rotatedBitmap = rotateBitmap(bitmap, imageProxy.imageInfo.rotationDegrees)

    detectedObjects.forEachIndexed { index, obj ->
        val label = obj.labels.firstOrNull()?.text ?: "Object"

        // Get bounding box and scale it to screen coordinates
        val box = obj.boundingBox
        val scaledRect = Rect(
            left = box.left.toFloat(),
            top = box.top.toFloat(),
            right = box.right.toFloat(),
            bottom = box.bottom.toFloat()
        )

        // Crop the detected object from the image
        val croppedBitmap = cropBitmap(rotatedBitmap, box)

        translator.translate(label)
            .addOnSuccessListener { translatedText ->
                results.add(
                    DetectionResult(
                        id = index,
                        label = label,
                        translatedLabel = translatedText,
                        boundingBox = scaledRect,
                        bitmap = croppedBitmap
                    )
                )
                processedCount++

                if (processedCount == detectedObjects.size) {
                    onDetectionsUpdate(results)
                    imageProxy.close()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Translation", "Translation failed for: $label", e)
                results.add(
                    DetectionResult(
                        id = index,
                        label = label,
                        translatedLabel = label,
                        boundingBox = scaledRect,
                        bitmap = croppedBitmap
                    )
                )
                processedCount++

                if (processedCount == detectedObjects.size) {
                    onDetectionsUpdate(results)
                    imageProxy.close()
                }
            }
    }

    if (detectedObjects.isEmpty()) {
        imageProxy.close()
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap

    val matrix = Matrix()
    matrix.postRotate(rotationDegrees.toFloat())
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun cropBitmap(bitmap: Bitmap, box: android.graphics.Rect): Bitmap {
    val left = max(0, box.left)
    val top = max(0, box.top)
    val width = kotlin.math.min(box.width(), bitmap.width - left)
    val height = kotlin.math.min(box.height(), bitmap.height - top)

    return if (width > 0 && height > 0) {
        Bitmap.createBitmap(bitmap, left, top, width, height)
    } else {
        bitmap
    }
}

@Composable
private fun DetectionOverlay(
    detections: List<DetectionResult>,
    onBoxClicked: (DetectionResult) -> Unit
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
                    .border(3.dp, Color.Yellow)
                    .clickable {
                        onBoxClicked(detection)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = detection.label,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = detection.translatedLabel,
                        color = Color.Yellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: String,
    availableLanguages: List<String>,
    onLanguageChange: (String) -> Unit,
    isTranslatorReady: Boolean
) {
    var showMenu by remember { mutableStateOf(false) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedLanguage)
                        if (!isTranslatorReady) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    availableLanguages.forEach { lang ->
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