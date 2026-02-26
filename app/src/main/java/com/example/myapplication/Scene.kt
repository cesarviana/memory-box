package com.example.myapplication

data class Scene(
    val person: Person? = null,
    val objects: List<Object> = emptyList()
) {
    fun hasNoPerson(): Boolean = person == null
}

