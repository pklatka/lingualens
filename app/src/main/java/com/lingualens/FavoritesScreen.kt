package com.lingualens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lingualens.data.AppDatabase
import com.lingualens.data.SavedTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    val translations by database.translationDao()
        .getAllTranslations()
        .collectAsState(initial = emptyList())

    var showDeleteDialog by remember { mutableStateOf<SavedTranslation?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Translations") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (translations.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    database.translationDao().deleteAll()
                                }
                            }
                        }) {
                            Icon(Icons.Default.DeleteForever, "Delete All")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (translations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No saved translations yet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap on detected objects to save them",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(translations, key = { it.id }) { translation ->
                    TranslationCard(
                        translation = translation,
                        onDelete = { showDeleteDialog = translation },
                        onExport = {
                            scope.launch {
                                exportImageWithTranslation(context, translation)
                            }
                        }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { translation ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Translation") },
            text = { Text("Are you sure you want to delete this translation?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                database.translationDao().deleteTranslation(translation)
                                // Delete image file
                                File(translation.imagePath).delete()
                            }
                        }
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TranslationCard(
    translation: SavedTranslation,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Image
                val bitmap = remember(translation.imagePath) {
                    try {
                        BitmapFactory.decodeFile(translation.imagePath)
                    } catch (e: Exception) {
                        null
                    }
                }

                if (bitmap != null) {
                    Card(
                        modifier = Modifier.size(80.dp),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = translation.originalLabel,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.size(80.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.BrokenImage, null)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Labels
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = translation.translatedLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = translation.originalLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(translation.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete button
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Export button
            FilledTonalButton(
                onClick = onExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Image with Translation")
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private suspend fun exportImageWithTranslation(context: Context, translation: SavedTranslation) {
    withContext(Dispatchers.IO) {
        try {
            // Load the original image
            val originalBitmap = BitmapFactory.decodeFile(translation.imagePath)
                ?: throw Exception("Failed to load image")

            // Create a new bitmap with extra space at the bottom for text
            val textHeight = 150 // Height for the text section
            val newBitmap = Bitmap.createBitmap(
                originalBitmap.width,
                originalBitmap.height + textHeight,
                Bitmap.Config.ARGB_8888
            )

            val canvas = Canvas(newBitmap)

            // Draw the original image
            canvas.drawBitmap(originalBitmap, 0f, 0f, null)

            // Draw background for text
            val backgroundPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#00E5FF")
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                0f,
                originalBitmap.height.toFloat(),
                originalBitmap.width.toFloat(),
                (originalBitmap.height + textHeight).toFloat(),
                backgroundPaint
            )

            // Draw text
            val textPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 48f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            val smallTextPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 36f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                alpha = 200
            }

            val centerX = originalBitmap.width / 2f
            val textY = originalBitmap.height + 50f

            // Draw original label
            canvas.drawText(
                translation.originalLabel,
                centerX,
                textY,
                textPaint
            )

            // Draw arrow
            canvas.drawText(
                "→",
                centerX,
                textY + 50f,
                smallTextPaint
            )

            // Draw translated label
            canvas.drawText(
                translation.translatedLabel,
                centerX,
                textY + 100f,
                textPaint
            )

            // Save to gallery
            saveImageToGallery(context, newBitmap, translation)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Image saved to gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun saveImageToGallery(context: Context, bitmap: Bitmap, translation: SavedTranslation) {
    val filename = "LinguaLens_${System.currentTimeMillis()}.jpg"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LinguaLens")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ) ?: throw Exception("Failed to create MediaStore entry")

    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
    } ?: throw Exception("Failed to open output stream")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, contentValues, null, null)
    }
}