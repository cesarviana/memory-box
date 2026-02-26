package com.example.myapplication

class Scene(
    private var person: Person? = null,
    private var objects: List<Object> = emptyList()
) {
    fun hasNoPerson(): Boolean = person == null

    fun hasPerson(): Boolean = person != null

    fun setPerson(person: Person?) {
        this.person = person
    }

    fun removePerson() {
        this.person = null
    }

    fun setObjects(objects: List<Object>) {
        this.objects = objects
    }

    fun getPerson(): Person? = person

    fun getObjects(): List<Object> = objects
}

