package com.example.myapplication

import android.util.Log
import android.util.Size
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class PhoneRingingImageProcessor(
    private val activity: MainActivity,
) : ImageProcessor {

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )

    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableClassification()
            .enableMultipleObjects()
            .build()
    )

    override fun process(imageProxy: ImageProxy, image: InputImage) {

        poseDetector.process(image)
            .addOnSuccessListener { pose ->
                val imageSize = Size(image.height, image.width) // image is rotated
                val canvasSize = Size(activity.myCanvas.width, activity.myCanvas.height)
                val mapper = PoseObjectMapper(imageSize, canvasSize)
                if (pose.allPoseLandmarks.isEmpty()) {
                    Log.i("MY_APP", "empty pose")
                    val emptyScene = Scene(person = null, objects = emptyList())
                    activity.onSceneUpdated(emptyScene)
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                var currentObjects = emptyList<DetectedObject>()

                objectDetector.process(image)
                    .addOnSuccessListener { objects ->
                        currentObjects = objects
                    }
                    .addOnFailureListener {
                        Log.e("MY_APP", "object detector failure", it)
                    }
                    .addOnCompleteListener {
                        val person = mapper.mapPose(pose)
                        val mappedObjects = mapper.mapObjects(currentObjects)
                        val scene = Scene(person = person, objects = mappedObjects)
                        activity.onSceneUpdated(scene)

                        imageProxy.close()
                    }
            }
            .addOnFailureListener {
                activity.viewBinding.textView.text = "Error on PHONE_RINGING"
            }
    }
}


