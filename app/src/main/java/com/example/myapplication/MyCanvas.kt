package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MyCanvas(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var scene: Scene? = null

    private val bluePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    fun setScene(scene: Scene) {
        this.scene = scene
        if (isShown) {
            invalidate()
        }
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
        }
    }
}
