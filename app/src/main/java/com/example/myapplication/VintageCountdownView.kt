package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.os.Handler
import android.os.Looper
import android.animation.ObjectAnimator
import android.media.ToneGenerator
import android.media.AudioManager

class VintageCountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var countdownSeconds = 0
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var onCountdownFinished: (() -> Unit)? = null

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)

    // Semi-transparent background paint (Gaussian blur effect)
    private val backgroundPaint = Paint().apply {
        color = Color.argb(180, 0, 0, 0) // 70% transparent black
        style = Paint.Style.FILL
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        strokeWidth = 3f
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    fun startCountdown(seconds: Int, onFinished: (() -> Unit)? = null) {
        countdownSeconds = seconds
        onCountdownFinished = onFinished
        visibility = VISIBLE
        alpha = 0f

        // Apply fade-in animation
        val fadeInAnimation = ObjectAnimator.ofFloat(this, "alpha", 0f, 1f)
        fadeInAnimation.duration = 500 // 500ms fade-in
        fadeInAnimation.start()

        updateDisplay()
    }

    private fun updateDisplay() {
        invalidate()

        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = Runnable {
            countdownSeconds--
            playBeep()
            if (countdownSeconds <= 0) {
                // Apply fade-out animation before hiding
                val fadeOutAnimation = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f)
                fadeOutAnimation.duration = 1000 // 500ms fade-out
                fadeOutAnimation.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        visibility = GONE
                        onCountdownFinished?.invoke()
                    }
                })
                fadeOutAnimation.start()
            } else {
                updateDisplay()
            }
        }
        handler.postDelayed(countdownRunnable!!, 1000)
    }

    private fun playBeep() {
        try {
            val (toneType, duration) = when (countdownSeconds) {
                4 -> Pair(ToneGenerator.TONE_DTMF_1, 100)
                3 -> Pair(ToneGenerator.TONE_DTMF_3, 100)
                2 -> Pair(ToneGenerator.TONE_DTMF_6, 100)
                1 -> Pair(ToneGenerator.TONE_DTMF_9, 100)
                else -> Pair(ToneGenerator.TONE_DTMF_1, 100)
            }
            toneGenerator.startTone(toneType, duration)
        } catch (_: Exception) {
            // Silently fail if sound cannot be played
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw semi-transparent background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2f * 0.8f

        // Draw outer circle border
        canvas.drawCircle(centerX, centerY, radius, circlePaint)

        // Draw inner circle border
        canvas.drawCircle(centerX, centerY, radius * 0.9f, circlePaint)

        // Draw rectangle frame
        val frameMargin = radius * 0.2f
        canvas.drawRect(
            centerX - radius - frameMargin,
            centerY - radius - frameMargin,
            centerX + radius + frameMargin,
            centerY + radius + frameMargin,
            borderPaint
        )

        // Draw large number
        paint.textSize = radius * 0.8f
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        canvas.drawText(
            countdownSeconds.toString(),
            centerX,
            centerY + paint.textSize / 3f,
            paint
        )

        // Draw "SECONDS" text
        paint.textSize = radius * 0.3f
        canvas.drawText(
            "SECONDS",
            centerX,
            centerY + radius * 0.5f,
            paint
        )
    }

    fun stop() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        visibility = GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}

