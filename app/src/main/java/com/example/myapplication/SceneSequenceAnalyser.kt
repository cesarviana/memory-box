package com.example.myapplication

class SceneSequenceAnalyser(
    private val sceneAnalyser: SceneAnalyser = SceneAnalyser(),
    private val requiredThreshold: Float = 0.7f
) {

    fun isHoldingPhone(sequence: Sequence): Boolean {
        val scenes = sequence.scenes

        if (sequence.tooShort()) {
            return false
        }

        val scenesWithHandNearEar = scenes.count { scene ->
            val poseState = sceneAnalyser.detectPose(scene)
            poseState == PoseState.HAND_NEAR_EAR
        }

        val percentage = scenesWithHandNearEar.toFloat() / scenes.size
        return percentage >= requiredThreshold
    }
}

