package com.example.myapplication

class SceneSequenceAnalyser(
    private val sceneAnalyser: SceneAnalyser = SceneAnalyser(),
    private val requiredThreshold: Float = 0.7f
) {

    companion object {
        private const val MINIMUM_IMAGES = 5
    }

    fun isHoldingPhone(sequence: Sequence): Boolean {
        val scenes = sequence.scenes

        if (scenes.size < MINIMUM_IMAGES) {
            return false
        }

        val scenesWithHandNearEar = scenes.count { scene ->
            val poseState = sceneAnalyser.detectPose(scene)
            poseState == PoseState.HAND_NEAR_EAR
        }

        val percentage = scenesWithHandNearEar.toFloat() / scenes.size
        return percentage >= requiredThreshold
    }

    fun getLatestPose(sequence: Sequence): PoseState? {
        val latestScene = sequence.getLatestScene() ?: return null
        return sceneAnalyser.detectPose(latestScene)
    }
}

