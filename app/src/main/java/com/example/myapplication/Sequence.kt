package com.example.myapplication

class Sequence {
    val scenes = mutableListOf<Scene>()
    private val maxSize = 10

    fun add(scene: Scene) {
        if (scenes.size >= maxSize) {
            scenes.removeAt(0)
        }
        scenes.add(scene)
    }

    fun getLatestScene(): Scene? = scenes.lastOrNull()

    fun clear() {
        scenes.clear()
    }
}

