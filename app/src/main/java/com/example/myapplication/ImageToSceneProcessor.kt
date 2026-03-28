package com.example.myapplication

import android.util.Size
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class ImageToSceneProcessor(
    private val getCanvasSize: () -> Size,
) : ImageProcessor {

    private var sceneUpdateListener: ((Scene) -> Unit)? = null
    private var cachedImageSize: Size? = null
    private var cachedCanvasSize: Size? = null
    private var cachedMapper: PoseObjectMapper? = null

    private val emptyScene = Scene(person = null)

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )

    fun onSceneUpdated(listener: (Scene) -> Unit): ImageToSceneProcessor {
        this.sceneUpdateListener = listener
        return this
    }

    override fun process(imageProxy: ImageProxy, image: InputImage) {

        poseDetector.process(image)
            .addOnSuccessListener { pose ->
                if (pose.allPoseLandmarks.isEmpty()) {
                    sceneUpdateListener?.invoke(emptyScene)
                    return@addOnSuccessListener
                }

                val mapper = getOrCreateMapper(image)
                val person = mapper.mapPose(pose)
                val scene = Scene(person = person)
                sceneUpdateListener?.invoke(scene)
            }
            .addOnFailureListener {
                // Error handling - could be improved with a separate error listener
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun getOrCreateMapper(image: InputImage): PoseObjectMapper {
        val imageSize = Size(image.height, image.width) // image is rotated
        val canvasSize = getCanvasSize()

        val mapper = cachedMapper
        if (mapper != null && cachedImageSize == imageSize && cachedCanvasSize == canvasSize) {
            return mapper
        }

        return PoseObjectMapper(imageSize, canvasSize).also {
            cachedImageSize = imageSize
            cachedCanvasSize = canvasSize
            cachedMapper = it
        }
    }
}


