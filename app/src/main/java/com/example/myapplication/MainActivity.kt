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
                PhoneRingingImageProcessor(activity, activity.poseLandmarkView)
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

    private var mediaPlayer: MediaPlayer? = null

    internal lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalyzer: ImageAnalysis
    internal lateinit var poseLandmarkView: PoseLandmarkView

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
                    it.setAnalyzer(cameraExecutor, StatefulImageAnalyzer(this))
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

    internal fun transitionToInitial() {
        currentState = AppState.INITIAL
        viewBinding.stateLabel.text = "Initial"
        viewBinding.textView.text = "Waiting for face..."

        mediaPlayer?.release()
        mediaPlayer = null
        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = null
    }

    internal fun transitionToPhoneRinging() {
        currentState = AppState.PHONE_RINGING
        viewBinding.stateLabel.text = "Phone Ringing"
        viewBinding.textView.text = "Phone Ringing..."

        mediaPlayer = MediaPlayer.create(this, R.raw.phone_ringing)
        mediaPlayer?.start()

        ringingTimeoutRunnable = Runnable { transitionToInitial() }
        stateHandler.postDelayed(ringingTimeoutRunnable!!, 30000)
    }

    internal fun transitionToPlayingVideo() {
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

    class StatefulImageAnalyzer(
        private val activity: MainActivity,
    ) : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L

        @SuppressLint("UnsafeOptInUsageError")
        @ExperimentalGetImage
        private fun transformImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
            val bitmap = imageProxy.toBitmap() ?: return null

            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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
            val transformedBitmap = transformImageProxyToBitmap(imageProxy)
            if (transformedBitmap == null) {
                imageProxy.close()
                return
            }

            val image = InputImage.fromBitmap(transformedBitmap, 0)

            val processor = activity.currentState.getImageProcessor(activity)
            processor.process(imageProxy, image)
        }
    }
}
