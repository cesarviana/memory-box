package com.example.myapplication

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.Pose
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
                if (pose.allPoseLandmarks.isEmpty()) {
                    Log.i("MY_APP", "empty pose")
                    activity.onNoPose()
                    imageProxy.close()
                    return@addOnSuccessListener
                }

                activity.onPoseDetected(pose)
                objectDetector.process(image)
                    .addOnSuccessListener { objects ->
                        activity.onObjectsDetected(objects)
                    }
                    .addOnFailureListener {
                        Log.e("MY_APP", "object detector failure", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
            .addOnFailureListener {
                activity.viewBinding.textView.text = "Error on PHONE_RINGING"
            }
    }
}
