package com.example.myapplication

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class InitialImageProcessor(private val activity: MainActivity) : ImageProcessor {

    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
    )

    override fun process(imageProxy: ImageProxy, image: InputImage) {
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    activity.transitionToPhoneRinging()
                }
            }
            .addOnFailureListener { e ->
                activity.viewBinding.textView.text = "Error"
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
