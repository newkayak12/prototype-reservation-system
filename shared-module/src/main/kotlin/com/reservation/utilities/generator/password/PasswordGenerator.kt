package com.reservation.utilities.generator.password

object PasswordGenerator {
    const val MAX_LENGTH = 18
    val lowerCase = ('a'..'z').toList()
    val upperCase = ('A'..'Z').toList()
    val digits = ('0'..'9').toList()
    val special = listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')
    val all = lowerCase + upperCase + special + digits

    fun createDefaultPassword(): String {
        val required =
            listOf(
                lowerCase.random(),
                upperCase.random(),
                digits.random(),
                special.random(),
            )

        val last = List(MAX_LENGTH - required.size) { all.random() }

        return (required + last).shuffled().joinToString("")
    }
}
