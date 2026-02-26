package com.example.myapplication

import android.graphics.PointF
import android.util.Range
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PoseState {
    HOLDING_PHONE_NEAR_EAR,
    HAND_NEAR_EAR,
    HAND_AWAY_FROM_EAR,
    NO_PERSON,
    UNKNOWN
}

class SceneAnalyser(
    private val earHandMinProximity: Int = 300,
    private val earObjectMaxProximity: Int = 150
) {

    companion object {
        private val PHONE_EXPECTED_SIZE_RANGE = Range(300, 600)
    }

    fun detectPose(scene: Scene): PoseState {
        if (scene.hasNoPerson()) {
            return PoseState.NO_PERSON
        }

        val person = scene.getPerson()!!

        // First check if person is holding an object near their ear
        if (isPersonHoldingPhoneNearEar(person, scene.getObjects())) {
            return PoseState.HOLDING_PHONE_NEAR_EAR
        }

        // Otherwise check if hand is near ear
        if (hasHandNearEar(person)) {
            return PoseState.HAND_NEAR_EAR
        }

        // If we have valid landmarks but no hand near ear
        if (person.leftEar != null || person.rightEar != null) {
            return PoseState.HAND_AWAY_FROM_EAR
        }

        return PoseState.UNKNOWN
    }

    private fun isPersonHoldingPhoneNearEar(person: Person, objects: List<Object>): Boolean {
        val possiblePhones = objects.filter {
            it.getSize() in PHONE_EXPECTED_SIZE_RANGE
        }

        // Check if any object is near left ear and left hand
        if (person.leftEar != null && person.leftHand != null) {
            for (obj in objects) {
                val objCenter = getCenterPoint(obj.boundingBox)
                val distanceToEar = distance(person.leftEar, objCenter)
                val distanceToHand = distance(person.leftHand, objCenter)

                if (distanceToEar < earObjectMaxProximity && distanceToHand < earHandMinProximity) {
                    return true
                }
            }
        }

        // Check if any object is near right ear and right hand
        if (person.rightEar != null && person.rightHand != null) {
            for (obj in possiblePhones) {
                val objCenter = getCenterPoint(obj.boundingBox)
                val distanceToEar = distance(person.rightEar, objCenter)
                val distanceToHand = distance(person.rightHand, objCenter)

                if (distanceToEar < earObjectMaxProximity && distanceToHand < earHandMinProximity) {
                    return true
                }
            }
        }

        return false
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

    private fun getCenterPoint(rect: android.graphics.Rect): PointF {
        return PointF(
            rect.exactCenterX(),
            rect.exactCenterY()
        )
    }

    private fun distance(p1: PointF, p2: PointF): Int {
        val yDifference = abs(p1.y - p2.y)
        val xDifference = abs(p1.x - p2.x)
        return sqrt((yDifference * yDifference) + (xDifference * xDifference)).roundToInt()
    }
}

