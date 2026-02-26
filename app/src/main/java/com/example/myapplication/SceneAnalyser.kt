package com.example.myapplication

import android.graphics.PointF
import android.util.Range
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PoseState {
    HAND_NEAR_EAR,
    HAND_AWAY_FROM_EAR,
    NO_PERSON,
    UNKNOWN
}

class SceneAnalyser(
    private val earHandMinProximity: Int = 300
) {

    fun detectPose(scene: Scene): PoseState {
        if (scene.person == null) {
            return PoseState.NO_PERSON
        }

        if (hasHandNearEar(scene.person)) {
            return PoseState.HAND_NEAR_EAR
        }

        if (scene.person.leftEar != null || scene.person.rightEar != null) {
            return PoseState.HAND_AWAY_FROM_EAR
        }

        return PoseState.UNKNOWN
    }

    private fun hasHandNearEar(person: Person): Boolean {
        if (person.leftEar != null && person.leftHand != null) {
            if (distance(person.leftEar, person.leftHand) < earHandMinProximity) {
                return true
            }
        }

        if (person.rightEar != null && person.rightHand != null) {
            if (distance(person.rightEar, person.rightHand) < earHandMinProximity) {
                return true
            }
        }

        return false
    }

    private fun distance(p1: PointF, p2: PointF): Int {
        val yDifference = abs(p1.y - p2.y)
        val xDifference = abs(p1.x - p2.x)
        return sqrt((yDifference * yDifference) + (xDifference * xDifference)).roundToInt()
    }
}

