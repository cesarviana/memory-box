package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseLandmarkView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var viewWidth = 0
    private var viewHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0

    private var pose: Pose? = null
    private val defaultPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val relevantLandmarks = listOf(
        PoseLandmark.LEFT_INDEX,
        PoseLandmark.LEFT_EAR,
        PoseLandmark.RIGHT_INDEX,
        PoseLandmark.RIGHT_EAR,
    )

    private val bluePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
        textSize = 50f
    }

    fun setPose(pose: Pose, imageWidth: Int, imageHeight: Int) {
        this.pose = pose
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        pose?.allPoseLandmarks?.forEach { landmark ->
            val point = translatePoint(landmark.position, imageWidth, imageHeight, viewWidth, viewHeight)
            val isRelevantLandmark = landmark.landmarkType in relevantLandmarks
            if (isRelevantLandmark) {
                canvas.drawCircle(point.x, point.y, 10f, bluePaint)
                canvas.drawText("(${point.x.toInt()}, ${point.y.toInt()})", point.x + 20, point.y, bluePaint)
            } else {
                canvas.drawCircle(point.x, point.y, 10f, defaultPaint)
            }
        }
    }

    private fun translatePoint(point: PointF, imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): PointF {
        val x = point.x / imageWidth * viewWidth
        val y = point.y / imageHeight * viewHeight
        return PointF(x, y)
    }
}
