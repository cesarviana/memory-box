package com.example.myapplication

import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat

class ImageSlideshow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val EFFECT_DURATION = 1200L
    }

    private val frontImageView: ImageView = ImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    private val backImageView: ImageView = ImageView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
    }

    private var images: List<Drawable> = emptyList()
    private var currentIndex = 0
    private var autoPlayRunnable: Runnable? = null
    private var intervalMs: Long = 3000L // Default 3 seconds

    init {
        addView(backImageView)
        addView(frontImageView)
    }

    fun setImages(imageDrawables: List<Drawable>, intervalMs: Long = 3000L) {
        this.images = imageDrawables
        this.intervalMs = intervalMs
        this.currentIndex = 0

        if (images.isNotEmpty()) {
            frontImageView.setImageDrawable(images[0])
            startAutoPlay()
        }
    }

    fun setImagesFromResources(resources: List<Int>, intervalMs: Long = 3000L) {
        val drawables = resources.mapNotNull { resId ->
            ResourcesCompat.getDrawable(context.resources, resId, context.theme)
        }
        setImages(drawables, intervalMs)
    }

    private fun startAutoPlay() {
        stopAutoPlay()
        autoPlayRunnable = Runnable {
            transitionToNext()
        }
        postDelayed(autoPlayRunnable!!, intervalMs)
    }

    private fun transitionToNext() {
        if (images.isEmpty()) return

        currentIndex = (currentIndex + 1) % images.size

        backImageView.setImageDrawable(images[currentIndex])

        // Fade out front and fade in back
        val fadeOut = ObjectAnimator.ofFloat(frontImageView, "alpha", 1f, 0f).apply {
            duration = EFFECT_DURATION
        }

        val fadeIn = ObjectAnimator.ofFloat(backImageView, "alpha", 0f, 1f).apply {
            duration = EFFECT_DURATION
        }

        fadeOut.start()
        fadeIn.start()

        // Swap the views after animation completes
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                val tempDrawable = frontImageView.drawable
                frontImageView.setImageDrawable(backImageView.drawable)
                backImageView.setImageDrawable(tempDrawable)
                backImageView.alpha = 0f
                frontImageView.alpha = 1f

                // Schedule next transition
                removeCallbacks(autoPlayRunnable!!)
                postDelayed(autoPlayRunnable!!, intervalMs)
            }
        })
    }

    fun stopAutoPlay() {
        autoPlayRunnable?.let { removeCallbacks(it) }
        autoPlayRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoPlay()
    }
}



