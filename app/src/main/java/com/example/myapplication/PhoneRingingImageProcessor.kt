package com.example.myapplication

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class PhoneRingingImageProcessor(
    private val activity: MainActivity,
    private val poseLandmarkView: PoseLandmarkView,
) : ImageProcessor {

    private val earIndexMinProximity = 60

    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    override fun process(imageProxy: ImageProxy, image: InputImage) {
        poseDetector.process(image).addOnSuccessListener { pose ->
            if (pose.allPoseLandmarks.isNotEmpty()) {
                poseLandmarkView.setPose(pose, image.width, image.height)
                val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
                val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
                val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
                val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)

                var triggerTransition = false
                val distancesText = StringBuilder()

                if (leftEar != null && leftIndex != null) {
                    val d = distance(leftEar, leftIndex)
                    distancesText.append("L $d ")
                    if (d < earIndexMinProximity) {
                        triggerTransition = true
                    }
                }

                if (rightEar != null && rightIndex != null) {
                    val d = distance(rightEar, rightIndex)
                    distancesText.append("R $d")
                    if (d < earIndexMinProximity) {
                        triggerTransition = true
                    }
                }

                if (triggerTransition) {
                    activity.transitionToPlayingVideo()
                }

                activity.viewBinding.textView.text = distancesText.toString()
            }
        }.addOnFailureListener {
            activity.viewBinding.textView.text = "Error on PHONE_RINGING"
        }.addOnCompleteListener {
            imageProxy.close()
        }
    }

    private fun distance(p1: PoseLandmark, p2: PoseLandmark): Int {
        val yDifference = abs(p1.position.y - p2.position.y)
        val xDifference = abs(p1.position.x - p2.position.x)
        return sqrt((yDifference * yDifference) + (xDifference * xDifference)).roundToInt()
    }
}
