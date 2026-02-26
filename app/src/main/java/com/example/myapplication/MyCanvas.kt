package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MyCanvas(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var objects: List<Object> = emptyList()
    private var person: Person? = null
    private val defaultPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val bluePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 5f
        style = Paint.Style.STROKE
        textSize = 50f
    }

    fun setPerson(person: Person) {
        this.person = person
        invalidate()
    }

    fun setObjects(objects: List<Object>) {
        this.objects = objects
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        person?.let { p ->
            p.leftEar?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
            p.rightEar?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
            p.leftHand?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
            p.rightHand?.let { canvas.drawCircle(it.x, it.y, 10f, bluePaint) }
        }

        objects.forEach {
            canvas.drawRect(it.boundingBox, defaultPaint)
        }
    }

    fun clearPerson() {
        person = null
        invalidate()
    }
}
