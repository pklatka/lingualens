package com.lingualens.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import androidx.core.graphics.createBitmap

class ObjectDetectorHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTDETV2,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    var objectDetectorListener: DetectorListener? = null
) {

    private var objectDetector: ObjectDetector? = null
    private var imageRotation = 0
    private lateinit var imageProcessingOptions: ImageProcessingOptions

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector?.close()
        objectDetector = null
    }

    // Initialize the object detector using current settings on the
    // thread that is using it. CPU can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the detector
    fun setupObjectDetector() {
        // Set general detection options, including number of used threads
        val baseOptionsBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }

            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName = when (currentModel) {
            MODEL_EFFICIENTDETV0 -> "efficientdet-lite0.tflite"
            MODEL_EFFICIENTDETV2 -> "efficientdet-lite2.tflite"
            MODEL_SSD_MOBILENETV2 -> "ssd-mobilenet-v2.tflite"
            else -> "efficientdet-lite0.tflite"
        }

        baseOptionsBuilder.setModelAssetPath(modelName)

        // Check if runningMode is consistent with objectDetectorListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (objectDetectorListener == null) {
                    throw IllegalStateException(
                        "objectDetectorListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }

            RunningMode.IMAGE, RunningMode.VIDEO -> {
                // no-op
            }
        }

        try {
            val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setScoreThreshold(threshold).setRunningMode(runningMode)
                .setMaxResults(maxResults)

            imageProcessingOptions = ImageProcessingOptions.builder()
                .setRotationDegrees(imageRotation).build()

            when (runningMode) {
                RunningMode.IMAGE, RunningMode.VIDEO -> optionsBuilder.setRunningMode(
                    runningMode
                )

                RunningMode.LIVE_STREAM -> optionsBuilder.setRunningMode(
                    runningMode
                ).setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for details"
            )
            Log.e(TAG, "TFLite failed to load model with error: " + e.message)
        } catch (e: RuntimeException) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. See error logs for " + "details",
                GPU_ERROR
            )
            Log.e(
                TAG,
                "Object detector failed to load model with error: " + e.message
            )
        }
    }

    // Runs object detection on live streaming cameras frame-by-frame and returns the results
    // asynchronously to the caller.
    fun detectLivestreamFrame(imageProxy: ImageProxy) {

        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLivestreamFrame" + " while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        // If the input image rotation is change, stop all detector
        if (imageProxy.imageInfo.rotationDegrees != imageRotation) {
            imageRotation = imageProxy.imageInfo.rotationDegrees
            clearObjectDetector()
            setupObjectDetector()
            return
        }

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(bitmapBuffer).build()

        detectAsync(mpImage, frameTime)
    }

    // Run object detection using MediaPipe Object Detector API
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        // As we're using running mode LIVE_STREAM, the detection result will be returned in
        // returnLivestreamResult function
        objectDetector?.detectAsync(mpImage, imageProcessingOptions, frameTime)
    }

    // Return the detection result to this ObjectDetectorHelper's caller
    private fun returnLivestreamResult(
        result: ObjectDetectorResult, input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        objectDetectorListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width,
                imageRotation
            )
        )
    }

    // Return errors thrown during detection to this ObjectDetectorHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        objectDetectorListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Wraps results from inference, the time it takes for inference to be performed, and
    // the input image and height for properly scaling UI to return back to callers
    data class ResultBundle(
        val results: List<ObjectDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inputImageRotation: Int = 0
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTDETV0 = 0
        const val MODEL_EFFICIENTDETV2 = 1
        const val MODEL_SSD_MOBILENETV2 = 2
        const val MAX_RESULTS_DEFAULT = 4
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val TAG = "ObjectDetectorHelper"
    }

    // Used to pass results or errors back to the calling class
    interface DetectorListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}