package com.lingualens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveScreen(
    navController: NavController,
    originalLabel: String,
    translatedLabel: String,
    capturedBitmap: Bitmap?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Save Translation") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Display captured image
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (capturedBitmap != null) {
                        Image(
                            bitmap = capturedBitmap.asImageBitmap(),
                            contentDescription = "Detected Object",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "No Image",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No image available",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Display translations
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            "Original:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            originalLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Divider()

                    Column {
                        Text(
                            "Translation:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            translatedLabel,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
                ) {
                    Text("Return")
                }

                Button(
                    onClick = {
                        if (capturedBitmap != null) {
                            isSaving = true
                            scope.launch {
                                val success = saveImage(
                                    context,
                                    capturedBitmap,
                                    originalLabel,
                                    translatedLabel
                                )
                                isSaving = false

                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Image saved successfully!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navController.popBackStack()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Failed to save image",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "No image to save",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving && capturedBitmap != null
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}

private suspend fun saveImage(
    context: Context,
    bitmap: Bitmap,
    originalLabel: String,
    translatedLabel: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val filename = "LinguaLens_${System.currentTimeMillis()}.jpg"
        val description = "Original: $originalLabel | Translation: $translatedLabel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LinguaLens")
                put(MediaStore.Images.Media.DESCRIPTION, description)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                true
            } ?: false
        } else {
            // Use legacy file saving for older Android versions
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val linguaLensDir = File(picturesDir, "LinguaLens")
            linguaLensDir.mkdirs()

            val file = File(linguaLensDir, filename)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            // Notify media scanner
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DESCRIPTION, description)
            }
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}