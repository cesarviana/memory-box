package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    internal enum class AppState {
        WAITING_PERSON,
        PERSON_HOLDING_PHONE,
        WAITING_RECORD
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    internal var currentState: AppState = AppState.WAITING_PERSON
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var recordingFilePath: String? = null
    private var blinkAnimation: android.view.animation.Animation? = null
    private var ringtonePlayer: MediaPlayer? = null
    private var hasRungForCurrentPresence = false

    internal lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private val sceneSequenceAnalyser = SceneSequenceAnalyser()
    private val sequence = Sequence()
    private lateinit var sceneProcessor: ImageToSceneProcessor

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var permissionGranted = true
        permissions.entries.forEach {
            if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
        }
        if (!permissionGranted) {
            Toast.makeText(baseContext, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
        } else {
            startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        sceneProcessor = ImageToSceneProcessor(::getCanvasSize).onSceneUpdated { scene ->
            onSceneUpdated(scene)
        }

        viewBinding.imageSlideshow.setImagesFromResources(
            listOf(R.raw.aline_cesar_bw),
            intervalMs = 120_000L
        )

        viewBinding.buttonViewMessage.setOnClickListener { playMessageVideo() }
        viewBinding.buttonRecordMessage.setOnClickListener { startRecordingMessage() }

        hideSystemUI()
        cameraExecutor = Executors.newSingleThreadExecutor()
        enterWaitingPerson()

        viewBinding.root.post {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }
    }

    internal fun getCurrentImageProcessor(): ImageProcessor = sceneProcessor

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, viewBinding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(baseContext, it)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also { it.setAnalyzer(cameraExecutor, StatefulImageAnalyzer(this)) }

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
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalyzer, videoCapture)
            } catch (exc: Exception) {
                Log.e("MY_APP", exc.message, exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onSceneUpdated(scene: Scene) {
        sequence.add(scene)
        viewBinding.myCanvas.setScene(scene)
        updatePersonPresence(scene)

        when (currentState) {
            AppState.WAITING_PERSON -> {
                if (sceneSequenceAnalyser.isHoldingPhone(sequence)) {
                    stopRingtone()
                    enterPersonHoldingPhone()
                }
            }

            AppState.PERSON_HOLDING_PHONE -> {
                if (scene.hasNoPerson()) {
                    enterWaitingPerson()
                }
            }

            AppState.WAITING_RECORD -> {
                if (scene.hasNoPerson()) {
                    stopRecording()
                    enterWaitingPerson()
                }
            }
        }
    }

    private fun enterWaitingPerson() {
        sequence.clear()
        transitionToState(AppState.WAITING_PERSON)
        hasRungForCurrentPresence = false

        stopVideo()
        stopRecording()

        viewBinding.imageSlideshow.visibility = View.VISIBLE
        viewBinding.videoView.visibility = View.GONE
        viewBinding.centralMessage.visibility = View.GONE
        viewBinding.recordingIndicator.visibility = View.GONE
        viewBinding.buttonViewMessage.visibility = View.GONE
        viewBinding.buttonRecordMessage.visibility = View.GONE
    }

    private fun enterPersonHoldingPhone() {
        transitionToState(AppState.PERSON_HOLDING_PHONE)

        stopVideo()

        viewBinding.imageSlideshow.visibility = View.GONE
        viewBinding.videoView.visibility = View.GONE
        viewBinding.centralMessage.visibility = View.VISIBLE
        viewBinding.centralMessage.text = getString(R.string.choose_option)
        viewBinding.buttonViewMessage.visibility = View.VISIBLE
        viewBinding.buttonRecordMessage.visibility = View.VISIBLE
    }

    private fun enterWaitingRecord() {
        transitionToState(AppState.WAITING_RECORD)

        stopVideo()

        viewBinding.imageSlideshow.visibility = View.GONE
        viewBinding.videoView.visibility = View.GONE
        viewBinding.centralMessage.visibility = View.VISIBLE
        viewBinding.centralMessage.text = getString(R.string.waiting_record_message)
        viewBinding.buttonViewMessage.visibility = View.GONE
        viewBinding.buttonRecordMessage.visibility = View.VISIBLE
    }

    private fun playMessageVideo() {
        stopRecording()
        viewBinding.buttonViewMessage.visibility = View.GONE
        viewBinding.buttonRecordMessage.visibility = View.GONE
        viewBinding.centralMessage.visibility = View.GONE
        viewBinding.imageSlideshow.visibility = View.GONE

        try {
            val videoUri = "android.resource://${packageName}/${R.raw.cabine}"
            viewBinding.videoView.apply {
                setVideoURI(videoUri.toUri())
                setOnPreparedListener { player ->
                    player.isLooping = false
                    start()
                }
                setOnCompletionListener {
                    enterWaitingRecord()
                }
                visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("MY_APP", "Error playing video", e)
            enterWaitingRecord()
        }
    }

    private fun updatePersonPresence(scene: Scene) {
        if (scene.hasNoPerson()) {
            hasRungForCurrentPresence = false
            return
        }

        if (hasRungForCurrentPresence) {
            return
        }

        hasRungForCurrentPresence = true
        playRingtoneOnce()
    }

    private fun playRingtoneOnce() {
        val player = ringtonePlayer ?: MediaPlayer.create(this, R.raw.receiving_call)?.also { mediaPlayer ->
            mediaPlayer.isLooping = false
            mediaPlayer.setOnCompletionListener { completedPlayer ->
                completedPlayer.seekTo(0)
            }
            mediaPlayer.setOnErrorListener { failedPlayer, what, extra ->
                Log.e("MY_APP", "Ringtone playback error: what=$what extra=$extra")
                failedPlayer.release()
                ringtonePlayer = null
                true
            }
            ringtonePlayer = mediaPlayer
        }

        if (player == null) {
            Log.e("MY_APP", "Unable to create ringtone player")
            return
        }

        if (player.isPlaying) {
            return
        }

        player.seekTo(0)
        player.start()
    }

    private fun stopRingtone() {
        ringtonePlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        ringtonePlayer = null
    }

    private fun stopVideo() {
        viewBinding.videoView.let {
            if (it.isPlaying) {
                it.stopPlayback()
            }
            it.visibility = View.GONE
        }
    }

    private fun startRecordingMessage() {
        if (activeRecording != null) {
            return
        }
        startRecording()
    }

    private fun transitionToState(state: AppState) {
        currentState = state
        viewBinding.stateLabel.text = currentState.name
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        try {
            val capture = videoCapture ?: run {
                Log.e("MY_APP", "VideoCapture not initialized")
                Toast.makeText(this, getString(R.string.camera_not_initialized), Toast.LENGTH_SHORT).show()
                return
            }

            val myAppDir = File(filesDir, "MyApplication")
            if (!myAppDir.exists()) {
                myAppDir.mkdirs()
            }

            val date = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(Date())
            val videoFile = File(myAppDir, "my_application_recording_$date.mp4")
            recordingFilePath = videoFile.absolutePath

            val outputOptions = FileOutputOptions.Builder(videoFile).build()
            activeRecording = capture.output
                .prepareRecording(this, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            viewBinding.recordingIndicator.visibility = View.VISIBLE
                            startBlinking()
                            viewBinding.centralMessage.visibility = View.VISIBLE
                            viewBinding.centralMessage.text = getString(R.string.recording_in_progress)
                            Log.i("MY_APP", "Video recording started")
                        }

                        is VideoRecordEvent.Finalize -> {
                            stopBlinking()
                            viewBinding.recordingIndicator.visibility = View.GONE
                            activeRecording = null

                            if (event.hasError()) {
                                Log.e("MY_APP", "Video recording error: ${event.error}")
                            } else {
                                Toast.makeText(
                                    this,
                                    getString(R.string.video_saved, recordingFilePath),
                                    Toast.LENGTH_LONG
                                ).show()
                                viewBinding.centralMessage.text = getString(R.string.recording_saved)
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("MY_APP", "Error starting recording", e)
            Toast.makeText(this, getString(R.string.recording_start_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            if (activeRecording != null) {
                activeRecording?.stop()
                activeRecording = null
            }
            stopBlinking()
            viewBinding.recordingIndicator.visibility = View.GONE
        } catch (e: Exception) {
            Log.e("MY_APP", "Error stopping recording", e)
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

    override fun onDestroy() {
        super.onDestroy()
        stopVideo()
        stopRecording()
        stopRingtone()
        cameraExecutor.shutdown()
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
        private val imageAnalysisInterval = 250

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
