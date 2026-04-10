package com.example.myapplication

import android.graphics.PointF

data class Person(
    val leftEar: PointF?,
    val rightEar: PointF?,
    val leftHand: PointF?,
    val rightHand: PointF?,
    val rightShoulder: PointF?,
    val leftShoulder: PointF?
)

class PersonBuilder {
    private var leftEar: PointF? = null
    private var rightEar: PointF? = null
    private var leftHand: PointF? = null
    private var rightHand: PointF? = null
    private var rightShoulder: PointF? = null
    private var leftShoulder: PointF? = null

    fun withLeftEar(point: PointF?) = apply { leftEar = point }
    fun withRightEar(point: PointF?) = apply { rightEar = point }
    fun withLeftHand(point: PointF?) = apply { leftHand = point }
    fun withRightHand(point: PointF?) = apply { rightHand = point }
    fun withRightShoulder(point: PointF?) = apply { rightShoulder = point }
    fun withLeftShoulder(point: PointF?) = apply { leftShoulder = point }
    fun build(): Person = Person(leftEar, rightEar, leftHand, rightHand, rightShoulder, leftShoulder)
}
