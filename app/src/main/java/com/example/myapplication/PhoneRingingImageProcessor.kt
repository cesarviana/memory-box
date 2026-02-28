package com.example.myapplication

import android.util.Log
import android.util.Size
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class PhoneRingingImageProcessor(
    private val activity: MainActivity,
) : ImageProcessor {

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )

    override fun process(imageProxy: ImageProxy, image: InputImage) {

        poseDetector.process(image)
            .addOnSuccessListener { pose ->
                val imageSize = Size(image.height, image.width) // image is rotated
                val canvasSize = activity.getCanvasSize()
                val mapper = PoseObjectMapper(imageSize, canvasSize)
                if (pose.allPoseLandmarks.isEmpty()) {
                    val emptyScene = Scene(person = null)
                    activity.onSceneUpdated(emptyScene)
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                val person = mapper.mapPose(pose)
                val scene = Scene(person = person)
                activity.onSceneUpdated(scene)
            }
            .addOnFailureListener {
                activity.viewBinding.textView.text = "Error on PHONE_RINGING"
            }.addOnCompleteListener {
                imageProxy.close()
            }
    }
}


