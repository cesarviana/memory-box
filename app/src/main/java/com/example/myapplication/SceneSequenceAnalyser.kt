package com.example.myapplication

class SceneSequenceAnalyser(
    private val sceneAnalyser: SceneAnalyser = SceneAnalyser(),
    private val requiredThreshold: Float = 0.7f
) {

    fun hasNoPerson(sequence: Sequence): Boolean {
        val scenes = sequence.scenes
        if (scenes.isEmpty()) return true
        val scenesWithNoPerson =
            scenes.count { it.person == null || !sceneAnalyser.isNearScreen(it.person, 150) }
        val percentage = scenesWithNoPerson.toFloat() / scenes.size
        return percentage >= requiredThreshold
    }

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

