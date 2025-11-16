package com.lingualens.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.lingualens.utils.ObjectDetectorHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.lingualens.Screen
import com.lingualens.ui.theme.Black
import com.lingualens.ui.theme.BrandCyan
import com.lingualens.utils.TranslationManager
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import com.lingualens.R
import kotlin.math.max

/**
 * Stores data for a single detected object, including the
 * original bounding box from MediaPipe.
 */
data class DetectionData(
    val id: Int,
    val label: String,
    val translatedLabel: String,
    // Use android.graphics.RectF to store the raw coordinates from MediaPipe
    val boundingBox: RectF,
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

    // State variables to store image dimensions for coordinate scaling
    var inputImageHeight by remember { mutableIntStateOf(1) }
    var inputImageWidth by remember { mutableIntStateOf(1) }
    var inputImageRotation by remember { mutableIntStateOf(0) }

    val translationManager = remember { TranslationManager() }

    val objectDetectorHelper = remember {
        ObjectDetectorHelper(
            context = context,
            objectDetectorListener = object : ObjectDetectorHelper.DetectorListener {
                override fun onError(error: String, errorCode: Int) {
                    Log.e("CameraScreen", "Detector Error: $error")
                }

                override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
                    // Get the detection results
                    val mediaPipeDetections =
                        resultBundle.results.firstOrNull()?.detections() ?: emptyList()
                    inputImageHeight = resultBundle.inputImageHeight
                    inputImageWidth = resultBundle.inputImageWidth
                    inputImageRotation = resultBundle.inputImageRotation

                    // Process results in a coroutine
                    scope.launch {
                        val newDetections = mediaPipeDetections.mapIndexed { index, detection ->
                            val label =
                                detection.categories().firstOrNull()?.categoryName() ?: "Unknown"
                            val confidence = detection.categories().firstOrNull()?.score() ?: 0f
                            val boundingBox = detection.boundingBox()
                            val graphicsRectF = RectF(
                                boundingBox.left,
                                boundingBox.top,
                                boundingBox.right,
                                boundingBox.bottom
                            )

                            val translated = translationManager.translate(label)

                            DetectionData(
                                id = index,
                                label = label,
                                translatedLabel = translated,
                                boundingBox = graphicsRectF,
                                confidence = confidence
                            )
                        }
                        detections = newDetections
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            objectDetectorHelper.clearObjectDetector()
            translationManager.close()
        }
    }

    LaunchedEffect(selectedLanguage) {
        isModelLoading = true
        val languageCode = TranslationManager.getLanguageCode(selectedLanguage)
        translationManager.downloadModel(languageCode) { message ->
            loadingMessage = message
        }
        isModelLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.lingualens_logo_no_bg),
                        contentDescription = "LinguaLens Logo",
                        modifier = Modifier.height(35.dp)
                    )
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Screen.Favorites.route)
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

                                val preview = Preview.Builder()
                                    .setTargetRotation(view.display.rotation)
                                    .build()
                                    .also {
                                        it.surfaceProvider = view.surfaceProvider
                                    }

                                val imageCaptureBuilder = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .setTargetRotation(view.display.rotation)
                                imageCapture = imageCaptureBuilder.build()

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setTargetRotation(view.display.rotation)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                    .build()
                                    .also {
                                        it.setAnalyzer(
                                            Executors.newSingleThreadExecutor()
                                        ) { imageProxy ->
                                            objectDetectorHelper.detectLivestreamFrame(imageProxy)
                                        }
                                    }

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
                                    Log.e("CameraScreen", "Camera binding failed: ${e.message}")
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Pass the image geometry state to the DetectionOverlay
                DetectionOverlay(
                    detections = detections,
                    inputImageHeight = inputImageHeight,
                    inputImageWidth = inputImageWidth,
                    inputImageRotation = inputImageRotation,
                    onBoxClicked = { detection ->
                        imageCapture?.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = imageProxyToBitmap(image)
                                    val rotatedBitmap = rotateBitmap(
                                        bitmap,
                                        image.imageInfo.rotationDegrees.toFloat()
                                    )
                                    image.close()

                                    _root_ide_package_.com.lingualens.capturedBitmap = rotatedBitmap

                                    navController.navigate(
                                        Screen.Save.createRoute(
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

/**
 * A composable that displays detection results overlaid on the camera feed.
 *
 * This composable replicates the coordinate transformation logic from
 * MediaPipe's official `OverlayView` class to correctly scale and rotate
 * bounding boxes from the image coordinate space to the view coordinate space.
 */
@Composable
private fun DetectionOverlay(
    detections: List<DetectionData>,
    inputImageHeight: Int,
    inputImageWidth: Int,
    inputImageRotation: Int,
    onBoxClicked: (DetectionData) -> Unit
) {
    // BoxWithConstraints is used to get the exact dimensions of the
    // composable, which represent the "view" dimensions.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Get the view's width and height
        val viewWidth = this.maxWidth.value
        val viewHeight = this.maxHeight.value

        // 1. Calculate rotated image dimensions
        val (rotatedImageWidth, rotatedImageHeight) = when (inputImageRotation) {
            0, 180 -> Pair(inputImageWidth, inputImageHeight)
            90, 270 -> Pair(inputImageHeight, inputImageWidth) // Swap width and height
            else -> return@BoxWithConstraints // Invalid rotation
        }

        // 2. Calculate scale factor.
        // For LIVE_STREAM, MediaPipe uses FILL_START, which corresponds to max()
        // This scales the image to fill the view, potentially cropping it.
        val scaleFactor = max(
            viewWidth / rotatedImageWidth,
            viewHeight / rotatedImageHeight
        )

        // If the scale factor is 0, the view is not yet laid out.
        if (scaleFactor == 0f) return@BoxWithConstraints

        // 3. Iterate and transform each detection
        detections.forEach { detection ->
            // Create a copy of the raw bounding box
            val boxRect = RectF(detection.boundingBox)

            // 4. Apply the matrix transformation
            val matrix = Matrix()

            // 4a. Move coordinate center to (0,0)
            matrix.postTranslate(-inputImageWidth / 2f, -inputImageHeight / 2f)

            // 4b. Rotate
            matrix.postRotate(inputImageRotation.toFloat())

            // 4c. Move coordinate center back, swapping dimensions if rotated 90/270
            if (inputImageRotation == 90 || inputImageRotation == 270) {
                matrix.postTranslate(inputImageHeight / 2f, inputImageWidth / 2f)
            } else {
                matrix.postTranslate(inputImageWidth / 2f, inputImageHeight / 2f)
            }

            // 4d. Apply the transformation to the bounding box
            matrix.mapRect(boxRect)

            // 5. Scale the transformed box to the view's coordinate space
            val left = boxRect.left * scaleFactor
            val top = boxRect.top * scaleFactor
            val right = boxRect.right * scaleFactor
            val bottom = boxRect.bottom * scaleFactor

            // Use the calculated, scaled coordinates to draw the UI elements
            // This is the same UI logic as the original file, but now
            // using the *correct* coordinates.
            Column(
                modifier = Modifier
                    .offset(
                        x = left.dp,
                        y = top.dp
                    )
                    .size(
                        width = (right - left).dp,
                        height = (bottom - top).dp
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
    val languages = listOf(
        "English",
        "Spanish",
        "French",
        "German",
        "Italian",
        "Portuguese",
        "Polish",
        "Dutch",
        "Japanese",
        "Chinese",
        "Korean",
        "Russian",
        "Arabic"
    )

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

/**
 * Converts an [ImageProxy] (in JPEG format) to a [Bitmap].
 * This is used for the ImageCapture use case.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    // This assumes the ImageProxy format is JPEG, which is the default for ImageCapture
    val buffer = imageProxy.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

/**
 * Rotates a [Bitmap] by the specified number of degrees.
 */
fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap

    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}