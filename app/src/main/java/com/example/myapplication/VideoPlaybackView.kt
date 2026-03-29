package com.example.myapplication

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.VideoView

class VideoPlaybackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backgroundVideo: VideoView
    private val backgroundDimmer: View
    private val foregroundVideo: VideoView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_video_playback, this, true)
        backgroundVideo = findViewById(R.id.video_background)
        backgroundDimmer = findViewById(R.id.video_background_dimmer)
        foregroundVideo = findViewById(R.id.video_foreground)
        visibility = GONE

        backgroundVideo.setRenderEffect(
            RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
        )
    }

    fun play(uri: Uri, onCompletion: () -> Unit) {
        stop()
        visibility = VISIBLE
        backgroundDimmer.visibility = VISIBLE

        foregroundVideo.apply {
            setVideoURI(uri)
            setOnPreparedListener { player ->
                player.isLooping = false
                start()
            }
            setOnCompletionListener {
                onCompletion()
            }
            visibility = VISIBLE
        }

        backgroundVideo.apply {
            setVideoURI(uri)
            setOnPreparedListener { player ->
                player.setVolume(0f, 0f)
                player.isLooping = false
                start()
            }
            visibility = VISIBLE
        }
    }

    fun stop() {
        foregroundVideo.setOnPreparedListener(null)
        foregroundVideo.setOnCompletionListener(null)
        if (foregroundVideo.isPlaying) {
            foregroundVideo.stopPlayback()
        }
        foregroundVideo.visibility = GONE

        backgroundVideo.setOnPreparedListener(null)
        backgroundVideo.setOnCompletionListener(null)
        if (backgroundVideo.isPlaying) {
            backgroundVideo.stopPlayback()
        }
        backgroundVideo.visibility = GONE
        backgroundDimmer.visibility = GONE
        visibility = GONE
    }
}

