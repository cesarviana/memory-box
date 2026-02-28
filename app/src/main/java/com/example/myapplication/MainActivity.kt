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
import androidx.core.net.toUri
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
    private var mediaPlayer: MediaPlayer? = null

    internal lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalyzer: ImageAnalysis

    private val sceneSequenceAnalyser = SceneSequenceAnalyser()
    private val sequence = Sequence()

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
        viewBinding.videoView.visibility = android.view.View.GONE
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

    internal fun onSceneUpdated(updatedScene: Scene) {
        sequence.add(updatedScene)
        viewBinding.myCanvas.setScene(updatedScene)

        if (updatedScene.hasNoPerson()) {
//            if (currentState == AppState.PHONE_RINGING) {
            transitionToInitial()
//            }
            return
        }

        val poseState = sceneSequenceAnalyser.getLatestPose(sequence)
        viewBinding.myCanvas.setPoseState(poseState)

        if (currentState == AppState.PHONE_RINGING && sceneSequenceAnalyser.isHoldingPhone(sequence)) {
            Log.i("MY_APP", "Person holding phone near ear detected!")
            transitionToPlayingVideo()
        }

    }


    internal fun transitionToInitial() {
        sequence.clear()
        currentState = AppState.INITIAL
        viewBinding.stateLabel.text = "Initial"
        viewBinding.textView.text = "Waiting for face..."

        stopRinging()
        stopVideo()

        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = null
    }

    private fun stopVideo() {
        viewBinding.videoView.let {
            if (it.isPlaying) {
                it.stopPlayback()
            }
            it.visibility = android.view.View.GONE
        }
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
        viewBinding.myCanvas.visibility = android.view.View.VISIBLE

        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = Runnable {
            if (currentState == AppState.PHONE_RINGING && sequence.getLatestScene()
                    ?.hasNoPerson() != false
            ) {
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
        viewBinding.myCanvas.visibility = android.view.View.GONE

        playVideo()
    }

    private fun playVideo() {
        try {
            val videoUri = "android.resource://${packageName}/${R.raw.cabine}"

            viewBinding.videoView.apply {
                setVideoURI(videoUri.toUri())
                setOnPreparedListener { mediaPlayer ->
                    mediaPlayer.isLooping = false
                    start()
                }
                setOnCompletionListener {
                    transitionToInitial()
                }
                visibility = android.view.View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("MY_APP", "Error playing video", e)
            viewBinding.textView.text = "Error playing video"
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopRinging()
        stopVideo()
    }

    internal fun getCanvasSize(): Size {
        return Size(viewBinding.myCanvas.width, viewBinding.myCanvas.height)
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
