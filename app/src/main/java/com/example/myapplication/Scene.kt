package com.example.myapplication

data class Scene(
    val person: Person? = null
) {
    fun hasNoPerson(): Boolean = person == null
}

