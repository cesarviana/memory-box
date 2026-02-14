package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private enum class AppState {
        INITIAL,
        PHONE_RINGING,
        PLAYING_VIDEO
    }

    private var currentState: AppState = AppState.INITIAL
    private val stateHandler = Handler(Looper.getMainLooper())
    private var ringingTimeoutRunnable: Runnable? = null

    private var mediaPlayer: MediaPlayer? = null

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var poseLandmarkView: PoseLandmarkView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(
                baseContext, "Permission request denied", Toast.LENGTH_SHORT
            ).show()
        } else {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        poseLandmarkView = viewBinding.poseLandmarkView
        hideSystemUI()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, viewBinding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
            baseContext, it
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FacesDetector(this, poseLandmarkView))
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                // Log the exception
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun transitionToInitial() {
        currentState = AppState.INITIAL
        viewBinding.stateLabel.text = "Initial"
        viewBinding.textView.text = "Waiting for face..."

        mediaPlayer?.release()
        mediaPlayer = null
        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = null
    }

    private fun transitionToPhoneRinging() {
        currentState = AppState.PHONE_RINGING
        viewBinding.stateLabel.text = "Phone Ringing"
        viewBinding.textView.text = "Phone Ringing..."

        mediaPlayer = MediaPlayer.create(this, R.raw.phone_ringing)
        mediaPlayer?.start()

        ringingTimeoutRunnable = Runnable { transitionToInitial() }
        stateHandler.postDelayed(ringingTimeoutRunnable!!, 30000)
    }

    private fun transitionToPlayingVideo() {
        currentState = AppState.PLAYING_VIDEO
        viewBinding.stateLabel.text = "Playing Video"
        viewBinding.textView.text = "Playing video"

        mediaPlayer?.release()
        mediaPlayer = null
        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = null

        // TODO: Start playing a video here
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    class FacesDetector(
        private val activity: MainActivity,
        private val poseLandmarkView: PoseLandmarkView
    ) : ImageAnalysis.Analyzer {
        private val earIndexMinProximity = 60
        private var lastAnalyzedTimestamp = 0L

        private val faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )

        private val poseDetector = PoseDetection.getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build()
        )

        private fun distance(p1: PoseLandmark, p2: PoseLandmark): Int {
            val yDifference = abs(p1.position.y - p2.position.y)
            val xDifference = abs(p1.position.x - p2.position.x)
            return sqrt((yDifference * yDifference) + (xDifference * xDifference)).roundToInt()
        }

        private fun isNear(p1: PoseLandmark, p2: PoseLandmark): Boolean {
            return distance(p1, p2) < earIndexMinProximity
        }

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {

            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp < 1000) {
                imageProxy.close()
                return
            }
            lastAnalyzedTimestamp = currentTimestamp

            @ExperimentalGetImage
            val bitmap = imageProxy.toBitmap()

            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            val flippedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            val image = InputImage.fromBitmap(flippedBitmap, 0)

            if (activity.currentState == AppState.INITIAL) {
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
            } else if (activity.currentState == AppState.PHONE_RINGING) {
                poseDetector.process(image).addOnSuccessListener { pose ->
                    if (pose.allPoseLandmarks.isNotEmpty()) {
                        poseLandmarkView.setPose(pose, image.width, image.height)
                        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)!!
                        val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)!!

                        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)!!
                        val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)!!

                        if (isNear(leftEar, leftIndex) || isNear(rightEar, rightIndex)) {
                            activity.transitionToPlayingVideo()
                        }
                        activity.viewBinding.textView.text = "L ${distance(leftEar, leftIndex)} R ${distance(rightEar, rightIndex)}"
                    }
                }.addOnFailureListener {
                    activity.viewBinding.textView.text = "Error on PHONE_RINGING"
                }.addOnCompleteListener {
                    imageProxy.close()
                }
            }
        }
    }
}
