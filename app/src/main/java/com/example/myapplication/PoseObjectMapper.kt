package com.example.myapplication

import android.graphics.PointF
import android.graphics.Rect
import android.util.Size
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseObjectMapper(val imageSize: Size, val canvasSize: Size) {
    private var scale = canvasSize.width.toFloat() / imageSize.width.toFloat()

    fun mapPose(pose: Pose): Person {
        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)?.position?.let { mapPoint(it) }
        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)?.position?.let { mapPoint(it) }
        val leftHand = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)?.position?.let { mapPoint(it) }
        val rightHand = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)?.position?.let { mapPoint(it) }

        return PersonBuilder()
            .withLeftEar(leftEar)
            .withRightEar(rightEar)
            .withLeftHand(leftHand)
            .withRightHand(rightHand)
            .build()
    }

    fun mapObjects(objects: List<DetectedObject>): List<Object> {
        return objects.map { obj ->
            Object(mapBoundingBox(obj.boundingBox))
        }
    }

    private fun mapPoint(point: PointF): PointF {
        val x = point.x * scale
        val y = point.y * scale
        return invertHorizontally(PointF(x, y))
    }

    private fun invertHorizontally(point: PointF): PointF {
        val x = canvasSize.width - point.x
        return PointF(x, point.y)
    }

    private fun mapBoundingBox(box: Rect): Rect {
        val left = canvasSize.width - box.left * scale
        val right = canvasSize.width - box.right * scale
        val top = box.top * scale
        val bottom = box.bottom * scale
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }
}

