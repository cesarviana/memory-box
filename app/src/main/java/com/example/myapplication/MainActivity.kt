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
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
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
        INITIAL,
        PHONE_RINGING,
        PLAYING_VIDEO,
        WAITING_FOR_RECORDING,
        RECORDING,
        RECORDING_COMPLETE
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val RINGING_TIMEOUT_MS = 30_000L
        private const val PERSON_DETECTION_TIMEOUT_MS = 30_000L
    }

    internal var currentState: AppState = AppState.INITIAL
    private val stateHandler = Handler(Looper.getMainLooper())
    private var ringingTimeoutRunnable: Runnable? = null
    private var personDetectionTimeoutRunnable: Runnable? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var recordingFilePath: String? = null
    private var blinkAnimation: android.view.animation.Animation? = null

    internal lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalyzer: ImageAnalysis

    private val sceneSequenceAnalyser = SceneSequenceAnalyser()
    private val sequence = Sequence()

    private lateinit var initialProcessor: ImageToSceneProcessor
    private lateinit var phoneRingingProcessor: ImageToSceneProcessor
    private lateinit var waitingForRecordingProcessor: ImageToSceneProcessor
    private lateinit var recordingProcessor: ImageToSceneProcessor
    private val playingVideoProcessor = NoOpImageProcessor()

    private fun initializeProcessors() {
        initialProcessor = ImageToSceneProcessor(::getCanvasSize).onSceneUpdated { scene ->
            onInitialSceneUpdated(scene)
        }
        phoneRingingProcessor = ImageToSceneProcessor(::getCanvasSize).onSceneUpdated { scene ->
            onSceneUpdated(scene)
        }
        waitingForRecordingProcessor =
            ImageToSceneProcessor(::getCanvasSize).onSceneUpdated { scene ->
                onWaitingForRecordingSceneUpdated(scene)
            }
        recordingProcessor = ImageToSceneProcessor(::getCanvasSize).onSceneUpdated { scene ->
            onRecordingSceneUpdated(scene)
        }
    }

    internal fun getCurrentImageProcessor(): ImageProcessor = when (currentState) {
        AppState.INITIAL -> initialProcessor
        AppState.PHONE_RINGING -> phoneRingingProcessor
        AppState.PLAYING_VIDEO -> playingVideoProcessor
        AppState.WAITING_FOR_RECORDING -> waitingForRecordingProcessor
        AppState.RECORDING -> recordingProcessor
        AppState.RECORDING_COMPLETE -> playingVideoProcessor
    }

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

        // Initialize the slideshow with images
        viewBinding.imageSlideshow.setImagesFromResources(
            listOf(R.raw.aline_cesar_bw, R.raw.aline_cesar_bw_2),
            intervalMs = 120_000L
        )

        initializeProcessors()

        hideSystemUI()

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Wait for the layout to be complete before starting camera
        viewBinding.root.post {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
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

//            val preview = Preview.Builder()
//                .build().also {
//                    it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
//                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, StatefulImageAnalyzer(this))
                }

            // Setup video capture
            videoCapture = VideoCapture.withOutput(
                Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.HIGHEST,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
            )

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer, videoCapture
                )
            } catch (exc: Exception) {
                Log.e("MY_APP", exc.message, exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    internal fun onInitialSceneUpdated(scene: Scene) {
        sequence.add(scene)
        viewBinding.myCanvas.setScene(scene)

        if (scene.hasNoPerson()) {
            Log.i("MY_APP", "No person in initial state")
            personDetectionTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
            personDetectionTimeoutRunnable = null
            return
        }

        if (sceneSequenceAnalyser.isHoldingPhone(sequence)) {
            Log.i("MY_APP", "Person holding phone near ear detected in initial state!")
            transitionToPlayingVideo()
            return
        }

        if (personDetectionTimeoutRunnable == null) {
            Log.i("MY_APP", "Starting person detection timeout")
            personDetectionTimeoutRunnable = Runnable {
                if (currentState == AppState.INITIAL && sequence.getLatestScene()?.hasPerson() == true
                ) {
                    Log.i(
                        "MY_APP",
                        "Person still detected after 30 seconds, transitioning to phone ringing"
                    )
                    transitionToPhoneRinging()
                }
            }
            stateHandler.postDelayed(personDetectionTimeoutRunnable!!, PERSON_DETECTION_TIMEOUT_MS)
        }
    }

    internal fun onSceneUpdated(scene: Scene) {
        sequence.add(scene)
        viewBinding.myCanvas.setScene(scene)

        if (scene.hasNoPerson()) {
            transitionToInitial()
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

        stopRinging()
        stopVideo()

        ringingTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        ringingTimeoutRunnable = null

        personDetectionTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        personDetectionTimeoutRunnable = null

        // Hide all messages and recording UI
        viewBinding.thankYouMessage.visibility = android.view.View.GONE
        viewBinding.recordingMessage.visibility = android.view.View.GONE
        viewBinding.recordingIndicator.visibility = android.view.View.GONE
        viewBinding.myCanvas.visibility = android.view.View.GONE

        // Show slideshow
        viewBinding.imageSlideshow.visibility = android.view.View.VISIBLE
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

        viewBinding.vintageCountdown.stop()
    }

    internal fun transitionToPhoneRinging() {
        currentState = AppState.PHONE_RINGING
        viewBinding.stateLabel.text = "Phone Ringing"

        personDetectionTimeoutRunnable?.let { stateHandler.removeCallbacks(it) }
        personDetectionTimeoutRunnable = null

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

        stopRinging()
        showCountdownBeforeVideo()
    }

    private fun showCountdownBeforeVideo() {
        viewBinding.vintageCountdown.visibility = android.view.View.VISIBLE
        viewBinding.vintageCountdown.startCountdown(5) {
            playVideo()
        }
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
                    transitionToWaitingForRecording()
                }
                visibility = android.view.View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("MY_APP", "Error playing video", e)
        }
    }

    internal fun transitionToWaitingForRecording() {
        sequence.clear()
        currentState = AppState.WAITING_FOR_RECORDING
        viewBinding.stateLabel.text = "Waiting for Recording"

        stopVideo()

        // Show the recording message
        viewBinding.recordingMessage.visibility = android.view.View.VISIBLE
        viewBinding.imageSlideshow.visibility = android.view.View.GONE
        viewBinding.myCanvas.visibility = android.view.View.GONE
    }

    internal fun onWaitingForRecordingSceneUpdated(scene: Scene) {
        sequence.add(scene)

        if (scene.hasNoPerson()) {
            Log.i("MY_APP", "No person detected in waiting for recording state")
            return
        }

        if (sceneSequenceAnalyser.isHoldingPhone(sequence)) {
            Log.i("MY_APP", "Person holding phone detected, starting recording countdown")
            transitionToRecording()
        }
    }

    internal fun transitionToRecording() {
        currentState = AppState.RECORDING
        viewBinding.stateLabel.text = "Recording"

        // Hide message and show countdown
        viewBinding.recordingMessage.visibility = android.view.View.GONE
        viewBinding.vintageCountdown.visibility = android.view.View.VISIBLE

        viewBinding.vintageCountdown.startCountdown(3) {
            startRecording()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        try {
            val capture = videoCapture ?: run {
                Log.e("MY_APP", "VideoCapture not initialized")
                Toast.makeText(this, "Erro: câmera não inicializada", Toast.LENGTH_SHORT).show()
                transitionToInitial()
                return
            }

            // Show recording indicator
            viewBinding.vintageCountdown.visibility = android.view.View.GONE
            viewBinding.recordingIndicator.visibility = android.view.View.VISIBLE
            startBlinking()

            val myAppDir = java.io.File(filesDir, "MyApplication")
            if (!myAppDir.exists()) {
                myAppDir.mkdirs()
            }
            // date time in format YYYY-MM-DD-HH-mm-ss
            val date =
                java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.getDefault())
                    .format(
                        java.util.Date()
                    )
            val videoFile = java.io.File(
                myAppDir,
                "my_application_recording_${date}.mp4"
            )
            recordingFilePath = videoFile.absolutePath

            val outputOptions = FileOutputOptions.Builder(videoFile).build()

            activeRecording = capture.output
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            Log.i("MY_APP", "Video recording started")
                        }

                        is VideoRecordEvent.Finalize -> {
                            if (event.hasError()) {
                                Log.e("MY_APP", "Video recording error: ${event.error}")
                                activeRecording = null
                            } else {
                                runOnUiThread {
                                    Toast.makeText(
                                        this,
                                        "Vídeo salvo: $recordingFilePath",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("MY_APP", "Error starting recording", e)
            Toast.makeText(this, "Erro ao iniciar gravação", Toast.LENGTH_SHORT).show()
            transitionToInitial()
        }
    }

    private fun startBlinking() {
        if (blinkAnimation == null) {
            blinkAnimation = AnimationUtils.loadAnimation(this, R.anim.blink)
        }
        viewBinding.recordingIndicator.startAnimation(blinkAnimation)
    }

    private fun stopBlinking() {
        viewBinding.recordingIndicator.clearAnimation()
        blinkAnimation = null
    }

    internal fun onRecordingSceneUpdated(scene: Scene) {
        sequence.add(scene)

        if (!sceneSequenceAnalyser.isHoldingPhone(sequence)) {
            Log.i("MY_APP", "Person stopped holding phone, stopping recording")
            stopRecording()
            transitionToRecordingComplete()
        }
    }

    private fun stopRecording() {
        try {
            stopBlinking()
            activeRecording?.stop()
            activeRecording = null

            viewBinding.recordingIndicator.visibility = android.view.View.GONE

            Log.i("MY_APP", "Video recording stopped")
        } catch (e: Exception) {
            Log.e("MY_APP", "Error stopping recording", e)
        }
    }

    internal fun transitionToRecordingComplete() {
        sequence.clear()
        currentState = AppState.RECORDING_COMPLETE
        viewBinding.stateLabel.text = "Recording Complete"

        // Show thank you message
        viewBinding.thankYouMessage.visibility = android.view.View.VISIBLE
        viewBinding.recordingIndicator.visibility = android.view.View.GONE
        viewBinding.recordingMessage.visibility = android.view.View.GONE
        viewBinding.imageSlideshow.visibility = android.view.View.GONE
        viewBinding.myCanvas.visibility = android.view.View.GONE

        // Transition back to initial after 5 seconds
        stateHandler.postDelayed({
            transitionToInitial()
        }, 5000)
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopRinging()
        stopVideo()
        stopRecording()
        viewBinding.imageSlideshow.stopAutoPlay()
    }

    internal fun getCanvasSize(): Size {
        val width = viewBinding.myCanvas.width.takeIf { it > 0 } ?: viewBinding.root.width
        val height = viewBinding.myCanvas.height.takeIf { it > 0 } ?: viewBinding.root.height
        return Size(width, height)
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

            val inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            activity.getCurrentImageProcessor().process(imageProxy, inputImage)
        }
    }
}
