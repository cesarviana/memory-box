package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Size
import android.util.SizeF
import android.view.View
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class MyCanvas(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val imageSize = Size(480, 640)
    private var scale = 1f

    private var objects: List<DetectedObject> = emptyList()
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

    fun setPose(pose: Pose) {
        this.pose = pose
        invalidate()
    }

    fun setObjects(objects: List<DetectedObject>) {
        this.objects = objects
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val scaleW = w / imageSize.width.toFloat()
        scale = scaleW
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        pose?.allPoseLandmarks?.forEach { landmark ->
            val point = translatePoint(landmark.position, scale)
            val isRelevantLandmark = landmark.landmarkType in relevantLandmarks
            if (isRelevantLandmark) {
                canvas.drawCircle(point.x, point.y, 10f, bluePaint)
                canvas.drawText(
                    "(${point.x.toInt()}, ${point.y.toInt()})",
                    point.x + 20,
                    point.y,
                    bluePaint
                )
            } else {
                canvas.drawCircle(point.x, point.y, 10f, defaultPaint)
            }
        }
        objects.forEach {
            val box = it.boundingBox
            val left = this.width - box.left * scale
            val right = this.width - box.right * scale
            val top = box.top * scale
            val bottom = box.bottom * scale
            canvas.drawRect(left, top, right, bottom, defaultPaint)
        }
    }

    private fun translatePoint(point: PointF, scale: Float): PointF {
        val x = point.x * scale
        val y = point.y * scale
        return invertHorizontally(PointF(x, y))
    }

    private fun invertHorizontally(point: PointF): PointF {
        val x = this.width - point.x
        return PointF(x, point.y)
    }
}
