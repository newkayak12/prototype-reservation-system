package com.reservation.utilities.generator.password

object PasswordGenerator {
    private const val MAX_LENGTH = 18
    private val lowerCase = ('a'..'z').toList()
    private val upperCase = ('A'..'Z').toList()
    private val digits = ('0'..'9').toList()
    private val special = listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')')
    private val all = lowerCase + upperCase + special + digits

    /**
     * 영문 대문자, 소문자, 숫자, 특수문자를 포함한 18자의 임의의 비밀번호를 생성합니다.
     */
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
