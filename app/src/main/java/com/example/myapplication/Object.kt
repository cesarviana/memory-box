package com.example.myapplication

import android.graphics.Rect
import kotlin.math.hypot

data class Object(val boundingBox: Rect) {
    fun getSize(): Int {
        return hypot(
            boundingBox.width().toDouble(),
            boundingBox.height().toDouble()
        ).toInt()
    }
}
