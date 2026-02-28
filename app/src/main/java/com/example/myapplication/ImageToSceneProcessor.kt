package com.example.myapplication

import android.util.Size
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class ImageToSceneProcessor(
    private val canvasSize: Size,
) : ImageProcessor {

    private var sceneUpdateListener: ((Scene) -> Unit)? = null

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
                val imageSize = Size(image.height, image.width) // image is rotated
                val mapper = PoseObjectMapper(imageSize, canvasSize)
                if (pose.allPoseLandmarks.isEmpty()) {
                    val emptyScene = Scene(person = null)
                    sceneUpdateListener?.invoke(emptyScene)
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                val person = mapper.mapPose(pose)
                val scene = Scene(person = person)
                sceneUpdateListener?.invoke(scene)
            }
            .addOnFailureListener {
                // Error handling - could be improved with a separate error listener
            }.addOnCompleteListener {
                imageProxy.close()
            }
    }
}


