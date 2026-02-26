package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
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
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.pose.Pose
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    internal enum class AppState {
        INITIAL {
            override fun getImageProcessor(activity: MainActivity): ImageProcessor =
                InitialImageProcessor(activity)
        },
        PHONE_RINGING {
            override fun getImageProcessor(activity: MainActivity): ImageProcessor =
                PhoneRingingImageProcessor(activity)
        },
        PLAYING_VIDEO {
            override fun getImageProcessor(activity: MainActivity): ImageProcessor =
                NoOpImageProcessor()
        };

        abstract fun getImageProcessor(activity: MainActivity): ImageProcessor
    }

    internal var currentState: AppState = AppState.INITIAL
    private val stateHandler = Handler(Looper.getMainLooper())
    private var ringingTimeoutRunnable: Runnable? = null
    private var isPersonDetected = false

    private var mediaPlayer: MediaPlayer? = null

    internal lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalyzer: ImageAnalysis
    private lateinit var myCanvas: MyCanvas

    private val imageSize = Size(480, 640)
    private val mapper = PoseObjectMapper(imageSize)

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
        myCanvas = viewBinding.poseLandmarkView
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

            val preview = Preview.Builder()
                .build().also {
                it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, StatefulImageAnalyzer(this))
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("MY_APP", exc.message, exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    internal fun onPoseDetected(pose: Pose) {
        if (currentState == AppState.PHONE_RINGING) {
            isPersonDetected = true
        }
        mapper.updateCanvasWidth(myCanvas.width)
        myCanvas.setPerson(mapper.mapPose(pose))
    }

    internal fun onNoPose() {
        if (currentState == AppState.PHONE_RINGING) {
            transitionToInitial()
        }
        myCanvas.clearPerson()
    }

    internal fun onObjectsDetected(objects: List<DetectedObject>) {
        mapper.updateCanvasWidth(myCanvas.width)
        myCanvas.setObjects(mapper.mapObjects(objects))
    }

    internal fun transitionToInitial() {
        isPersonDetected = false
        currentState = AppState.INITIAL
        viewBinding.stateLabel.text = "Initial"
        viewBinding.textView.text = "Waiting for face..."

        stopRinging()

        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = null
    }

    private fun stopRinging() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = null
    }

    internal fun transitionToPhoneRinging() {
        currentState = AppState.PHONE_RINGING
        viewBinding.stateLabel.text = "Phone Ringing"
        viewBinding.textView.text = "Phone Ringing..."

        isPersonDetected = false
        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = Runnable {
            if (currentState == AppState.PHONE_RINGING && !isPersonDetected) {
                transitionToInitial()
            }
        }
        stateHandler.postDelayed(ringingTimeoutRunnable!!, RINGING_TIMEOUT_MS)

        mediaPlayer = MediaPlayer.create(this, R.raw.phone_ringing)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    internal fun transitionToPlayingVideo() {
        currentState = AppState.PLAYING_VIDEO
        viewBinding.stateLabel.text = "Playing Video"
        viewBinding.textView.text = "Playing video"

        stopRinging()

        // TODO: Start playing a video here
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopRinging()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val RINGING_TIMEOUT_MS = 60000L
    }

    class StatefulImageAnalyzer(
        private val activity: MainActivity,
    ) : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L
        private val imageAnalysisInterval = 500

        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {

            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp < imageAnalysisInterval) {
                imageProxy.close()
                return
            }
            lastAnalyzedTimestamp = currentTimestamp

            val mediaImage = imageProxy.image ?: run {
                imageProxy.close()
                return
            }
            Log.i("MY_APP", "Image size: ${imageProxy.width} x ${imageProxy.height}")

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            val processor = activity.currentState.getImageProcessor(activity)

            processor.process(imageProxy, inputImage)
        }
    }
}
