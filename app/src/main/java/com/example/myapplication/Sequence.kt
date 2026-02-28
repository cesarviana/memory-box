package com.example.myapplication

class Sequence {
    val scenes = mutableListOf<Scene>()
    private val sequenceSize = 10

    fun add(scene: Scene) {
        if (scenes.size >= sequenceSize) {
            scenes.removeAt(0)
        }
        scenes.add(scene)
    }

    fun tooShort(): Boolean = scenes.size < sequenceSize

    fun getLatestScene(): Scene? = scenes.lastOrNull()

    fun clear() {
        scenes.clear()
    }
}

