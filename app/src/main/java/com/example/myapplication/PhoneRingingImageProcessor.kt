package com.example.myapplication

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PhoneRingingImageProcessor(
    private val activity: MainActivity,
) : ImageProcessor {

    private val earIndexMinProximity = 60

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

    private fun findHandNearToEar(pose: Pose): PoseLandmark? {
        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
        val leftHand = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
        val rightHand = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)

        val distancesText = StringBuilder()

        if (leftEar != null && leftHand != null) {
            val d = distance(leftEar, leftHand)
            distancesText.append("L $d ")
            if (d < earIndexMinProximity) {
                return leftHand
            }
        }

        if (rightEar != null && rightHand != null) {
            val d = distance(rightEar, rightHand)
            distancesText.append("R $d")
            if (d < earIndexMinProximity) {
                return rightHand
            }
        }
        return null
    }

    private fun distance(p1: PoseLandmark, p2: PoseLandmark): Int {
        val yDifference = abs(p1.position.y - p2.position.y)
        val xDifference = abs(p1.position.x - p2.position.x)
        return sqrt((yDifference * yDifference) + (xDifference * xDifference)).roundToInt()
    }
}
