package com.example.myapplication

data class Scene(
    val person: Person? = null
) {
    val sceneAnalyser = SceneAnalyser()
    fun hasNoPerson(): Boolean = person == null || this.isAway(person)

    fun isAway(person: Person): Boolean {
        return !sceneAnalyser.isNearScreen(person, 200)
    }

}

