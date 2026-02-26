package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MyCanvas(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var scene: Scene? = null
    private var poseState: PoseState? = null

    private val defaultPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
        textSize = 60f
    }

    private val bluePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val statePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.FILL_AND_STROKE
        textSize = 60f
    }

    private val stateBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 180
    }

    fun setScene(scene: Scene) {
        this.scene = scene
        invalidate()
    }

    fun setPoseState(poseState: PoseState?) {
        this.poseState = poseState
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        scene?.let { s ->
            s.person?.let { p ->
                p.leftEar?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
                p.rightEar?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
                p.leftHand?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
                p.rightHand?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
            }

            s.objects.forEach {
                canvas.drawRect(it.boundingBox, defaultPaint)
                canvas.drawText(it.getSize().toString(), it.boundingBox.left.toFloat(), it.boundingBox.top.toFloat(), defaultPaint)
            }
        }

        poseState?.let {
            drawPoseState(canvas, it)
        }
    }

    private fun drawPoseState(canvas: Canvas, state: PoseState) {
        val text = state.toString()
        val textBounds = android.graphics.Rect()
        statePaint.getTextBounds(text, 0, text.length, textBounds)

        val padding = 20f
        val x = padding
        val y = padding + textBounds.height()

        val boxLeft = x - padding / 2
        val boxTop = y - textBounds.height() - padding / 2
        val boxRight = x + textBounds.width() + padding / 2
        val boxBottom = y + padding / 2

        canvas.drawRect(boxLeft, boxTop, boxRight, boxBottom, stateBackgroundPaint)
        canvas.drawText(text, x, y, statePaint)
    }

    fun clearScene() {
        scene = null
        poseState = null
        invalidate()
    }
}
