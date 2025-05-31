package com.reservation.utilities.provider

interface Tokenable {
    fun identity(): String

    fun role(): String

    fun username(): String
}
